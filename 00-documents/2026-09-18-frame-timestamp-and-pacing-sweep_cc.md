# 时间戳与节奏缺陷的全仓排查（2026-09-18）

`screencapture` 修完 fps/PTS 之后，做了一次全仓排查，找其它模块里是否存在同一类缺陷。本文记录
排查方法、四处修复，以及**核实为合理用法的清单**——后者的价值不低于前者：它标明哪些位置**看起来**
像同一个缺陷但其实不是，下次不必再查一遍。

基线提交：`fd1ac70ce`。本文对应 `15e8bcd25`、`233d5dd13`、`cc79699b5` 三个提交，以及复核后追加的修正提交（见 §4、§5 末尾）。

起因见 `00-documents/2026-09-18-screencapture-pts-fix-and-mp4-output_cc.md`。那份文档修的是
`Screenshot2H26xStrategy`；本文回答的是「同样的毛病还在哪里」。

## 1. 三类模式

排查按三个可机械识别的形态展开。

**模式 A —— 用帧计数推算时间戳。** 形如 `frameIndex * 1_000_000 / fps`。缺陷本质不在常数错了，
而在于**它假设生产循环真的跑出了标称速率**。一旦某帧迟到或被丢弃，时间轴照样推进一整个帧周期，
误差只累加不回收。

**模式 B —— MediaCodec 输出回调丢弃 `info.presentationTimeUs` 而自行重算。** 重算只会让上报的
时间轴与它所描述的帧脱节。

**模式 C —— 硬编码的采集/轮询节奏。** 包括两种：该受配置参数控制的循环里写死了 `delay(32ms)`；
以及「处理一帧 → 睡固定时长」的写法——每个周期都要额外加上本次处理的耗时，真实速率**永远**低于
请求值。正确写法是按固定间隔推移下一次的截止时间。

排查覆盖了所有 media/codec/capture 路径、所有 `delay` / `sleep` / `postDelayed` 循环，以及所有
MediaCodec 输出回调。

## 2. 修复一：`camera2live` 编码时间戳（`15e8bcd25`）

**这是刚修那个缺陷的精确同构体，也是本轮唯一的 HIGH。**

`CameraAvcEncoder.kt` 原先：

```kotlin
private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / frameRate
```

该值作为**输入** PTS 交给 `codec.queueInputBuffer()`，而 `mFrameCount` 只统计**实际入队**的帧。
两个前提都不成立：

- `frameRate` 是**请求**的帧率。真实摄像头的出帧速率随曝光与负载浮动。
- 同文件的 `BoundedFrameQueue.offer()` 在队列满时**丢弃最旧帧**（`MAX_PENDING_FRAMES = 5`），
  被丢的帧从不推进计数器。

于是编码时间轴按缺帧比例压缩真实时间、整体跑快。后果有两层，第二层更实际：

1. 时间轴加速——摄像头实跑 15fps 而 `frameRate = 30` 时约 2 倍速。
2. **码率失配**——MediaCodec 的码率控制用 PTS 判断一串帧覆盖了多少墙钟时间。时间轴被压缩后，
   它以为自己在产出 `frameRate` fps，于是把 `KEY_BIT_RATE` 分摊到比实际更多的帧上，链路上的
   实际码率按同样比例低于目标值。

**为什么一直没炸**：仓库内唯一消费者是网络回调与 debug 用的裸 `.h264` 文件。裸 Annex-B 流不携带
时间戳，正好把它掩盖住了。任何把这路流送进 muxer 或做音视频同步的库使用者会立刻看到加速与漂移。

改为 `System.nanoTime()` 相对首帧，与 `Screenshot2H26xStrategy` 同形。`mFrameCount` 与
`computePresentationTimeUs` 随之删除；`frameRate` 保留——它喂的 `KEY_FRAME_RATE` 本来就是对的
位置。

> 一处需要澄清：排查初稿曾称「`image.timestamp`（真实采集时钟）就在手边却被丢弃」。**不准确**——
> 编码器收到的是已转换的 `ByteArray`，要拿采集时钟需从 `ImageReader` 回调一路接线。取入队时刻的
> 单调时钟是代价小得多且足够的做法。

## 3. 修复二：`lib-mvvm` 倒计时漂移（`233d5dd13`）

`ScreenCountdownManager` 原先 `delay(TICK_INTERVAL)` 之后把模型时钟**恰好**减去 1000ms。而循环体
内还有 `_countdownState.update{}` 与可能挂起的 `_countdownEffect.emit()`，这些耗时只累加不回收：
倒计时结束得比自己声明的时长越来越晚，暴露给 UI 的 `remainingTimeMillis` 也随之与墙钟分离。

改为从单调原点推导剩余时间，并按移动截止时间安排每次 tick。

**这里有一处必须连带改的东西，否则修完就坏。** 警告判定原本是：

```kotlin
if (enableWarning && remaining == warningThresholdMillis)
```

剩余时间一旦来自时钟，就几乎不可能**精确**落在阈值上，这条警告会永远不触发。已改为一次性的
`<=` 跨越判定；发射的秒数取自 `warningThresholdMillis`——正是精确相等成立时的取值，对调用方不变。

另外 `remainingTimeMillis` 按整 tick **向上取整**后才暴露。否则一个 tick 之后会是 59997，调用方
除以 1000 会少显示一秒。

## 4. 修复三：demo 解码器投喂节奏（`cc79699b5`）

`DecodeH265RawFile.startDecoding()` 原先在 NAL 起始码扫描的**内层**循环里 `Thread.sleep(32)`
（代码自带 `FIXME We'd better control the FPS by SpeedManager`）。三个问题叠加：

- 协程里用 `Thread.sleep` 阻塞调度线程；
- **按 NAL 而非按图像睡**——一张前面挂了 VPS/SPS/PPS/SEI 的图会连烧好几个帧周期才被喂进去；
- 32ms 与同文件处处假定的 24fps（应为 41ms）对不上，且是「干完活再睡」。

改为按移动截止时间投喂，且**只在 VCL 单元上消耗一个帧周期**——HEVC 的 `nal_unit_type` 0–31 是
VCL，32 及以上是参数集、SEI 与分隔符，它们属于其后那张图。文件里原本带 `@Suppress("unused")` 的
`getNaluType()` 正好用上，注解去掉。

队列**保留 `offer()` 未换成 `put()`**：阻塞式背压会在解码器停止排空时把投喂线程永久挂住，对
demo 是更坏的故障模式。改为丢帧时打 error 日志，消除「静默」那一半。

**复核修正（风格一致性，非构建阻断）**：新增的
`import kotlin.time.Duration.Companion.milliseconds` 被插在 `kotlinx.coroutines.cancel` 与
`.delay` 之间。按字典序 `kotlin.` 的第 7 个字符是 `.`（0x2E）、`kotlinx` 是 `x`（0x78），所以
`kotlin.*` 应排在 `kotlinx.*` 之前——仓库既有写法也是如此（例如
`RecordSingleAppScreenActivity.kt:30-31`）。已上移修正。本轮改动的全部 Kotlin 文件已逐一复核
import 顺序，只此一处。

**但它不会让构建失败**，本仓库的两项 import 顺序检查都是关闭的：

| 位置 | 配置 |
|------|------|
| `.editorconfig:17` | `ktlint_standard_import-ordering = disabled` |
| `10-configs/detekt.yml:783` | `ImportOrdering: active: false` |

规则未启用，`ignoreFailures = false` 便无从作用——后者管的是「检查产生失败项时是否中断构建」，
而这里不会产生失败项。此处一度被记为「会破坏构建」，`5672ecbbd` 的提交信息里也是这个说法；
改动该留，理由须更正。记在这里，以免后来者据此误判本仓库的 import 约束强度，或在排查真实的
构建失败时找错方向。

## 5. 修复四：demo 音频接收端轮询（`cc79699b5`）

`AudioReceiver` 的两个循环都是 `queue.poll()` + 硬编码 `delay(10.milliseconds)`：非阻塞轮询加
固定睡眠，排空速率被钉死在约 100 项/秒（与到达速率无关），空闲时还以 100Hz 空转。突发积压后
排空不会快于 100/s，已经产生的延迟永不回收。

**对照组就在隔壁**：`AudioSender.kt:124` 与 `:135` 做同一件事，用的是阻塞 `take()`。接收端现与
之对齐。`delay` 与 `milliseconds` 两个 import 随之失效，已删（detekt 零容忍）。

### 5.1 复核修正：`take()` 必须可取消，且不能藏在安全调用里

对齐 `AudioSender` 的同时，也把它的两处潜在缺陷一并带了过来。两处都已在**两个文件里**修正，
以免它们再次分叉。

**一、`ArrayBlockingQueue.take()` 不可取消。** `stopServer()` 先 `ioScope.cancel()`，但协程若此刻
正阻塞在 `take()` 里，取消**不会**让它返回——它会一直占着一个 `Dispatchers.IO` 线程，直到又有元素
入队；而停止之后不会再有。原先的 `poll()` + `delay(10ms)` 虽然限速，`delay` 却是可取消的，
10ms 内必定退出。现改为 `runInterruptible { queue.take() }`：取消被映射为线程中断，
`take()` 抛 `InterruptedException`，协程正常结束。

`take()` 同时移出了 `runCatching` 之外——否则 `runCatching` 会吞掉 `CancellationException`，
要等下一轮 `ensureActive()` 才退出。

**二、`audioPlayer?.play(receiveAudioQueue.take())` 的求值顺序。** Kotlin 的安全调用在接收者为
null 时**不会**对实参求值，所以 `audioPlayer` 一旦为 null，`take()` 根本不执行，`while(true)`
就变成一个满转的死循环，而不是等待。当前生命周期下 `audioPlayer` 在线程启动前已赋值且从不置
null，所以实际不会触发；但这个前提不写在调用点旁边，任何一次重构都可能打破它。已改为先读队列、
再做安全调用。

## 6. 已核实为合理用法（不必再查）

这一节是本文的另一半价值。以下位置形态上像上述三类模式，**但不是缺陷**，理由如下。

| 位置 | 为什么不是缺陷 |
|---|---|
| `audio/.../opus/OpusEncoder.kt`、`audio/.../aac/AacEncoder.kt` | PTS 由**实际喂入**的 PCM 字节数推导，采样精确，对生产者速率零假设 |
| `audio/.../aac/AacDecoder.kt`、`audio/.../opus/OpusDecoder.kt` | 解码器**输入**端的单调 PTS；输出直通 AudioTrack，从不受 PTS 调度 |
| `audio/.../AudioPlayer.kt` `getAudioTimeUs()`、`AacStreamPlayer`、`OpusStreamPlayer` | 读 `AudioTrack.playbackHeadPosition`，即硬件真实播放时钟——正是该做的事 |
| `audio/.../aac/AacFilePlayer.kt` | 用 `MediaExtractor.sampleTime`，真实容器时间戳 |
| `androidbase/.../LeoTextureView.kt`、`demo/.../ScreenShareClientActivity.kt` | `frameIndex * 1_000_000 / 120` 形似模式 A，但都是**解码器输入**端，输出走 `releaseOutputBuffer(id, true)` 立即渲染，PTS 只需单调。**若改用带时间戳的 `releaseOutputBuffer(id, ns)` 就会变成真缺陷。**`120` 是与码流无关的魔数，属代码异味 |
| `demo/.../SpeedManager.kt` | **正确参考实现**：睡到绝对截止时间，并按计算值推进（代码里明写 "to avoid drifting"）。再遇到模式 C 可直接照此改 |
| `demo/.../*ByFFMpeg.kt` | `sleepOffset = FRAME_INTERVAL_MS - elapsed`，工作耗时是**被减去**而非累加；且按「以 VCL NAL 结尾的 NAL 组」分组，一帧一睡 |
| `androidbase/.../NetworkMonitor.kt` | 非媒体时间轴，偏差量级可忽略 |
| `opengl/`、`nfc/`、`camerax/`、`adpcm-*`、`h264-hevc-decoder`、`ffmpeg-javacpp` | 已扫，无命中。camerax 委托给 CameraX；adpcm / h264-hevc 封装是无状态的逐次调用编解码器，自身没有时间轴 |

**模式 B 全仓零命中。** 所有 `onOutputBufferAvailable` / `dequeueOutputBuffer` 之后的代码，要么
透传 `info.presentationTimeUs`，要么根本不碰时间戳。唯一做变换的
`ScreenRecordMediaCodecStrategy` 只是相对首帧重基准，保留真实间隔，正确。

**一处死代码，已处理**：`audio/.../AudioPlayer.kt` 的 `computePresentationTimeUs(frameIndex)`
全仓无调用方，却是发布库上的公开 API。经确认后已标记 `@Deprecated`，与
`ScreenProcessor.computePresentationTimeUs` 同批。

这里要把理由说准，它和模式 A **不完全同类**：`frameIndex * 1_000_000 / sampleRate` 这个式子本身
是对的——只要 `frameIndex` 真的是**已交付的 PCM 帧累计数**。音频按采样计数推导 PTS 正是本节上表
里 `OpusEncoder` / `AacEncoder` 被判定为合理用法的原因。问题在于**签名对此既无说明也无约束**：
传进来的若是回调次数或包序号，就会得到一条静默偏离音频的时间轴。加之它挂在播放器上，而播放器
并不喂编码器——播放位置应当用紧挨着它的 `getAudioTimeUs()`（读 `AudioTrack.playbackHeadPosition`，
硬件真实播放时钟）。弃用不产生新警告：无调用方。

## 7. 顺带完成的两件事

- **退役 fps/PTS 待办公告**（`ece85ff1b`）。`CLAUDE.md`、`AGENTS.md` 与项目记忆里的「已知问题
  （待处理）」写着「尚未修复，且不要顺手修」，而 `e0dabfca4` 已经修了。这两处各自写明了自己的
  退役条件，条件触发却没执行；`CLAUDE.md` 每个会话都会载入上下文，留着会让后续会话拒绝碰已经
  修好的代码。三处连同记忆索引一并删除。
- **补齐 CHANGELOG**（`7245072f4`、`cc79699b5`）。fps/PTS 修复、`setMp4OutputFile()` 新 API、
  两条录屏链路的 `KEY_FRAME_RATE` 修正、以及本文的 `camera2live` 与 `lib-mvvm` 两项，此前在
  CHANGELOG 中**一条记录都没有**，而它们都是发布库的行为变更或新公开 API。

## 8. 验证状态

### 已完成

- 人工静态核查：改动行行宽均未超 100 字符；`mFrameCount`、`computePresentationTimeUs`、
  `delay` / `milliseconds` 的残留引用均已清零；新增 import 均被使用
- HIGH 那条（`camera2live`）的代码路径经独立复核，未只凭排查结论采信；`AtomicLong` 已导入、
  `frameRate` 仍被 `KEY_FRAME_RATE` 使用（删除后不会触发 detekt 未用成员）
- **对本文三个提交做了一轮复核**，发现并修正两处：§4 的 import 顺序（风格一致性，不影响构建
  ——该项检查本仓库未启用）、§5.1 的 `take()` 可取消性与安全调用求值顺序。后者是真缺陷：
  原改动为与 `AudioSender` 对齐，把它的两处隐患一并复制了过来
- `lib-mvvm` **没有测试目录**，§8 原先担心的「既有单测断言精确相等会失败」不存在；
  `CountdownEffect.ShowWarning` 在仓库内也没有生产调用方，行为变更只影响库的外部使用者

### 待本地执行（尚未进行）

```bash
./gradlew --continue --rerun-tasks \
  :camera2live:compileDebugKotlin :camera2live:ktlintCheck :camera2live:detekt \
  :lib-mvvm:compileDebugKotlin :lib-mvvm:ktlintCheck :lib-mvvm:detekt :lib-mvvm:testDebugUnitTest \
  :demo:compileDevDebugKotlin :demo:ktlintCheck :demo:detekt
```

`:lib-mvvm:testDebugUnitTest` 已确认**没有**相关用例（该模块无 `src/test` 目录），不会因警告判定
由精确相等改为跨越而失败。是否补测另议。

### 待真机验证（尚未进行）

- **`camera2live` 时间戳**：录一段固定时长，确认相邻帧 PTS 间隔跟随墙钟而非恒为
  `1_000_000 / frameRate`；把输出送进 muxer，确认时长等于实际录制时长
- **`camera2live` 码率**：对比实际码率是否更接近 `KEY_BIT_RATE`。**刻意制造丢帧（高负载）再验
  一次**——这正是旧公式失准最厉害的场景
- **倒计时**：长时倒计时结束时刻与墙钟一致；警告 effect 恰好触发一次
- **H265 demo 播放**：已知时长的文件播放速度接近 24fps，且不出现 `Decoder queue full`
- **双向语音**：音频无卡顿，突发后延迟能回落（旧实现回不去）
