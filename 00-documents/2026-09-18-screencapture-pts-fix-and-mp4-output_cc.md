# 录屏时间戳修正与 MP4 输出（2026-09-18）

本文记录两件相互依赖的改动：修正 `screencapture` 截图录制链路的时间戳与采集节奏，以及在裸流之外
增加 MP4 输出。后者依赖前者——原因见 §2.2，那不是排序偏好，是硬约束。

基线提交：`ea3be8ded`。本文对应 `1236244ae`、`4f6fb359a`、`88c666233` 三个提交。

起因是 `00-documents/2026-09-18-record-single-app-screen-rotation-survival_cc.md` §9 记录的既有问题，
以及随后「能不能直接生成 HEVC 编码的 mp4」这一需求。

## 1. 需求与既定选择

用户确定的四点，本文按此执行：

| 项 | 选择 |
|------|------|
| 顺序 | 先修 PTS，再做 MP4 |
| 实现位置 | **方案 B**：做在 `screencapture` 库里，而非各调用方 |
| 输出形态 | **并存**：裸流照旧，MP4 是新增的第二路 |
| API 21–23 | 退回 H.264，并打日志说明 |

## 2. 改动一：时间戳与采集节奏（`1236244ae`）

### 2.1 问题

`setFps()` 只影响时间戳，不影响采集速率。四处互相矛盾：

| 位置（改动前） | 实际行为 |
|------|----------|
| 采集循环 | `delay(32.milliseconds)` 硬编码，完全不读 `builder.fps` |
| `encodeImages()` | EGL 时间戳 = `computePresentationTimeUs(frameCount, fps) * 1000` |
| `onOutputBufferAvailable()` | 上报给监听器的 PTS 同样由帧计数重算，**丢弃编码器给出的 `info.presentationTimeUs`** |
| `initResources()` | `MediaFormat.KEY_FRAME_RATE` 接的是 `builder.keyFrameRate`（默认 20），不是 `fps` |

后果：传 `fps = 5f` 时实际采集约 25 fps，而每帧 PTS 仍按 200000µs（5 fps）递增。真机实测
（2026-09-18，第二段横屏）首帧 `14:56:28.118` PTS=200000、末帧 `14:56:35.192` PTS=35200000，
墙钟 7.074 秒对应 PTS 跨度 35.0 秒——**时间轴比真实时间快 4.95 倍**。

帧计数法的根本缺陷不在于常数错了，而在于**它假设采集循环真的跑出了 `fps` 帧每秒**。一旦某帧迟到，
它照样把时间轴推进一整个帧周期，误差只累加不回收。

### 2.2 为什么这是 MP4 的前置条件

裸 Annex-B 流**不存储时间戳**，播放器按自己的假设决定速度，所以这个缺陷一直被掩盖着。
MP4 会把时间轴写进索引。同一份数据：

- 写成 `.h265`：看不出问题
- 写成 `.mp4`：录 7 秒得到一个 35 秒、5 倍慢放的文件

所以不是「先修更稳妥」，而是**不修就交付不了可用的 MP4**。

### 2.3 改法

**时间戳改为取自单调时钟。** 新增文件级私有函数 `elapsedNanosSince(origin)`，以第一帧为原点返回
真实经过的纳秒数，直接喂给 `EGLExt.eglPresentationTimeANDROID`。原点哨兵用 `Long.MIN_VALUE` 而
非 `0`——`System.nanoTime()` 的原点是任意的，读到 0 完全合法，用 0 当哨兵会导致每帧重新锁定原点。

**上报改为透传编码器的 PTS。** `onOutputBufferAvailable` 改用 `info.presentationTimeUs`。
这个值正是本录制器通过输入 Surface 交给编码器的那个，重算只会让上报的时间轴与它描述的帧脱节。
`frameCount` 随之删除。

**采集节奏改为按 `fps` 推移截止时间：**

```kotlin
val captureIntervalMs = captureIntervalMillis(builder.fps)
var nextCaptureAtMs = nowMillis()
while (...) {
    // ...采集并编码...
    nextCaptureAtMs += captureIntervalMs
    val waitMs = nextCaptureAtMs - nowMillis()
    if (waitMs > 0L) delay(waitMs.milliseconds) else nextCaptureAtMs = nowMillis()
}
```

**不用「干完活再睡固定时长」**：那样每个周期都要额外加上本次采集的耗时，真实速率会永远低于请求值
——即使把 32ms 换成 200ms 也一样。截止时间落后时直接丢弃积压而不是追赶，避免在已过期的截止时间上空转。
`nowMillis()` 用 `System.nanoTime()` 而非 `currentTimeMillis()`，录制中途改系统时钟不会让采集卡死或飞速空转。

### 2.4 `KEY_FRAME_RATE` 的连带修正

`MediaFormat.KEY_FRAME_RATE` 的语义就是帧率，原先却接了 `builder.keyFrameRate`。修好采集节奏之后
这个错配会真正造成损害：告诉编码器 20 fps 而实际只喂 5 fps，码率预算会按错误的帧数分摊。现改为接
`builder.fps`。

### 2.5 遗留未处理

- `Builder.keyFrameRate` / `setKeyFrameRate()` **不再抵达编码器**，但保留在 API 上以免调用方编译不过。
  已加 KDoc 说明其现状，并指出关键帧间隔应当用 `iFrameInterval`。**退役这个 setter 是一次独立的
  API 变更**，本次不做。
- `ScreenProcessor.computePresentationTimeUs()` 标记为 `@Deprecated` 而非删除——它是已发布的库 API。
  仓库内现已无调用方，因此不产生新的弃用警告。
- `ScreenRecordMediaCodecStrategy`（MediaProjection 路径）**本来就在用 `info.presentationTimeUs`
  并做 `beginPTS` 归零，从来没有这个缺陷**，未做改动。

## 3. 改动二：MP4 输出（`4f6fb359a`）

### 3.1 为什么必须用 MediaMuxer

MP4 不是「裸流前面加个头」。要做三件事：把起始码换成长度前缀；把 VPS/SPS/PPS 从码流中取出放进
sample entry；为每帧建索引并在关闭时写出 `moov`。这些都由 `MediaMuxer` 完成，拼字节做不到。
仓库此前没有任何 `MediaMuxer` 用法。

### 3.2 为什么从 codec 报告的 MediaFormat 开轨

新类 `Mp4TrackWriter` 在 `onOutputFormatChanged` 里用 **codec 给出的 `MediaFormat`** 调
`addTrack`，而不是自己从 CODEC_CONFIG 缓冲区拼 `csd`。原因：**HEVC 把 VPS+SPS+PPS 打包进单个
`csd-0`，而 AVC 要求 SPS 在 `csd-0`、PPS 在 `csd-1`。** 手工拼装一旦弄错，产出的文件能顺利封装、
不报任何错，却哪里都播不了。框架给出的 format 已经按各自编码格式切分好。

### 3.3 并存：两路输出互不影响

`ScreenDataListener` 收到的内容**一字未改**，现有调用方看到的和以前完全一致。MP4 是纯增量。

样本在编码器回调线程上同步写入 muxer，监听器仍走 `screenshotHandler?.post`。两者都能看到每一个样本，
只有 muxer 需要保证顺序。

### 3.4 生命周期：`close()` 不是可选项

**没有 `moov` 的 MP4 不可播放。** 进程在录制中途被杀，裸流已写入的部分照样能播，MP4 则整个作废。
这是采用容器的固有代价，已在 `Mp4TrackWriter` 的 KDoc 里写明。

`close()` 挂在 `releaseOwnedResources()` 中，即旋转重建、停止录制、`onDestroy` 三条路径共用的那条。
样本数为 0 时删除文件而不是留下——`MediaMuxer.stop()` 拒绝空轨道，留下的会是一个零字节、
看起来却像一次录制的 `.mp4`。

### 3.5 失败隔离

MP4 侧的任何异常都在 `Mp4TrackWriter` 内部被捕获，`broken` 标志置位后，后续调用直接返回（避免每帧一条
错误日志），并删除半成品文件。**异常不会逃逸到编码器回调**，所以 MP4 写失败不会连带中断裸流录制。
「并存」意味着一路垮掉不应拖垮另一路。

### 3.6 线程与锁

`onOutputFormatChanged` 原先没有取 `codecCallbackLock`、也没有校验 `h26xEncoder !== codec`，
现已补上：否则拆除流程可能在它开轨的同时把 writer 关掉。

释放时在**同一个**锁块里同时摘下 encoder 与 writer：

```kotlin
val encoder: MediaCodec?
val writer: Mp4TrackWriter?
synchronized(codecCallbackLock) {
    encoder = h26xEncoder.also { h26xEncoder = null }
    writer = mp4Writer.also { mp4Writer = null }
}
```

一个块即可：两者一起消失，不存在「encoder 已摘、writer 未摘」的中间态。给外层 `val` 在
`synchronized` 块内赋值是合法的——`kotlin.synchronized` 声明了 `EXACTLY_ONCE` 契约，确定赋值
分析认得它。

`close()` 放在锁外执行：写索引耗时较长，而回调侧一旦发现 `h26xEncoder` 已为 null 就提前返回，
够不到这个 writer，所以锁外关闭是安全的。反过来把 `close()` 挪进锁内，会让编码器回调停等一整个
索引写入。

### 3.7 API 21–23 退回 H.264

`MediaMuxer` 自 API 24 起才能写 HEVC 轨道。低于该版本时它会接受轨道随后失败，所以「要 HEVC」和
「要 MP4」无法同时满足，让路的是编码格式。

```kotlin
private fun fallBackToAvcIfMuxerCannotCarryHevc() {
    if (mp4OutputFile == null) return
    if (encodeType != EncodeType.H265) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return
    LogContext.log.w(TAG, "MediaMuxer cannot write an HEVC track before API 24 ...")
    encodeType = EncodeType.H264
}
```

三点设计取舍：

1. **只对请求了 MP4 的调用方生效。** API 21–23 上 HEVC 裸流本身毫无问题，无条件降级是调用方没要求
   过的回退。
2. **在编码器存在之前解析。** 保证 MP4 与裸流永远是同一种编码。
3. **结果可读回。** 新增 `Screenshot2H26xStrategy.encodeType`，调用方据此命名自己的文件。

## 4. 改动三：demo 接通（`88c666233`）

`RecordSingleAppScreenActivity`：

- 新增 `sessionBaseName`，在**构建录制器时**而非开始录制时确定。builder 需要提前知道 MP4 路径；
  两个文件分别取名会让旋转产生的分段事后无法配对。代价是文件名的时刻从「开始录制」变成「武装录制器」，
  两者间隔很短。
- 武装但从未开始的录制器**不产生任何文件**——muxer 要等 codec 报告 format 才创建。
- 裸流扩展名改为按 `screenProcessor.encodeType`（**实际**编码格式）决定，而不是请求的
  `VIDEO_ENCODE_TYPE`。一个装着 H.264 的 `.h265` 比长一点的文件名糟糕得多。

## 5. 明确记录的取舍

- **旋转仍然一段一文件。** `MediaMuxer` 的 track format 加入后不可更改，所以几何变化必须切文件，
  与裸流行为一致。
- **不调用 `setOrientationHint()`。** 画面本来就是按旋转后的几何重新编码的，已经是正的；再加方向
  提示会被播放器二次旋转。
- **不做跨分辨率拼接。** 需要能跟随中途几何变化的解码器，代价远大于收益。

## 6. 对现有调用方的影响

| 调用方 | 影响 |
|--------|------|
| 未调用 `setMp4OutputFile()`（含 `ScreenShareClientActivity` 一路） | 不产生 MP4；变化只有**时间戳变准**与**`fps` 真的生效** |
| `setFps()` 的使用者 | 采集速率现在真的会变。此前请求 5 fps 实得约 25 fps，修正后 CPU 与码率开销显著下降 |
| `setKeyFrameRate()` 的使用者 | 该值不再抵达编码器；关键帧间隔请改用 `setIFrameInterval()` |

## 7. 触到的上限

`Screenshot2H26xStrategy.kt` 现为 **1229 行**（detekt `LargeClass` 阈值 1300）、类内 **31 个函数**
（`TooManyFunctions.thresholdInClasses` 阈值 33）。

这正是把 MP4 逻辑独立成 `Mp4TrackWriter` 的直接原因之一，也是时间戳/节奏三个辅助函数放在**文件顶层**
而非类内的原因——`thresholdInFiles` 只统计顶层函数，该文件原本为 0。

**下一次给这个类加方法之前必须先拆分。** 该问题在
`2026-09-02-audio-media-teardown-followup-fixes_cc.md` §14.7 已列为已知未处理项，现在余量更小了。

## 8. 复审补记（2026-09-18）

本次改动交付后做过一轮复审，结论与随之而来的修正：

- **`ScreenRecordMediaCodecStrategy` 并非全身而退。** §2.4 修的 `KEY_FRAME_RATE` 错配在
  `ScreenRecordMediaCodecStrategy.kt:167` 逐字相同、当时未修。提交说明里那句「从来没有这个缺陷」
  只对 PTS 成立。真实调用方 `MediaProjectionService.kt:254-259` 传的 `ScreenShareSetting` 默认
  `fps = 20F`、`keyFrameRate = 8`，等于按 8 fps 分摊码率却按约 20 fps 喂帧。现已一并改为 `fps`。
  该链路由虚拟显示推帧、没有采集循环，所以只需改这一个键，不涉及节奏改造。
- **§3.6 的「两次取锁」**：文档与代码原本是一致的（都取两次），评审认为单个锁块更好——两者原子
  摘除，不存在「encoder 已摘、writer 未摘」的中间态——并据此改写了文档，但**没有同步改代码**，
  于是文档一度描述了一份不存在的实现。现已按文档把代码改为单个锁块。
  当初改成两次取锁是因为不确定 `kotlin.synchronized` 是否带 `EXACTLY_ONCE` 契约；
  仓库内 `BaseNettyClient.kt:602-604` 早有同样写法且一直编译通过，该顾虑不成立。
- `Mp4TrackWriter` 的线程契约原写作「所有调用都来自编码器回调线程」，但 `close()` 恰恰是特意
  不在该线程上调用的。已改为陈述真实不变量：由调用方的回调锁加摘除协议串行化。
- demo 侧三处残留一并清理：过期的 `FIXME`、已成死调用的 `setKeyFrameRate(20)`、以及
  `sessionBaseName` 仅用毫秒时间戳导致同毫秒内可能撞名。

## 9. 验证状态

### 已完成

- 人工静态核查：三处改动文件行宽均无超 100 字符项；`frameCount` 无残留引用
- 编译风险自查并修正一处：`const val` 不允许函数调用（`or`/`inv`），已改为 `private val`

### 待本地执行（尚未进行）

```bash
./gradlew --continue --rerun-tasks \
  :screencapture:compileDebugKotlin :screencapture:ktlintCheck :screencapture:detekt \
  :demo:ktlintCheck :demo:detekt :demo:compileDevDebugKotlin
```

### 待真机验证（尚未进行）

- **PTS 是否已修正**：日志中相邻帧的 `presentationTimeUs` 间隔应接近**墙钟间隔**，不再是恒定的
  `1_000_000 / fps`。`fps = 5f` 时帧应约每 200ms 出现一次，而不是约 40ms
- **`fps` 是否真的生效**：`Get H265 data` 的出现频率应从约 25 次/秒降到约 5 次/秒
- **MP4 可播放性与时长**：录约 N 秒，MP4 时长应约为 N 秒（此前会是 5N），画面方向正确
- **两路输出是否配对**：同一段应得到 `screen-<时间戳>.h265` 与 `screen-<时间戳>.mp4`，基名相同
- **旋转**：每次旋转应各自产出一对文件，且 MP4 均可独立播放
- **武装未开始**：按停止后不应留下空的 `.mp4`
- **API 21–23 设备**（若有）：应看到退回 H.264 的警告日志，且裸流文件名为 `.h264`
- **失败隔离**：MP4 写入失败时裸流录制应继续
