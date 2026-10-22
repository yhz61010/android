# `review/native-modules` 对比 `master` 的快速代码审查（2026-10-20）

> 本文分两部分：§0–§8 为第一轮快速审查；§9 起为第二轮详细审查，目前完成第 1/3 部分（录屏与共享基础库），第一轮在该范围内的结论以 §10 的裁决为准。Codex 随后在同一 HEAD 上对文档方案做了第三轮源码复核；该轮更正已经直接并入 §10–§14，在当时不代表代码已实施。第四轮（Claude，2026-10-20）对 Codex 的修订逐条对照源码复核，补充了 F2 缺失常量与测试影响、G4 的 `@RequiresApi`、G9 的真实迟到窗口，并按录屏所有者的既有隔离策略撤回 F10 的 `Error` 重抛。第五轮再次对照 AOSP 与当前控制流，进一步订正 FrameDropper 的多周期推进规则，修复 F10 验证项矛盾，并推翻第四轮 G3 的重试 token 判断；对应位置均保留轮次说明。第六轮（Claude，2026-10-20）新增 G11、G12、G13。第七轮（Codex，2026-10-21）对此前确认的 26 项做了实现改动，其中 F6、F9、G7 仅部分落地。第八轮（Claude，2026-10-21）的复核与后续公开 API 修正见 §15 和 §14.2。G11/G13 随 `b0c417d95`、`f6cbb985e` 和 `fec53929a` 落地后，远端记录见 §16；Codex 后续复核确认 G13 已正确落地，但 G11 仍存在 `stopTrafficMonitor()` 返回后才启动监控的剩余竞态。第十轮（Codex，2026-10-22）已使最终发布/启动与停止串行化，并以新增竞态用例验证停止栅栏，见 §17。另有一条跨分支待办（Detekt 任务未设 `classpath`，导致 `!!` 等需类型解析的规则实际失效）记于 §18，不在本分支修复。

## 0. 范围与方法

| 项目 | 值 |
|------|----|
| 审查分支 | `review/native-modules`，HEAD `e57608f70` |
| 对比基线 | `origin/master` 的合并基 `97c84d242`（`docs: update Claude repository guidance`） |
| 领先 / 落后 | 领先 93 个提交，落后 0 |
| 差异规模 | 285 个文件，+31424 / −5114；其中 `00-documents/` 45 个、`.claude/` 7 个不计入代码审查 |
| 方法 | 快速审查：按领域并行派出 4 个审查代理（音频与 Netty、共享基础库、录屏与相机、原生模块），主会话另审构建脚本、OpenGL、Manifest 与零散测试 |
| 执行方式 | 第一轮审查当时**仅做源码静态分析**，未执行 Gradle 或真机验证；同一 HEAD 后续执行的 `staticCheck` 结果见 §14，不能倒推为修复方案已验证 |
| 代码改动 | 源码无改动；后续复核只修订本文档中的结论与方案 |
| 行号核对 | 两处 HIGH、三处 MEDIUM（M1、M2、M4）及 `BaseNettyClient` 一处 LOW 的行号已对照工作树抽查；其余行号来自审查代理，未逐一复核 |

## 1. 总体结论

| 严重度 | 数量 | 说明 |
|--------|------|------|
| CRITICAL | 0 | — |
| HIGH | 2 | 录屏 PTS 改动引入的潜在丢帧回归；`lib-mvvm` 倒计时取整导致 tick 重复/跳跃 |
| MEDIUM | 7 | 3 处公开 API 行为变更未按「API 演进约定」记录；其余为逻辑矛盾、线程归属、文件体量 |
| LOW | 约 15 | 多为预存问题、日志级别、文档与实现不一致 |

四个领域的审查结论均为 Approve 或带 MEDIUM 警告的 Approve。audio 与 basenetty 的 teardown 状态机此前已经过七轮审查，本轮未再发现并发缺陷。

## 2. HIGH

### H1. 录屏：`KEY_MAX_FPS_TO_ENCODER` 与真实时钟 PTS 叠加可能间歇丢帧

- 位置：`screencapture/src/main/kotlin/com/leovp/screencapture/screenrecord/base/strategies/Screenshot2H26xStrategy.kt:832`，另见 `:459`
- 缺陷：本分支把每帧 PTS 从 `frameCount / fps` 的精确网格改为截屏完成后的单调时钟值，但 `MediaFormat` 仍设置 `KEY_MAX_FPS_TO_ENCODER = fps`。编码器侧 GraphicBufferSource 的 FrameDropper 把首帧 PTS 加一个周期设为首个目标点；候选帧比当前目标点早 2 ms 以上就丢弃。保留帧后按其 lateness 计算 `n`，把目标点推进 `(n + 1)` 个周期；丢弃帧不推进目标点。因此目标网格以首帧为锚，但会跳过已经错过的槽位，并非每次保留后固定只加一个周期。
- 触发：在没有越过多个目标槽位的稳态段，PTS 约等于“采集 deadline + 本帧采集耗时”；若首帧或上一目标相位对应的采集较慢，后续较快帧可能比当前目标点早 2 ms 以上而被丢弃。超时帧会让 FrameDropper 跳过一个或多个槽位，不能简化成“任意相邻间隔小于 `1/fps − 2 ms` 就丢”。旧代码网格 PTS 不受采集耗时抖动影响，属本次新引入的回归。
- 建议：去掉该 key（采集节奏已由 `nextCaptureAtMs` 循环控制），或用采集 deadline 而非完成时刻打 PTS。
- 验证：需真机录制并对比 MP4 实际帧数（未验证）。

### H2. `lib-mvvm`：倒计时对外值取整与 `delay` 提前唤醒叠加，UI 先重复后跳跃（第二轮降为 MEDIUM，见 F3）

- 位置：`lib-mvvm/src/main/kotlin/com/leovp/mvvm/ScreenCountdownManager.kt:114-115`（`elapsedMillisSince`）、`:124-126`（`remainingMillisAt`）、`:157-159`（tick 循环）
- 缺陷：`elapsedMillisSince` 向下取整，`remainingMillisAt` 向上取整到整 tick，而 `BaseViewModel.kt:86-87` 传入的 `viewModelScope` 在 `Dispatchers.Main` 上 `delay` 走 `Handler.postDelayed`，以截断后的 `uptimeMillis` 判定到期，可比 `System.nanoTime` 差值早最多约 1 ms 唤醒。
- 触发：`startedAtNanos` 毫秒小数部分非零时，首个 tick 醒来 elapsed=999，remaining 向上取整回到初值，UI 连续两次收到 60000；下一轮 `waitMillis=1001` 后直接跳到 58000。表现为「停一秒再跳两秒」。
- 附带：直接使用 `System.nanoTime()` 使 `runTest` 虚拟时间下 `remaining` 永不递减；该模块没有配套单测。
- 建议：对外值按 tick 序号推导（`countdownDurationMillis − nextTickAtMillis`），时钟只用于调度；注入时钟以便测试。

## 3. MEDIUM

### 3.1 公开 API 行为变更未按「API 演进约定」记录

按当前 `AGENTS.md` 的公开 API 规则，升级后的既有调用方需要调整时，应在对应公开 API 的 KDoc 追加影响与迁移说明；面向使用者的行为变化还应记入 `CHANGELOG.md`。以下三处均缺失：

| # | 位置 | 变更 | 缺失 |
|---|------|------|------|
| M1 | `yuv/src/main/cpp/YuvUtilNative.cpp:233-236`、`yuv/src/main/kotlin/com/leovp/yuv/YuvUtil.kt:32-47` | 被 `@Deprecated` 保留的 `android420ToI420(ByteArray, pixelStrideUV, …)` 现对 `pixelStrideUV != 1` 直接抛 `IllegalArgumentException` | `@Deprecated` 消息与 KDoc 均未说明。`YUV_420_888` 半平面帧的 `pixelStride == 2` 是最常见输入，升级后从返回数据变为崩溃 |
| M2 | `androidbase/src/main/kotlin/com/leovp/androidbase/ui/LeoTextureView.kt:42` | `var isH265` 由 public 改为 private | 无 KDoc 迁移说明，`CHANGELOG.md` 无条目 |
| M3 | `pref/src/main/kotlin/com/leovp/pref/base/AbsPref.kt:35-38`、`:67-70` | 序列化失败时从「写入 `""`」改为「静默跳过、保留旧值」，读取方拿到过期对象而非 null | KDoc 与 `CHANGELOG.md` 均未记录；`AbsPrefTest` 已把新行为固化为预期 |

### 3.2 其余 MEDIUM

- **M4** `audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt:231`：`require(payload.data.isNotEmpty())` 把「文件以 start code 结尾」判成会话失败并走 `errorCallback`，而 `OpusFramedFileReaderTest.kt:57` 明确把该形态当合法输入。触发：录音在写完 `START_CODE` 后、`data` 前被截断（掉电/崩溃）的 .opus 文件，本应自然结束却报错。
- **M5** `Screenshot2H26xStrategy.kt:846` → `:308`/`:330`：`MediaCodec` 在无 Looper 的 EGL 线程创建，回调落到主 Looper；于是 `MediaMuxer` 构造（打开文件）、每帧 `writeSampleData` 和 `ByteArray(info.size)` 拷贝全在 UI 线程执行。demo 开启 StrictMode 即报 DiskRead/Write。第一轮的“让 EGL 线程带 Looper”建议不完整；API 21–22 必须在专用 Looper 线程创建 codec，API 23+ 应使用双参数 `setCallback(cb, handler)`，完整方案见 F2。
- **M6** `demo/.../ffmpeg/utils/DecodeH264RawFileByFFMpeg.kt:210`、`DecodeH265RawFileByFFMpeg.kt:319`：协程体内 `Thread.sleep()` 阻塞 IO 线程，`Job.cancel()` 不会像线程中断一样立即唤醒它；仍是「干完活再睡固定差值」的旧节奏模型，与同分支 `DecodeH265RawFile.kt` 改成的 `delay()` + 移动 deadline 不一致。第一轮关于 `isClosed`/`isDecoding` 缺少可见性保护的判断错误：当前两个类的这两个字段均已标注 `@Volatile`。
- **M7** `floatview/src/main/kotlin/com/leovp/floatview/framework/FloatViewImpl.kt`：文件 795 行（本次 +214），紧贴项目 800 行上限；`show()`/`remove()`/`finishRemoval()`/`detachWindow()` 之间 `windowAdded`/`removing`/`removalFailed`/`released`/`listenersRegistered` 状态位耦合密集。建议拆出窗口附着与动画两块。

## 4. LOW

### 音频与 Netty

- `audio/.../aac/AacStreamPlayer.kt:249`、`.../opus/OpusStreamPlayer.kt:243`：`stopped.set(true)` 先于 `StreamPlayerStopper.stopAndJoin()` 的自作用域守卫执行；守卫抛 IAE 后播放器已拒收帧但 decoder/track 未释放。仅在误从 ioScope 子协程调用时触发。
- `basenetty/.../client/BaseNettyClient.kt:904`：`releaseNetty()` 超时返回 false 后 `connectStatus` 永久停在 RELEASING，再次 `release()` 在 `:867` 守卫处返回 false，worker group 无补偿路径。与 `BaseNettyServer.stopServer()` 的 finally 必落 STOPPED 不对称。**与 `2026-10-16-basenetty-client-release-terminal-state_cc.md` 记录的是同一问题，方案见该文档 §4/§5，仍待决定。**
- `audio/.../MicRecorder.kt:231`：`startRecord()` 无重入守卫，二次调用会在同一 AudioRecord 上再起一个 record job 并覆盖 `recordJob`，`stopRecordAndJoin()` 只 join 最新的那个。预存问题，但本轮重写了该函数。
- `audio/src/test/.../BaseMediaCodecAsynchronousTest.kt:63`、`:432`、`:483`、`BaseMediaCodecSynchronousTest.kt:41`：`assertFailsWith<TimeoutException> { releaseFuture.get(100ms) }` 加 `verify(exactly = 0)` 在慢 CI 上无法区分「被锁阻塞」与「尚未调度」，证明力弱但不会误报失败。
- `demo/.../audio/sender/AudioSender.kt:164`：`start()` 非挂起，在主线程构造 `MicRecorder`（打开 AudioRecord、启动编码器）并 `startRecord()`；`AudioActivity.record()` 同样。预存。

### 共享基础库

- `lib-common-android/.../utils/NetworkUtil.kt:300-312`：同一 `callback` 重复注册且旧回调 `unregisterNetworkCallback` 失败时直接 `return`，新回调既未注册也不通知调用方；`NetworkMonitor.startMonitor()` 会认为注册成功，`globalWifiSignal` 始终为 null。

### 录屏与相机

- `Screenshot2H26xStrategy.kt`：全文 1232 行，超出项目 800 行上限（detekt `LargeClass` 阈值 1300 不会报）。
- `Screenshot2H26xStrategy.kt:588-605` 与 `ScreenProcessor.kt`：接口 KDoc 仍说 `onInit()` 在主线程调用，实现却要求禁止主线程（`runOnEglThread` 阻塞 ≤5 s，任务被领取后无上限）；`ScreenCapture.Builder.build()` 返回 `ScreenProcessor`，按接口文档写的调用方会在主线程阻塞。
- `screencapture/src/test/.../Screenshot2H26xStrategyTest.kt`：纯 JVM 下依赖 android.jar stub 加 `mockkStatic(EGL14)`，`HandlerThread.start()` 与 `TextureRenderer()` 的 GLES 调用未被 mock；后续 §14 的 `staticCheck --rerun-tasks` 已确认测试能够运行，但其 mock/default-value 环境仍不能证明真实 GLES/Codec 行为。

### 原生模块

- `jpeg/src/main/cpp/JPEGNative.cpp:180-183`：`GetStringUTFChars` 返回 null（OOM 已挂起）后仍调用 `AndroidBitmap_unlockPixels`，属「异常挂起时调用 JNI」。极端内存不足时才触发。
- `h264-hevc-decoder/.../H264HevcDecoder.kt`、`adpcm-ima-qt-codec/.../AdpcmImaQtDecoder.kt`、`AdpcmImaQtEncoder.kt`：实现了幂等 `Closeable`，但与 `lib-image/.../BitmapProcessor.kt:120-128` 不同，没有 `finalize`/`Cleaner` 兜底；忘记 `close()` 时 `AVCodecContext` 与帧缓冲随对象被 GC 永久泄漏且无日志。
- `ffmpeg-sdk/src/main/cpp/adpcm_ima_qt_decoder/adpcm_ima_qt_decoder.cpp:9`、`:60`；`adpcm_ima_qt_encoder/adpcm_ima_qt_encoder.cpp:9`、`:65`、`:82`：构造/析构的普通信息日志使用 `LOGE`。
- `ffmpeg-javacpp/build.gradle.kts:13`：`abiFilters` 收窄为仅 `arm64-v8a`，README 已说明但无 Gradle 期校验；消费方 APK 若仍含 `armeabi-v7a`，32 位设备运行到 `AdpcmImaQTDecoder` 才抛 `UnsatisfiedLinkError`。
- `10-configs/ffmpeg-runtime-consumer-test/verify.sh:26`、`:49`：依赖 `rg` 但未检查其存在，`set -euo pipefail` 下缺 `rg` 会以无意义错误中断。

## 5. 待核实（需真机或编译确认）

1. `screencapture/.../ScreenRecordMediaCodecStrategy.kt:426`：匿名 `MediaProjection.Callback` 注册后从未 `unregisterCallback`，`onRelease()` 与 `changeOrientation()` 都不处理。diff 外的既有问题，但本次修了 `onRelease()` 泄漏却没一起收口。
2. `OpusFilePlayer.kt:75`、`:200`：生产者在解码后队列 ≥48/64 时暂停，但已进入 `OpusDecoder` 输入队列（容量 64）和 codec 输入槽的帧仍会产出 PCM；若在途帧 >16，`queue.offer` 失败即 `requestFailure("Decoded PCM queue is full")` 整段会话终止，而非丢帧降级。实际在途深度取决于 codec 输入槽数。
3. `Screenshot2H26xStrategy.kt`：对 HEVC 也写 `KEY_PROFILE=AVCProfileBaseline`/`KEY_LEVEL=AVCLevel51`（既有）；叠加新 MP4 输出后需确认 HEVC MP4 可播。
4. `demo/.../mediaplayer/base/DecodeH265RawFile.kt`：消费端 `queue.poll()` 为空时仍 `queueInputBuffer(size=0)` 且 `++frameCount` 推进 PTS；喂入端改为 24 fps 节奏后「队列为空」成为常态，可能形成空包忙循环并拉偏时间轴。
5. `androidbase/.../NetworkMonitor.kt:127-150`（行号已按 2026-10-21 实施后的代码更新）：用户 `callback` 在持有 `monitorLifecycleLock` 的监控线程上执行；若回调里对主线程同步等待而主线程同时在 `onDestroy` 调 `stopMonitor()`，会死锁。KDoc 已声明限制，demo 与 `BaseActivity` 的回调是否全部异步未逐一核实。**第六轮已核实并转为确认项 G13**：`BaseActivity.kt:264` 用 `runOnUiThread` 异步投递、不会触发，但使用方自行写同步等待即可死锁，作者决定结构性修掉，不再只靠文档约束。
6. `floatview/.../FloatViewOwner.kt` 与 `FloatViewImpl.finishRemoval()`：`onActivityDestroyed → remove(true)` 若 `detachWindow` 抛非 `IllegalArgumentException`，`released=false`、`owner.release()` 不执行，Activity 被静态 `windowMap` 与 Application 回调强引用直至下一次手动 `remove(true)`。该异常路径真机是否可达未确认。
7. `basenetty/.../BaseNettyClient.kt:928`、`:945`：`closeChannelAndAwait()` 被 3 s 超时取消时，`finally` 只发出 `shutdownGracefully` 不等待，`terminationFuture` 永不被观察；group 未在 2 s 内终止将无日志。设计取舍，确认是否可接受。
8. `SuspendTeardownGate.run` 的 CancellationException 分支对 follower 发布成功；`AudioActivity.launchCleanup` 对 leader 只 rethrow 不记日志，故 `MicRecorder.finishRecorderReleaseAndJoin` 在 NonCancellable 内重抛的 CancellationException 只剩 gate 内一条 error 日志。确认是否在 `launchCleanup` 补一行日志。
9. `ffmpeg-sdk/src/main/cpp/h264_hevc_decoder/h264_hevc_decoder_all_in_one_file.cpp:581-586`：drain 阶段若 `avcodec_receive_frame` 返回 `EAGAIN`，函数抛异常并 `DeleteLocalRef(output)`，本次已取出的帧被丢弃。按 FFmpeg 契约 flush 后不应出现 `EAGAIN`，理论不可达，但一旦出现即静默丢帧；建议改为返回已取得的帧。
10. 12 个 `.so` 二进制：`.gitattributes:15` 对 `*.so` 启用 LFS，HEAD 中 blob 为 LFS 指针；四个 ABI 目录均已更新；16KB 对齐在 `ffmpeg-sdk/src/main/cpp/CMakeLists.txt:30`、`:55`、`:80`、`:105` 与三个 `build_ffmpeg_*.sh:36-37` 中均已配置。实际 ELF 段对齐未检查。

后续 `staticCheck` 已解除两项纯构建疑问：`AdpcmImaQTDecoder.kt` 的 `av_frame_free` 重载能够编译；第一轮列出的三处超 100 列行在当前工作树均已拆分，ktlint 已通过。它们不再属于「待核实」。

## 6. 未发现问题的区域

- **音频与 Netty**：`BaseMediaCodec` 生命周期状态机（start/releaseAndJoin 竞态、二次 join、`releaseCodecOnce` 幂等）；`BaseMediaCodecSynchronous` EOS 排空与失败单次上报；`BaseMediaCodecAsynchronous` 回调加锁与缓冲归还；`AacFilePlayer`/`OpusFilePlayer` 终态单入口与回调顺序；`StreamPlayerStopper` 的 stop→join→release 顺序；`NettyFutureExt.awaitCompletion` 的 isDone 快路径与取消处理；`BaseNettyServer` 互斥 + NonCancellable + finally 落 STOPPED；demo 三个 Activity 的「先 launch 再 cancel」修正；`AudioReceiver`/`AudioSender` 的 `stopping` 先置位 + 发布后复查补偿。ktlint 行长、import、调用点尾随逗号、detekt 阈值均未发现新增违规。
- **共享基础库**：`log`（`isLoggable` 下沉、`LogExt` 级别短路及测试）；`lib-network`（`payloadOperationOrNull` 保留 `CancellationException`/`Error` 透传，`errorData` 有迁移 KDoc 与 CHANGELOG）；`ForegroundComponent` 单例发布顺序与主线程 Handler；`OnSingleClickListener`/`DebounceExt` 改用 `elapsedRealtime` 并可注入时钟；`LifecycleViewModelBridge` 键修正；`android-restricted` 重命名。第一轮曾把 `ToastExt`/`FloatViewCreator.tryShow` 回退链列为无问题，第二轮已由 G1–G3、G6 推翻。`lib-json`、`lib-common-kotlin`、`http` 在该区间无改动。
- **录屏与相机**：`Mp4TrackWriter` 的锁内 detach 后再 close、CODEC_CONFIG/EOS 标志剥离、PTS 单位（EGL ns / muxer µs / camera µs）成立，但创建/关闭异常路径存在 §11.1 F10；`CameraAvcEncoder` 的队列回压与时间戳方案成立，但 API 21–22 callback 线程归属并入 F2；`Falcon` 超时与回收；demo 的会话号守卫与 `Configuration` 旋转重建；`ScreenRecordMediaCodecStrategy.onRelease()` 泄漏修复。第一轮对 `Screenshot2H26xStrategyTest` 可执行性的疑问已由 §14 的实际 `staticCheck` 解除，但 JVM/Robolectric 通过仍不能替代真实 GLES/Codec 验证。
- **原生模块**：`lib-image` 的 `BitmapRotateNative.cpp` 与 `BitmapProcessor.kt`（handle 判空、尺寸溢出、局部引用、`@Synchronized`、幂等 `close`）；`yuv` 的 `ProcessByteArray` 模板（`Release*Elements` 模式、长度/步长校验、直接缓冲区容量校验）；ADPCM JNI 层（`GetByteArrayElements` 全部配对 `JNI_ABORT`、回调期间异常挂起处理、编码回调中 `release()` 的延迟释放）；H.264 解码主路径（send/receive 的 `EAGAIN` 回压循环、`FrameUnrefGuard`、循环内局部引用逐个删除、`drainStarted` 双侧一致校验）；`ffmpeg_build` 脚本；三个 FFmpeg 模块 README。
- **主会话另审**：根 `build.gradle.kts` 为三个 FFmpeg 原生模块的 release 发布配置声明共享 capability `com.leovp.android:ffmpeg-native-runtime`，并移除 `h264-hevc-decoder` 的 `pickFirsts`，与 demo 仅依赖 `adpcm-ima-qt-codec-h264-hevc-decoder` 一致；`opengl/GLRenderer.kt` 的尺寸判断 `&&` 改为 `||` 为正确修复，`@Synchronized` 与 `onDrawFrame` 的 `synchronized(this)` 同锁；demo Manifest 为 `RecordSingleAppScreenActivity` 增加 `configChanges` 与旋转存活改动配套。

## 7. 建议的处理顺序

1. 真机核实 H1，若确认则去掉 `KEY_MAX_FPS_TO_ENCODER` 或改用 deadline PTS。
2. 修 F2 的 callback 线程归属，并修 F3、补 `ScreenCountdownManager` 单测（注入时钟）。
3. 修 F10 的 muxer 失败清理与 G4/G9 的 Wi-Fi callback 会话隔离和失败脱钩。
4. 为 M1–M3 补 KDoc 迁移说明与 `CHANGELOG.md` 条目。
5. 处理 M4（仅把“空 data 且没有下一 start code”视为自然 EOF；连续 start code 仍报损坏）。
6. 其余 LOW 与待核实项按模块排期；`BaseNettyClient` 终态问题沿用 10 月 16 日提案文档的决策。

## 8. 关联文档

- `00-documents/2026-10-15-audio-netty-teardown-review-package_cc.md`：audio/basenetty teardown 的七轮审查记录。
- `00-documents/2026-10-16-basenetty-client-release-terminal-state_cc.md`：`BaseNettyClient.release()` 终态不对称的方案提案。
- `00-documents/2026-10-14-leovp-review-fixes.md`：共享基础库 `276419f8c` 提交对应的修复记录。
- `00-documents/2026-09-18-frame-timestamp-and-pacing-sweep_cc.md`：录屏 PTS 与节奏改动的原始说明。

---

# 第二轮 · 详细审查（第 1/3 部分：录屏与共享基础库）

## 9. 范围、方法与结论

| 项目 | 值 |
|------|----|
| 本部分范围 | `screencapture`（含测试）、`camera2live`、`camerax`、`lib-mvvm`；`floatview`、`lib-common-android`、`androidbase`、demo `NetworkMonitorActivity` 与 `FloatViewWindowAndroidTest`；`lib-network`、`lib-compose`、`log`、`pref`、`opengl`、`android-restricted`。约 3.8k 新增行，占代码差异 30%。第 2/3 部分为 `audio` + `basenetty` + demo 音频/Socket，第 3/3 部分为原生模块 + demo 其余 + 构建脚本，均未开始 |
| 方法 | 3 个细审代理按切片通读改动文件全文（不只看 hunk），逐条裁决第一轮假设并给出代码级修复；随后 3 个独立复核代理对每条发现做对抗性核验（缺陷是否真实、行号是否准确、修法能否编译并通过 detekt/ktlint、是否兼容 minSdk 21、是否破坏调用方） |
| 执行方式 | 第二轮审查本身为源码静态分析，未做真机验证。文中「确定」指依据公开 API 文档或仓库代码可直接证实；「推断」已单独标注。同一 HEAD 后续执行过 `staticCheck`，见 §14；它只解除编译/静态检查/单测层面的疑问 |
| 代码改动 | 源码无改动；第三轮只更新本文档 |
| 结论 | 31 条发现：29 条确认（第二轮有 4 条修法经复核修正；第三轮又修正 F2/F3/F5/F8/F10、G1/G3–G6/G9、N3/N4/N7/N8 等方案细节；第五轮订正 FrameDropper 规则并推翻第四轮的 G3 token 判断；第六轮新增 G11/G12/G13，其中 G13 由 §5 待核实项转入；5 条取舍已与作者确认，见 §13）、2 条推翻。HIGH 2 条、MEDIUM 15 条、LOW 12 条，全部附代码级修复方案 |

## 10. 第一轮假设的裁决

| 第一轮编号 | 裁决 | 证据与修正 |
|---|---|---|
| H1 录屏丢帧 | **确认，触发条件修正** | 机制成立：Surface 输入（`createInputSurface` :467、`eglSwapBuffers` :463），`eglPresentationTimeANDROID` 写入的正是 FrameDropper 比较的时间戳。AOSP `FrameDropper.cpp`：首帧设置 `desired = timeUs + interval`；`timeUs < desired − 2000` 时丢弃且不推进；保留时计算 `n = (timeUs − desired + 2000) / interval`，再推进 `(n + 1) * interval`。因此网格锚定首帧但会跳过错过的槽位。仅在尚未跳槽的稳态段，才可近似为后续采集耗时比锚相位早 2 ms 以上会丢；第四轮写成“每次保留固定 `+ interval`”及无条件 `dur_k < dur_1 − 2 ms` 过度简化。缺陷与删除该 key 的修法不变（第五轮按 AOSP 源码订正） |
| H2 倒计时跳秒 | **确认，降为 MEDIUM** | 以 T0=5000.7 ms 重算：tick1 在 uptime 6000 唤醒 → elapsed 999 → ceil 回 60000；`_countdownState` 是 `MutableStateFlow`（:62），重复值被合并，UI 看到 60000 停留约 2 s；tick2 wait 1001 → 58000，59000 永不出现。早醒依据：`MessageQueue` 按截断的 `uptimeMillis` 比较，kotlinx `HandlerContext` 恢复后不再对 `nanoTime` 复核（推断，高置信）。影响局限于显示节拍，不破坏资源或数据，第二轮降为 MEDIUM |
| M2 `isH265` 转私有 | **确认** | 全仓无外部调用方；属公开 getter 移除，需 CHANGELOG「变更」一条 |
| M3 `AbsPref` 保留旧值 | **确认，已定方案 A** | 基线 `else -> put(key, v.toJsonString())` 失败写 `""`；现跳过写入。分支新增的 `AbsPrefTest.kt:8-14` 测试名即 `failed object serialization does not overwrite stored value`，说明「保留旧值」是作者有意为之。见 §11.3 N1 |
| M5 回调落主线程 | **确认，升为 HIGH** | EGL 线程是无线程 Looper 的 executor 线程，`createEncoderByType`（:846）因而把默认 callback handler 选到主 Looper；:1148 注释自证。拷贝（:294）、`Mp4TrackWriter` 构造（:329）、`writeSampleData`（:308）全在主线程。它把逐帧内存分配和磁盘 I/O 放入 UI 热路径，第二轮升为 HIGH，见 F2 |
| `Mp4TrackWriter` 无问题 | **推翻** | `onOutputFormat()` 在把新建的 `MediaMuxer` 写入字段前调用 `addTrack()`/`start()`；任一步抛异常时 `fail()` 看不到该局部实例，无法 `release()`。此外 `close()` 的 `stop()` 失败路径仅在 `samplesWritten == 0` 时删文件，与类内“无 `moov` 的部分 MP4 比没有更糟”约定矛盾。见 §11.1 F10 |
| M7 `FloatViewImpl` 795 行 | **事实确认，但降为维护性建议** | 触摸/停靠逻辑 :156-361 约 206 行可抽出；注意 `dockAnimator` 在 :258-260、:336 与 `cancelAnimations()` :744-746 两处写，抽取时所有权需随之迁移。当前文件尚未超过项目 800 行建议上限，也未触发 detekt `LargeClass`，不计入 §9 的 26 条缺陷；实施 G3 前若新增成员函数，应优先抽取窗口生命周期组件，避免触发 `TooManyFunctions` |
| LOW `NetworkUtil` 重注册 | **部分成立** | 旧匿名回调仍转发到同一 `callback` 实例（`NetworkMonitor.kt:56-58` 为固定 `val`），信号不丢；唯一损失是 `NetworkMonitor.kt:181` 置空后的首个快照 |
| LOW `onInit()` KDoc 矛盾 | **部分成立** | 接口 `ScreenProcessor.kt:36-37` 已写「unless the implementation documents otherwise」，与实现 :592 自洽；陈旧的是实现内部 :609-610 的注释 |
| LOW 单测能否纯 JVM 运行 | **后续构建已解除** | 全局 `isReturnDefaultValues=true`，`TextureRenderer` 构造不校验 GL 返回值；§14 的 `staticCheck --rerun-tasks` 已实际执行并通过对应 debug JVM 测试。该结果只证明测试可运行，不证明真实 GLES/Codec 行为 |
| 待核实 `NetworkMonitor` 持锁回调 | **确认为设计取舍** | KDoc :164-166 已禁止同步等主线程；`BaseActivity.kt:266` 与 demo :83 均为异步 `runOnUiThread` |
| `NetworkMonitor` Wi-Fi 回调受 generation 保护 | **推翻** | generation 只检查轮询 `Runnable`；固定的 `wifiSignalCallback` 不携带 generation。旧框架回调在 stop/restart 后迟到时仍可覆盖新会话的 `globalWifiSignal`。见 §11.2 G9 |
| 待核实 FloatView detach 失败泄漏 | **确认** | 见 §11.2 G3 |
| `log` / `lib-compose` / `opengl` 无问题 | **确认**（`opengl` 另有一处预存锁外访问，见 §11.3 N7） | — |

## 11. 确认的问题与代码级修复方案

截至 2026-10-21，本节第六轮之前确认的 26 项（F1–F10、G1–G9、N1–N8，不含 N5、G10 两项已推翻）
均已有实现改动；其中 F6、F9、G7 仍有部分要求未落地，详见 §15.1 与 §15.3。本节保留原始问题、方案与
验证要求，便于逐项追溯。JVM/Robolectric、静态检查和 Demo 编译的实际结果见 §14；依赖真实 Codec、
GLES、MediaProjection、WindowManager 或设备时序的真机项仍未执行，不能由单测结果替代。

第六轮新增的三项中，**G12 已随 2026-10-21 的实施落地**（`eaaa49e62`）；
**G13 已由 `b0c417d95`/`f6cbb985e` 完整落地，G11 在 2026-10-22 的第十轮修复后完整落地**。
远端实施记录见 §16；Codex 复核发现的剩余停止栅栏竞态、最终修复与本机验证见 G11 条目、
§15.5 及 §17。

### 11.1 录屏、相机与倒计时

#### F1 [HIGH] 删除 `KEY_MAX_FPS_TO_ENCODER`

- 位置：`screencapture/src/main/kotlin/com/leovp/screencapture/screenrecord/base/strategies/Screenshot2H26xStrategy.kt:829-833`
- 缺陷：采集循环已按 `captureIntervalMillis(fps)` 定步（:967-975），再设该键等于让抖动的真实时间戳接受第二层限流。AOSP `FrameDropper` 的目标网格以首帧 PTS 为锚：过早 2 ms 以上的候选帧被丢且目标不动；保留帧则根据 lateness 一次推进一个或多个周期。本实现 PTS 约等于“采集 deadline + 本帧采集耗时”，所以在未跳槽的稳态段，较慢的锚相位会让后续较快帧被判定为过早；发生 overrun 时目标网格会跳过错过的槽位。基线用 `frameIndex/fps` 等距时间戳，不会因采集耗时抖动触发，属本分支引入的回归。
- 修复：整块删除，含 `@Suppress("unchecked", "InlinedApi")`。`Build` 在 :834/:840 仍被引用，不会产生未用 import。`ScreenRecordMediaCodecStrategy.kt:187` 的同名设置应保留，VirtualDisplay 以刷新率推帧，需要限流。

```kotlin
// 修改前 :829-833
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    @Suppress("unchecked", "InlinedApi")
    setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, builder.fps)
}
// 修改后：删除整段
```

- 影响：无 API 变化。CHANGELOG「修复」一条。
- 依据：[MediaFormat.KEY_MAX_FPS_TO_ENCODER](https://developer.android.com/reference/android/media/MediaFormat#KEY_MAX_FPS_TO_ENCODER) 明确用于 Surface 输入编码器丢弃超出上限的帧；AOSP [`FrameDropper.cpp`](https://android.googlesource.com/platform/frameworks/av/+/nougat-mr2.1-release/media/libstagefright/omx/FrameDropper.cpp) 使用 2 ms jitter 窗口，与上述推导一致。
- 验证：真机 `setFps(20)` 录 10 s，在采集提交点和 `onOutputBufferAvailable` 分别计数，排除 CODEC_CONFIG/EOS 后比较实际保留帧数，并用 `MediaExtractor` 读取 MP4 样本数与 PTS 间隔。不要把 `adb logcat -s FrameDropper:V` 作为必需判据：AOSP 源码中的 `ALOGV` 在量产构建可能被编译关闭。

#### F2 [HIGH] MediaCodec 回调可能落到主线程，API 21–22 还需迁移创建线程

- 位置：`Screenshot2H26xStrategy.kt:846,856,294,308,329`；`CameraAvcEncoder.kt:203-217`；`LeoTextureView.kt:169-181`
- 缺陷：录屏回调在主 Looper 上执行整帧拷贝与 `MediaMuxer` 磁盘写入（StrictMode 违例），并与 `Falcon` 的主线程绘制（`Falcon.kt:156-167`，1 s 上限）争抢，加剧 F1。相同的 API 21–22 单参数 `setCallback(cb)` 路径还存在于 `CameraAvcEncoder` 和 `LeoTextureView`：两者只在 API 23+ 显式传 Handler，API 21–22 的 codec 若在主线程或无 Looper 线程创建，默认 event handler 仍会选到主 Looper。相机编码器会在回调里复制整帧，播放器则会排队输入及渲染输出；即使当前调用方碰巧不在主线程，也不应把正确性依赖于调用线程。
- 第三轮纠错：第二轮原方案对 API 21–22 的判断错误。单参数 `setCallback(cb)` 使用的是 codec **创建时**选定的默认 event handler；把这次调用 post 到另一个 Looper 并不会迁移 handler。并且 [MediaCodec.setCallback](https://developer.android.com/reference/android/media/MediaCodec#setCallback(android.media.MediaCodec.Callback,%20android.os.Handler)) 的契约要求异步 callback 在 `configure()` **之前**设置。正确顺序如下：
  - API 23+：可在 EGL 线程创建 codec，但先 `setCallback(cb, callbackHandler)`，再 `configure()`。
  - API 21–22：必须在 `scr-rec-codec` 的 Looper 线程上完成 `createEncoderByType()` 和单参数 `setCallback(cb)`，把创建结果安全交还 EGL 线程后再 `configure()`。
- 录屏修复：不新增外层成员函数，避免把当前 31 个函数继续推向 detekt 阈值；在 `initResources()` 内完成有界交接。下面省略现有 mime 计算，但保留超时竞态的所有权处理，不能只用裸 `FutureTask.get()`，否则任务在超时后才创建出 codec 时会泄漏。

```kotlin
// 新增字段（生命周期由 EGL 线程管理）
private var codecCallbackThread: HandlerThread? = null

val mime = when (builder.encodeType) {
    ScreenRecordMediaCodecStrategy.EncodeType.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
    ScreenRecordMediaCodecStrategy.EncodeType.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
}
val callbackThread = HandlerThread("scr-rec-codec").apply { start() }
codecCallbackThread = callbackThread
val callbackHandler = Handler(callbackThread.looper)

val encoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    MediaCodec.createEncoderByType(mime).also {
        h26xEncoder = it // publish before later setup can throw
        it.setCallback(mediaCodecCallback, callbackHandler)
    }
} else {
    val abandoned = AtomicBoolean(false)
    val created = AtomicReference<MediaCodec?>(null)
    val task = FutureTask {
        val codec = MediaCodec.createEncoderByType(mime)
        created.set(codec)
        try {
            check(!abandoned.get()) { "Codec creation was abandoned" }
            codec.setCallback(mediaCodecCallback)
            check(!abandoned.get()) { "Codec creation was abandoned" }
            codec
        } catch (error: Throwable) {
            if (created.compareAndSet(codec, null)) codec.release()
            throw error
        }
    }
    check(callbackHandler.post(task)) { "Codec callback thread rejected creation" }
    try {
        task.get(CALLBACK_SETUP_TIMEOUT_MS, TimeUnit.MILLISECONDS).also {
            check(created.compareAndSet(it, null)) { "Codec ownership handoff failed" }
            h26xEncoder = it
        }
    } catch (error: Throwable) {
        abandoned.set(true)
        task.cancel(true)
        created.getAndSet(null)?.release()
        throw (error.cause ?: error)
    }
}
encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
```

- 第四轮补充：`CALLBACK_SETUP_TIMEOUT_MS` 目前不存在（companion 只有 :117-119 三个常量），需新增，建议 2000 ms。纯 JVM 测试 `Screenshot2H26xStrategyTest.kt:43-44` 在 `SDK_INT=0` 下会走 API<23 分支，存根 `Handler.post()` 返回 `false` 使 `check` 抛出，现有打桩会失败，须同步 mock `HandlerThread`/`Handler.post` 或改走 Robolectric。
- 相机与播放器修复：`CameraAvcEncoder` 和 `LeoTextureView.initDecoder()` 的 API 21–22 分支复用同一套“在各自 callback HandlerThread 创建 codec + 单参 `setCallback` + 有界交接所有权”的模式；API 23+ 保留双参数 overload，但必须继续保证 callback 在 `configure()` 前设置。不要只把 `setCallback` post 到 HandlerThread，也不要让 `configure()` 早于创建任务完成。各自现有的 `stopCodecCallbackThread()` / `stopDecoderCallbackThread()` 继续负责 `quitSafely + join`，初始化失败也必须走到这里。
- teardown：在 codec 已从所有者字段 detach 并 release 后，先 `quitSafely()`；若当前线程不是 callback thread，则最多 `join(1_000)` 并在仍存活时记录 warning。只有清理恰好运行在 callback thread 时跳过 join，避免自等待；不能一概不 join，否则 release 返回时线程仍可能存活。
- 复核要点：`post()` 失败、创建超时、创建/`setCallback`/`configure` 异常均必须进入现有初始化失败清理；API 21–22 超时任务即使迟到也会看到 `abandoned` 或由 `created.getAndSet(null)` 释放，所有权只能交接一次。录屏监听器仍经 `screenshotHandler.post` :309 投递。`Screenshot2H26xStrategy.kt` 当前已经导入 `AtomicReference`、`FutureTask`、`TimeUnit`，实现时复用并在完成后清理未使用 import，再核对 detekt/ktlint。
- 配套：`Screenshot2H26xStrategyTest.kt:44` 目前只打桩单参 `setCallback`；三个类都补 API 23+「双参数 callback 在 configure 前调用」和 API 21–22「codec 创建与单参数 callback 均发生在专用线程，且在 configure 前完成」测试；另补 setup 超时/异常时 codec 与 HandlerThread 都被释放的用例。
- 验证：至少在一台 API 21–22 与一台 API 23+ 真机记录三个类的**实际 callback 执行线程**，均应为对应专用线程；录屏在 `StrictMode.enableDefaults()` 下无主线程磁盘 I/O 违例，release 后 callback 线程在 1 s 内消失。

#### F3 [MEDIUM] 倒计时对外值按 tick 推导，并注入单调时钟

- 位置：`lib-mvvm/src/main/kotlin/com/leovp/mvvm/ScreenCountdownManager.kt:124-128`、:155-159
- 修复：时钟只负责排期，对外值由到期的 tick 决定；删除 `remainingMillisAt()`，否则 detekt `UnusedPrivateMember`。同时把 `System.nanoTime` 抽成 internal primary constructor 的 `nowNanos: () -> Long`，现有 public secondary constructor 传 `System::nanoTime`，测试传 `testScheduler.currentTime * 1_000_000`。这样不把测试钩子暴露为公开 API，现有构造调用也无需调整。

```kotlin
// elapsedMillisSince() 改用 nowNanos()。
// 修改前 :159
remaining = remainingMillisAt(startedAtNanos)
// 修改后：值由已经到期的 tick 决定，不再对实际余量 ceil。
remaining = (countdownDurationMillis - nextTickAtMillis).coerceAtLeast(0L)
```

- 复核要点：`nextTickAtMillis` 每轮 +1000，序列单调递减到 0 必然退出；:170 `remaining <= warningThresholdMillis` 对 tick 值仍成立；主线程卡顿后仍按截止时间快速补发错过的 tick。无公共 API 变化。
- 验证：用 `runTest` 收集 `countdownState`，断言 60 s 序列为 `60000, 59000, …, 0`、相邻差恒为 −1000、warning 恰好一次、timeout 恰好一次；再用可控 `nowNanos` 模拟第一次 delay 恢复时只过去 999 ms，确认仍只发布 59000 而不重复 60000。仅推进 test scheduler、仍读取真实 `System.nanoTime()` 的测试不能声称覆盖该竞态。

#### F4 [MEDIUM] `onRelease()` 未启动即释放泄漏 `scr-rec-send` 线程

- 位置：`screencapture/.../ScreenRecordMediaCodecStrategy.kt:254-265`；`onStop()` :268-270；`releaseHandler()` :320-325
- 缺陷：`onInit()` 末尾 :251 `initHandler()` 创建线程；`onRelease()` 只经 `onStop()` 释放，而 `onStop()` 在 `videoEncoderLoop=false` 时提前返回。本分支修了同路径的编码器/VirtualDisplay 泄漏，漏了这一处。
- 修复：`releaseHandler()` 已存在且幂等。

```kotlin
// onRelease() 修改后
onStop()
releaseHandler() // onStop() returns early when never started; quit the thread anyway
h26xEncoder?.release()
```

- 验证：Robolectric 下 `onInit(); onRelease()` 后 `Thread.getAllStackTraces()` 无 `scr-rec-send`。

#### F5 [MEDIUM] 相机编码 PTS 改为到达即打戳，`queue` 收为 private

- 位置：`camera2live/src/main/kotlin/com/leovp/camera2live/codec/CameraAvcEncoder.kt:33`（`val queue: ConcurrentLinkedQueue<ByteArray>` 公开）、:229-232（入队不打戳）、:252（`submit` 时才调用 `elapsedMicrosSinceFirstFrame()`）、:360（`MAX_PENDING_FRAMES = 5`）、:388-392（`PendingInputBuffers.drain` 写死 `ByteArray`）
- 缺陷：帧在 `BoundedFrameQueue` 中最多滞留 5 个周期，编码器变慢时多帧在同一次 `drainInputBuffers` 内被打上几乎相同的时间戳，时间轴成簇、抖动，与 :286-298 KDoc 声称的真实时间轴不符（单调性仍成立）。
- 决定（2026-10-20，用户确认）：`queue` 收为 `private`。仓库内无外部读取者（`camera2live`、demo `git grep` 为空）。
- 修复：审查者原方案不能编译（`internal` 类型出现在 public 属性类型里触发 `EXPOSED_PROPERTY_TYPE`；泛型化后 `drain` 推断不出 `T`），采用复核修正版。

```kotlin
internal class EncoderFrame(val data: ByteArray, val presentationTimeUs: Long)

internal class BoundedFrameQueue<T>(
    private val capacity: Int,
    private val onElementDropped: () -> Unit = {}, // independent of the element type
) : ConcurrentLinkedQueue<T>() {
    // offer/addAll: replace ByteArray with T, logic unchanged
}

internal class PendingInputBuffers {
    fun <T> drain(
        nextFrame: () -> T?,
        submit: (bufferId: Int, data: T) -> Unit,
        onFailure: (Exception) -> Unit,
    ) { /* unchanged */ }
}

// CameraAvcEncoder
private val queue: ConcurrentLinkedQueue<EncoderFrame> =
    BoundedFrameQueue(MAX_PENDING_FRAMES, ::onEncoderFrameDropped)

fun offerDataIntoQueue(data: ByteArray) {
    synchronized(inputBufferLock) {
        if (!acceptingFrames.get()) return
        // Stamp on arrival, not when an input buffer happens to be free.
        queue.offer(EncoderFrame(data, elapsedMicrosSinceFirstFrame()))
    }
    // rest unchanged
}

// drainInputBuffers(): submit = { id, frame ->
//     inputBuffer.putEncoderFrame(frame.data)
//     codec.queueInputBuffer(id, 0, frame.data.size, frame.presentationTimeUs, 0)
// }
```

- 复核要点：首帧原点在第一帧**到达**时锁存；若该帧随后被 `BoundedFrameQueue` 挤掉，首个送入编码器的 PTS 大于 0，MediaCodec 与 muxer 均不要求从 0 起。:303 KDoc「Called only from drainInputBuffers」需同步改写。`BoundedFrameQueueTest.kt:13,27,39` 写成 `BoundedFrameQueue<ByteArray>(2) { … }`；`PendingInputBuffersTest` 可由类型推断，不必改。
- 影响：公开属性 `queue` 移除，CHANGELOG「变更」加粗条目「**`CameraAvcEncoder.queue` 不再公开**」；`CameraAvcEncoder` 类 KDoc 追加迁移说明：内部队列深度不再属于公开契约，现阶段只能从丢帧日志观察过载。`onEncoderFrameDropped()` 当前是 private，不能把它写成调用方迁移目标；若确有结构化指标需求，应另行设计只读计数或公开 observer，而不是重新暴露可变队列。
- 验证：单测注入慢 `submit`，断言两帧 PTS 差约等于入队间隔而非约 0。

#### F6 [LOW] 删除 `keyFrameRate` 兼容路径与无调用方的 `computePresentationTimeUs`

- 事实：基线把 `builder.keyFrameRate` 写入 `KEY_FRAME_RATE`（基线 `ScreenRecordMediaCodecStrategy.kt:167`、`Screenshot2H26xStrategy.kt:318`）；提交 `7245072f4` 改为写入 `fps` 后，`keyFrameRate` 字段与 `setKeyFrameRate()` 不再接任何东西，KDoc 自述「Kept so existing callers still compile」，与当前 `AGENTS.md`“不因兼容旧版本保留旧入口、旧签名或旧实现”的规则冲突。`ScreenProcessor.kt:74-81` 的 `computePresentationTimeUs(frameIndex, fps)` 加了 `@Deprecated` 但无 `ReplaceWith`，全仓无调用方（`git grep` 命中的均为各类自己的私有单参函数或 audio 的 `IAudioMediaCodec` 接口）。
- 澄清：demo 的 `ScreenShareSetting`（`ScreenShareMasterActivity.kt:525-530`，`@Keep data class(width, height, dpi)` + 体内 `var keyFrameRate = 8`）只在进程内传给 `MediaProjectionService.startScreenShare()` 并 `toJsonString()` 记日志，**不经网络传递**，删字段无串流兼容问题。
- 决定（2026-10-20）：库侧与 demo 侧一并删除。
- 删除清单（复核补全）：

| 文件 | 行 | 内容 |
|---|---|---|
| `screencapture/.../screenrecord/ScreenCapture.kt` | :47、:65、:101、:116、:133 | 字段、`setKeyFrameRate()`、日志字段、两处透传 |
| `screencapture/.../strategies/ScreenRecordMediaCodecStrategy.kt` | :117-124、:134、:143 | 字段与 KDoc、`setKeyFrameRate()`、日志字段 |
| `screencapture/.../strategies/Screenshot2H26xStrategy.kt` | :366-373、:392 | 字段与 KDoc、`setKeyFrameRate()` |
| `screencapture/.../base/ScreenProcessor.kt` | :74-81 | `computePresentationTimeUs(frameIndex, fps)` 整个函数 |
| demo `sharescreen/master/MediaProjectionService.kt` | :259 | `.setKeyFrameRate(setting.keyFrameRate)` 整行 |
| demo `sharescreen/master/ScreenShareMasterActivity.kt` | :530、:176 | `ScreenShareSetting.keyFrameRate` 属性、注释行 |
| demo `RecordSingleAppScreenActivity.kt` | :261-262 | 已过时的说明注释 |

- 影响：公开 Builder API 移除，CHANGELOG「变更」加粗条目「**`screencapture` 移除无效的 `setKeyFrameRate()` 与 `ScreenProcessor.computePresentationTimeUs()`**：帧率由 `setFps()` 决定并写入 `KEY_FRAME_RATE`，调用方删除 `setKeyFrameRate()` 调用即可」；三个 Builder 的 `setFps()` KDoc 追加同一句迁移说明；`ScreenProcessor` 接口 KDoc 增加「需要 PTS 时由实现按输入源的单调时间戳计算，不再调用已删除的帧序号辅助函数」，覆盖直接实现者的迁移入口。
- 验证：`./gradlew :screencapture:compileDebugKotlin :demo:compileDevDebugKotlin detekt`（未验证）。

#### F7 [LOW] HEVC 路径不写 AVC profile/level

- 位置：`Screenshot2H26xStrategy.kt:834-843`
- 缺陷：`AVCProfileBaseline = 0x01 = HEVCProfileMain` 恰巧合法，但 `AVCLevel51 = 0x8000 = HEVCHighTierLevel5`，部分 HEVC 编码器拒绝 configure（常量值确定）。基线已如此，叠加新 MP4 输出后更值得修。
- 修复：文件内无 `EncodeType` 直接 import，用限定名（:808-809 同文件已这样写）；`builder.encodeType` 是 `fallBackToAvcIfMuxerCannotCarryHevc()`（:420）之后的实际值，读取时机正确。

```kotlin
if (builder.encodeType == ScreenRecordMediaCodecStrategy.EncodeType.H264) {
    // 原 :834-843 的 KEY_PROFILE / KEY_LEVEL 两段原样放入
}
```

- 验证：API 24+ 真机 H265 + MP4 `configure()` 不抛 `CodecException`。

#### F8 [LOW] `MediaProjection.Callback` 只注册不注销

- 位置：`ScreenRecordMediaCodecStrategy.kt:424-428`；调用点 :239（`onInit`）、:397（`takeScreenshot`）；`changeOrientation()` :296-311 再次 `onInit()` 又注册一次
- 修复：保存为字段，只注册一次，`onRelease()` 中 `unregisterCallback`；`takeScreenshot()` 不再重复注册。附带：`registerCallback(cb, null)` 内部 `new Handler()`，要求 `onInit()` 在有 Looper 的线程调用，现状即如此。

```kotlin
private val projectionCallback = object : MediaProjection.Callback() {}
private val projectionCallbackLock = Any()
// Guarded by projectionCallbackLock.
private var projectionCallbackRegistered = false

private fun registerCallbackAboveAndroid14() {
    synchronized(projectionCallbackLock) {
        if (projectionCallbackRegistered) return
        mediaProjection.registerCallback(projectionCallback, null)
        projectionCallbackRegistered = true
    }
}
// onRelease() 开头
synchronized(projectionCallbackLock) {
    if (projectionCallbackRegistered) {
        // Clear library ownership even if the platform rejects teardown.
        projectionCallbackRegistered = false
        try {
            mediaProjection.unregisterCallback(projectionCallback)
        } catch (error: Exception) {
            LogContext.log.e(TAG, "Unable to unregister projection callback.", error)
        }
    }
}
```

- 复核要点：`changeOrientation()` 与截图路径可能和释放交错；裸 Boolean 的 check-then-act 会重复注册，仅换成 `AtomicBoolean` 也无法阻止“注销已把标记置 false，新注册完成后，旧注销再把同一个 callback 注销”的交错。因此同一私有锁必须覆盖检查、平台调用和状态提交的完整事务。`registerCallback` 抛异常时赋值尚未发生，状态自然保持 false；注销异常是 teardown 的次要失败，记录后必须继续释放 VirtualDisplay、codec 和 HandlerThread。callback 本身为空实现，不会重入该锁。补并发注册只调用平台一次、注册与注销不交错、注册失败后可重试、注销异常不阻断其余释放的测试。

#### F9 [LOW] 陈旧注释与文件体量

- `Screenshot2H26xStrategy.kt:609-610`「documented main-thread caller」与 :588「Do not call this on the main thread」矛盾，改为「a caller that ignores the documented restriction」。
- :75 注释写 29 个函数，实际 31（detekt `thresholdInClasses: 33`，`ignorePrivate/ignoreOverridden: false`），F2 方案刻意不新增函数。
- 文件 1232 行，超项目 800 行上限。建议把 `Builder`（:347-432）与 EGL 初始化/释放（:466-603）拆到独立文件。

#### F10 [MEDIUM，Codex 复核新增] `Mp4TrackWriter` 失败清理不完整

- 位置：`screencapture/src/main/kotlin/com/leovp/screencapture/screenrecord/base/Mp4TrackWriter.kt:53-68`、`:124-140`
- 缺陷 1：`onOutputFormat()` 先把新建的 `MediaMuxer` 保存在局部变量 `created`，调用 `addTrack()`、`start()` 成功后才赋给字段 `muxer`。若 `addTrack()` 或 `start()` 抛异常，`fail()` 读取到的字段仍为 `null`，只能删输出文件，无法释放已创建的 native muxer/文件描述符。
- 缺陷 2：`close()` 在 `MediaMuxer.stop()` 抛异常后会记录“文件不完整”，但 `finally` 只在 `samplesWritten == 0L` 时删除文件。已经写过样本但没有成功写入 `moov` 的文件会以正常 `.mp4` 文件名残留，与 `fail()` KDoc 的“部分 MP4 比没有更糟”约定矛盾。
- 修复：创建后立即发布字段所有权，使公共失败路径能释放；`close()` 只在 `stop()` 成功后保留文件。

```kotlin
fun onOutputFormat(format: MediaFormat) {
    if (broken || muxer != null) return
    try {
        val created = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        muxer = created // Publish ownership before any later operation can throw.
        trackIndex = created.addTrack(format)
        created.start()
        LogContext.log.i(TAG, "MP4 track opened. dstFile=${outputFile.absolutePath}")
    } catch (error: Throwable) {
        fail("Could not open ${outputFile.absolutePath}", error)
    }
}

fun close() {
    val target = muxer
    muxer = null
    // 原有 dropped 日志不变。
    if (target == null) return
    var completed = false
    try {
        if (samplesWritten > 0) {
            target.stop()
            completed = true
            // 原成功日志不变。
        } else {
            // 原无样本日志不变。
        }
    } catch (error: Throwable) {
        LogContext.log.e(TAG, "MP4 stop failed; ${outputFile.name} is incomplete", error)
    } finally {
        try {
            target.release()
        } catch (releaseError: Exception) {
            LogContext.log.e(TAG, "MP4 release failed", releaseError)
        }
        if (!completed) discardOutputFile()
    }
}
```

- 复核要点：`onOutputFormat()` 与 `write()` 仍由所有者的 `codecCallbackLock` 串行化，提前发布字段不会让半初始化 muxer 被并发使用；`close()` 无样本、`stop()` 失败两种情况均删除文件，只有成功写完索引才保留。第四轮订正：Codex 原方案还要求清理后重抛 `Error` 并让 `fail()`/`discardOutputFile()` 只捕 `Exception`，**不采用**。`Mp4TrackWriter` 的 `catch (Throwable)` 与所有者的设计一致：`Screenshot2H26xStrategy.kt:263-270` 刻意阻止回调逃逸，:1205-1228 的 teardown 逐步吞错。`close()` 重抛会被 :1207 立即吞掉，无效；`onOutputFormat()` 重抛则经 `onOutputFormatChanged`（:322-330，无 try）逃逸到 MediaCodec 回调线程直接杀进程。`Error` 透传模式目前只见于 `lib-network`（`generic/ResultExt.kt:62`、`net/ResultExt.kt:54`），要在录屏侧引入须先改所有者，超出本条范围。
- 验证：为 `MediaMuxer` 增加可注入工厂/窄包装，分别模拟 `addTrack()`、`start()`、`stop()` 抛 `Exception`，断言 `release()` 恰好调用一次且输出文件被删除。再让这三步分别抛测试用 `Error`，断言仍完成同样清理且不从 `Mp4TrackWriter` 逃逸，以固化本条选择的 callback 隔离语义；若未来决定统一改为透传 `Error`，必须连同 `onOutputFormatChanged` 与 `releaseOwnedResources` 的所有者边界一起另案修改。

### 11.2 悬浮窗、Toast、网络监控

#### G1 [MEDIUM] Toast 共享 Handler 误删并发任务

- 位置：`lib-common-android/src/main/kotlin/com/leovp/android/exts/ToastExt.kt:37`（文件级 `mainHandler`）、:180（无 token 的 `post { showToast }`）、:256 与 :346（`removeCallbacksAndMessages(null)`）、:287（无 token 定时器）、:354（`post(cancelAction)`）
- 缺陷：`removeCallbacksAndMessages(null)` 清空该 Handler 的全部消息（框架文档明示，确定）。线程 A、B 几乎同时 `toast()`，主线程执行 A 的 `showToast` 到 :256 时 B 的 post 被删除，B 永不显示。:256 仅在 `API.ABOVE_R` 分支，:346 全版本生效，本次把 `cancelToast` 接入同一 Handler 扩大了影响面；当前代码 :256 还会吞掉 :354 刚投递的 `cancelAction`。
- 修复：用专属 token 只取消自己的定时任务。`lib-common-android/build.gradle.kts:20` 已 `api(libs.androidx.core.ktx)`；当前版本目录为 AndroidX Core 1.17.0，本地 `core-1.17.0-sources.jar` 已核对存在 `HandlerCompat.postDelayed(Handler, Runnable, Object, long)`，无需依赖记忆假设。

```kotlin
private val toastRequestToken = Any()
private val dismissToken = Any()

// 后台线程请求显示时，用独立 token 投递；0 ms 仍是异步主线程任务。
HandlerCompat.postDelayed(
    mainHandler,
    { showToast(/* 原参数 */) },
    toastRequestToken,
    0L
)

// showToast() 原 :256：只撤销上一个 toast 的自动隐藏，不删除其它显示请求。
mainHandler.removeCallbacksAndMessages(dismissToken)

// 原 :287 定时器。
HandlerCompat.postDelayed(
    mainHandler,
    { FloatView.with(FLOAT_VIEW_TAG).remove() },
    dismissToken,
    delayMs
)

// cancelToast() 的 cancelAction 内：显式取消尚未执行的请求及自动隐藏任务。
mainHandler.removeCallbacksAndMessages(toastRequestToken)
mainHandler.removeCallbacksAndMessages(dismissToken)
```

- 影响：旧 toast 的 dismiss 定时器仍按 token 被清掉，不会误删新 toast；`cancelToast()` 仍能取消其它线程已投递但尚未执行的 toast 请求。CHANGELOG「修复」一条。
- 验证：Robolectric PAUSED looper 下两个后台线程各 `toast()` 一次，`idle()` 后显示次数为 2。

#### G2 [MEDIUM] Activity finishing 时 `build()` 跳过但 `show()` 复用同 tag 旧实例

- 位置：`floatview/src/main/kotlin/com/leovp/floatview/framework/FloatViewManager.kt:33-36`（跳过并 `return`）、:52-53（取已存在实例）；`FloatViewCreator.kt:102-104`、:110-112（无条件 `show(config.tag)`）；`FloatViewImpl.kt:614-627`（`show()` 对已显示窗口先 `detachWindow()` 再 `addView`）
- 缺陷：Service 以默认 tag 显示悬浮窗，某 Activity 在 `finish()` 后的异步回调里 `FloatView.with(activity).layout(v).show()`：创建被跳过，`show(tag)` 却把 Service 的窗口 detach 再 addView（闪烁），并返回 `SHOWN`。
- 修复：`create()` 返回是否真的创建。`FloatViewOwner.isContextAlive`（:38）真实存在；:43 的 `error(...)` 与 `check` 同为 `IllegalStateException`，`tryShow` 的 `catch (Exception)` 仍能接住。不能改成「死 Context 时抛异常」，`CHANGELOG.md:341` 已承诺「记录警告并跳过创建，不抛异常」。

```kotlin
// FloatViewManager
fun create(context: Context, config: DefaultConfig): Boolean {
    checkFloatViewMainThread()
    if (!FloatViewOwner.isContextAlive(context)) {
        Log.w(TAG, "Skip float view tag[${config.tag}]: Activity is finishing or destroyed")
        return false
    }
    check(!windowMap.containsKey(config.tag)) {
        "Float view tag[${config.tag}] has already exist. Can't recreate it!"
    }
    val tag = config.tag
    windowMap[tag] = FloatViewImpl(context, config) { removed ->
        if (windowMap[tag] === removed) windowMap.remove(tag)
    }
    return true
}

// FloatViewCreator
/** @return `false` when creation was skipped for a finishing or destroyed Activity. */
fun build(): Boolean = FloatViewManager.create(context, config)

fun tryShow(): FloatViewShowResult = try {
    if (build()) {
        FloatViewManager.show(config.tag)
    } else {
        FloatViewShowResult.FAILED_AND_DETACHED
    }
} catch (_: Exception) {
    FloatViewManager.duplicateShowResult(config.tag) ?: FloatViewShowResult.FAILED_AND_DETACHED
}

private fun createAndShowFloatView() {
    if (build()) FloatViewManager.show(config.tag)
}
```

- 影响：`build(): Unit → Boolean` 对忽略返回值的调用方源码兼容；仓库内无读取返回值的调用方，demo 全用 `.show()`，无 Java 调用。`build()` KDoc 追加返回值说明，CHANGELOG「变更」补一句。
- 验证：`FloatViewLifecycleTest` 增加用例：先用 Application context 创建默认 tag 窗口，再对 `finish()` 后的 Activity 调 `tryShow()`，断言返回 `FAILED_AND_DETACHED` 且 `removeViewImmediate` 未被调用。

#### G3 [MEDIUM] 宿主 Activity 已销毁但 detach 失败时永不释放

- 位置：`FloatViewImpl.kt:749-767`（`detachWindow()` 非 `IllegalArgumentException` → `removalFailed = true; return false`）、:769-784（`finishRemoval()` :772-774 失败即 return，:776-783 的 `released = true`/`owner.release()`/`onRemoved(this)` 不执行）；`FloatViewOwner.kt:12`（强引用 Activity）、:18（注册到 Application）、:25-27（`onActivityDestroyed` 只调一次 `remove(true)`）；`FloatViewManager.kt:24`（静态 `windowMap`）
- 缺陷：失败后 `remove(true)` 虽可重入（:676 仅拦 `removing && !immediately`，:770 仅拦 `released`），但 Activity 销毁后无人再调用，`remove()` 非立即路径 no-op，`show()` 在 :600 直接返回，实际等同永久泄漏。「保留以便重试」对调用方主动 `remove(true)` 合理，对 `onActivityDestroyed` 路径没有第二次机会。
- 修复：宿主已死时先做**有界重试**，再强制解除库自身的引用。仅一次失败就立即从 map 删除并不能证明窗口物理上已摘除；尤其 `systemWindow=true` 时，不能依赖 Activity token 的 `closeAll` 一定代为清理。为避免给已接近 `TooManyFunctions` 阈值的 `FloatViewImpl` 再加成员函数，增加两个字段并复用现有 `finishRemoval()`：

```kotlin
private var destroyedOwnerDetachRetries = 0
private val destroyedOwnerDetachRetry = Runnable { finishRemoval() }

private fun finishRemoval() {
    if (released) return
    cancelAnimations()
    if (!detachWindow()) {
        hideAfterFailure()
        if (owner.isAlive) return
        if (destroyedOwnerDetachRetries++ < MAX_DESTROYED_OWNER_DETACH_RETRIES) {
            mainHandler.postDelayed(destroyedOwnerDetachRetry, DESTROYED_OWNER_RETRY_DELAY_MS)
            return
        }
        Log.e(TAG, "Owner destroyed; detach retries exhausted for tag=${config.tag}")
        // Last resort: release the library's static map/lifecycle callback. This does not assert
        // that WindowManager physically detached the failed window.
        windowAdded = false
        removalFailed = false
    }
    released = true
    // 原 :777-783 不变
}

// companion object
private const val MAX_DESTROYED_OWNER_DETACH_RETRIES = 3
private const val DESTROYED_OWNER_RETRY_DELAY_MS = 100L
```

- 第五轮推翻第四轮的 token 补充：当前 `remove()` :676 在 `removing && !immediately` 时直接返回，不会调用 `unregisterListeners()`；再次 `remove(true)` 虽会在 :679 清掉旧重试，但紧接着在 :682 重新执行 `finishRemoval()`，失败时会重新投递并继续累计有界次数。`show()` 也会在 :600 因 `removing` 提前返回。因此不存在“只清掉重试而不补投”的路径，不需要 retry token；测试应增加“等待重试期间再次 `remove(false)`/`remove(true)`”两例来锁定这个结论。
- 安全性与边界：重试窗口最多约 300 ms，之后 `owner.release()` 与 `onRemoved(this)` 必须执行，避免 `windowMap` 和 lifecycle callback 永久持有 Activity。若 WindowManager 在全部重试中持续抛非 `IllegalArgumentException`，只能保证**库引用释放**，不能宣称实际窗口已摘除；错误日志必须保留这一差异。内部兜底，无需 KDoc/CHANGELOG。
- 验证：`FloatViewLifecycleTest` 增加两例：第一次失败、下一次成功时最终 `exist()==false`；持续抛 `IllegalStateException` 时推进主 Looper 超过重试窗口，断言调用次数有界、`exist()==false`，且 `registeredActivityLifecycleCallbacks` 为空。真机再覆盖 Activity 销毁 + 普通应用窗口 / `systemWindow` 两种类型，确认没有残留可见窗口。

#### G4 [LOW] Wi-Fi callback 注册/注销失败不能静默

- 位置：`lib-common-android/.../utils/NetworkUtil.kt:299-313`；`androidbase/.../NetworkMonitor.kt:189`
- 第三轮纠错：不能在注销失败后把 caller callback 永久留在静态 `IdentityHashMap` 里“等待重试”。platform callback 强持有 lambda，而 lambda 可经 `NetworkMonitor.callback` 回到 Activity；一次失败就会形成进程级泄漏。正确做法是把 framework wrapper 做成可脱钩对象，先移除映射并清空业务 listener，再调用平台注销。即使平台仍持有失败的 wrapper，它也只剩 application context 和空 listener。

```kotlin
@RequiresApi(Build.VERSION_CODES.S)
private class WifiSignalNetworkCallback(
    context: Context,
    callback: (WifiSignal) -> Unit,
) : ConnectivityManager.NetworkCallback() {
    private val appContext = context.applicationContext ?: context
    @Volatile private var listener: ((WifiSignal) -> Unit)? = callback

    fun detachListener() {
        listener = null
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        val wifiInfo = capabilities.transportInfo as? WifiInfo ?: return
        if (isWifiActive(appContext)) listener?.invoke(generateWifiSignal(wifiInfo))
    }

    override fun onLost(network: Network) {
        listener?.invoke(WifiSignal(-1, -1, -1, -1))
    }
}
```

- 第四轮补充：该类脱离了 `SDK_INT >= S` 分支却使用 `NetworkCapabilities.transportInfo`（API 29），必须标 `@RequiresApi(Build.VERSION_CODES.S)`，并同步新增 `import androidx.annotation.RequiresApi`，否则 lint `NewApi` 会让 `staticCheck` 失败。
- `wifiNetworkCallbacks` 的 value 改为 `WifiSignalNetworkCallback`。替换/注销时先从 map `remove(callback)` 并 `detachListener()`，再调用 `unregisterNetworkCallback`；平台异常记录后返回 `false`，但不把映射塞回去。注册异常也捕获 `Exception`，先对尚未成功注册的 wrapper 调用 `detachListener()`，再记录并返回 `false`。SDK < 31 时两个方法都是无需工作的幂等成功，返回 `true`。保持现有 `wifiNetworkCallbackLock` 覆盖“移除旧项—注销旧 wrapper—注册并写入新项”的完整替换事务，避免两个线程对同一 callback 同时注册出两个 framework wrapper；不要为了缩短锁区间而留下这个竞态。
- `getWifiNetworkStatsAboveAndroidS()` 与 `unregisterWifiNetworkStatsAboveAndroidS()` 均改为 `Boolean`；忽略返回值的 Kotlin/Java 源码调用仍可编译，但公开签名和失败语义改变，KDoc 与 CHANGELOG 追加迁移说明。`NetworkStatsCallbackTest.kt` 增加：SDK < 31 返回 true；注册异常返回 false 且无映射；注销失败返回 false、映射已移除、迟到 platform 事件不再调用业务 listener；重复注册替换失败时旧 wrapper 已脱钩且新注册未进行。

#### G5 [MEDIUM，原 LOW，经复核上调] `NetworkMonitor` 把 Activity 捕获进两处静态持有者

- 位置：`androidbase/.../framework/BaseActivity.kt:265`（传 `this@BaseActivity`）；`NetworkUtil.kt:36` 为 `object`，:57-58 静态 `IdentityHashMap`，:291 匿名回调捕获 `ctx`；**复核新增**：`TrafficStatHelper.kt:28` `class TrafficStatHelper private constructor(val ctx: Context)` + :105 `SingletonHolder` 单例，`NetworkMonitor.kt:185` `getInstance(ctx)` 首次传入的 Activity 被进程级单例永久持有。
- 缺陷：第二处与注销是否失败无关，首个创建 `NetworkMonitor` 的 Activity 必然泄漏到进程结束。只在 `getWifiNetworkStatsAboveAndroidS` 内取 `applicationContext` 覆盖不到它；只在 `NetworkMonitor` 构造处转换又覆盖不到库使用者直接调用公开的 `TrafficStatHelper.getInstance(activity)`。
- 修复：两层都做防御。`NetworkMonitor` 立即把传入 context 规范化为 application context，供 ping 和 `NetworkUtil` 使用；`TrafficStatHelper` 构造器也只保存 `context.applicationContext ?: context`，从公开单例入口根治首次参数为 Activity 的泄漏。`BaseActivity.kt:265` 可保持不变。G4 的 wrapper 另行清空业务 listener，解决注销失败时 platform callback 经 `NetworkMonitor.callback` 间接持有 Activity 的链路。

```kotlin
class NetworkMonitor internal constructor(
    ctx: Context,
    private val ip: String,
    private val callback: (NetworkMonitorResult) -> Unit,
    private val schedulerFactory: () -> NetworkMonitorScheduler,
    private val beforeResultDispatch: () -> Unit = {},
) {
    /** Never retain an Activity in monitor-owned work. */
    private val ctx: Context = ctx.applicationContext ?: ctx
    // rest unchanged
}

class TrafficStatHelper private constructor(context: Context) {
    val ctx: Context = context.applicationContext ?: context
    // rest unchanged
    companion object : SingletonHolder<TrafficStatHelper, Context>(::TrafficStatHelper)
}
```

- 验证：由于 `TrafficStatHelper` 是进程单例，测试需在独立 JVM/fork 或增加 test-only reset，确保第一次用 Activity 初始化；断言其 `ctx === app`。另用 Activity 构造 `NetworkMonitor`，通过 internal 测试 accessor 或传入的 `NetworkUtil` mock 断言实际使用 application context，不能用已经被其他测试初始化的单例得到空通过。
- 影响：`TrafficStatHelper.ctx` 是公开只读属性，修复后从“首次传入对象”变为其 application context。给该属性补 KDoc，并在 CHANGELOG「修复」说明不再保留 Activity；依赖对象身份比较的调用方应改为比较 `applicationContext`。

#### G6 [LOW] `showToast` 在 try 外访问 `lateinit config`，单例还可能持有 Activity

- 位置：`ToastExt.kt:225`（`try` 在 :235），`LeoToast.config` 为 `lateinit`（:57）
- 缺陷：未调用 `LeoToast.init()` 时抛 `UninitializedPropertyAccessException`；经 :180 post 的路径直接崩主线程，本次新增的系统 toast 兜底（:241/:245/:284/:334）都覆盖不到。`showToast` 现有 4 个 return，加 1 个为 5，低于 ReturnCount 7。
- 修复：不能只在现有 `try` 外增加初始化判断后直接调用系统 Toast，否则系统 Toast 自己抛出的异常仍会穿透。先计算与配置无关的 message/兜底 duration，再把 `config` 访问、未初始化回退和原 `when` 全部包入现有异常边界；`isInitialized` 保持 `internal`，不扩大公开 API。未初始化且 `debug=true` 时应记录 warning 后直接返回，不能把原本受 release 配置门控的调试信息意外展示给终端用户；非 debug 才走系统 Toast 兜底。另把 `LeoToast` 构造参数立即规范化为 `context.applicationContext ?: context`：它也是进程单例，首次未初始化调用若传 Activity，不能为了执行兜底而把 Activity 永久存进 SingletonHolder 实例。

```kotlin
// LeoToast: normalize before the singleton stores the first caller.
class LeoToast private constructor(context: Context) {
    private val ctx: Context = context.applicationContext ?: context
    internal val isInitialized: Boolean get() = ::config.isInitialized
    // ...
}

// showToast()
val message = if (debug) "DEBUG: $msg" else (msg ?: "null")
var duration = if (longDuration == true) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
try {
    val leoToast = LeoToast.getInstance(ctx)
    if (!leoToast.isInitialized) {
        if (debug) {
            Log.w("ToastExt", "Ignore debug toast before LeoToast.init().")
            return
        }
        showSystemToast(ctx, message, duration)
        return
    }
    val toastCfg = leoToast.config
    val isLongDuration = longDuration ?: (toastCfg.duration == Toast.LENGTH_LONG)
    duration = if (isLongDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    // 原 debug 判断和 when 分支移到这里，逻辑不变。
} catch (error: Exception) {
    Log.e("ToastExt", "show toast failed", error)
    try {
        showSystemToast(ctx, message, duration)
    } catch (fallbackError: Exception) {
        Log.e("ToastExt", "system toast fallback failed", fallbackError)
    }
}
```

#### G7 [LOW] 六处行为变更缺 KDoc / CHANGELOG

经 `git diff` 与 `grep CHANGELOG.md` 核实，以下关键词在 CHANGELOG 中零命中（`CHANGELOG.md:338-345` 只写了释放语义）：

| 位置 | 变更 | 需补 |
|---|---|---|
| `FloatViewImpl.kt:503-506` | API 21-25 窗口类型由 `TYPE_TOAST or TYPE_PHONE`（2005 \| 2002 = 2007 = `TYPE_PRIORITY_PHONE`，三方应用必 `BadTokenException`）改为 `TYPE_PHONE`，z-order 下降 | CHANGELOG「修复」 |
| `ForegroundComponent.kt:69` | 监听回调线程由 `HandlerThread("fg")` 改为主线程，下游阻塞工作会卡主线程 | `AppStateListener` KDoc「Callbacks run on the main thread」+ CHANGELOG「变更」 |
| `NetworkMonitor` / `NetworkUtil` | 不再要求 `CHANGE_NETWORK_STATE` | CHANGELOG :125-127 补半句 |
| `LeoTextureView.kt:42` | `isH265` public → private | `LeoTextureView` 类 KDoc 说明不再直接赋值，调用 `initDecoder(vps, sps, pps)`，实现按 `vps != null` 自动选择 HEVC/AVC；CHANGELOG「变更」 |
| `ViewExt.kt:179` | 去抖改用 `elapsedRealtime` | CHANGELOG「修复」 |
| `ToastExt.kt:344-356` | `cancelToast` 改为主线程投递 | CHANGELOG「修复」 |

#### G8 [LOW] 两个并发测试可能空通过

- 位置：`androidbase/src/test/java/com/leovp/androidbase/utils/network/NetworkMonitorLifecycleTest.kt:134-136`、:262-264
- 修复：`iteration.join(3_000)` 后加 `assertFalse(iteration.isAlive)`（`assertFalse` 已在 :15 导入）。

#### G9 [MEDIUM，Codex 复核新增] `NetworkMonitor` 的旧 Wi-Fi 回调可污染新会话

- 位置：`androidbase/.../NetworkMonitor.kt:56-58`、`:177-192`、`:203-218`；`lib-common-android/.../NetworkUtil.kt:282-314`
- 缺陷：轮询任务用 `generation + scheduler` 隔离 stop/restart，但 Wi-Fi 监听始终复用同一个 `wifiSignalCallback`，回调本身没有会话标识。真实窗口有两种（第四轮按 AOSP `ConnectivityManager.CallbackHandler` 收窄，推断）：注销瞬间**正在执行中**的旧 `onCapabilitiesChanged()`/`onLost()`，以及注销失败后仍存活的旧 wrapper；二者都会在新会话注册后写入 `globalWifiSignal`。已成功注销的 request，其排队消息会因查不到 callback 而被框架丢弃，不构成窗口。现有测试只验证 restart 时立即清空旧信号，没有模拟旧框架 callback wrapper 的迟到事件。
- 修复：每次 start 创建捕获 `generation` 与 `scheduler` 的独立回调；回调在 `monitorLifecycleLock` 内完成“仍是当前会话”检查和赋值，避免 check 与 stop 清空之间的竞态。配合 G4，注销时先从 `NetworkMonitor` 字段 detach，再让 `NetworkUtil` 清空 wrapper listener；**不**保留失败 callback 集合，避免把 Activity 回调变成新的泄漏根。

```kotlin
// Guarded by monitorLifecycleLock. At most one callback belongs to the current session.
private var wifiSignalCallback: ((NetworkUtil.WifiSignal) -> Unit)? = null

private fun registerWifiSignalCallback(
    generation: Long,
    scheduler: NetworkMonitorScheduler,
) {
    val sessionCallback: (NetworkUtil.WifiSignal) -> Unit = { signal ->
        synchronized(monitorLifecycleLock) {
            if (isCurrentMonitor(generation, scheduler)) globalWifiSignal = signal
        }
    }
    if (NetworkUtil.getWifiNetworkStatsAboveAndroidS(ctx, sessionCallback)) {
        wifiSignalCallback = sessionCallback
    } else {
        LogContext.log.w(TAG, "Wi-Fi signal callback registration failed")
    }
}

private fun unregisterWifiSignalCallback() {
    val callback = wifiSignalCallback ?: return
    wifiSignalCallback = null // detach session ownership before platform teardown
    if (!NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(ctx, callback)) {
        LogContext.log.w(TAG, "Wi-Fi signal callback unregistration failed")
    }
}
```

- 调用顺序：`startMonitor()` 进入锁后先推进 generation、置 stopped、注销并清空旧 session callback，再创建并发布 scheduler；随后置 `monitorStopped=false`、注册新 callback、post 首次任务。这样即使注册 API 同步回调，当前 session 也已有效；若首次 post 抛异常，现有 `finally -> stopMonitor()` 仍会推进 generation 并注销刚注册的 callback。`stopMonitor()` 先推进 generation，再 detach/注销 callback，最后释放 scheduler 并清空信号。注册失败时仍可继续 ping/流量监控。
- 验证：在 `NetworkMonitorLifecycleTest` 捕获每次传给 mock `NetworkUtil` 的**session lambda**；restart 后先让新 lambda 上报新信号，再触发旧 lambda 的 `onLost()`，断言结果仍使用新信号。framework wrapper 的脱钩行为属于 `NetworkStatsCallbackTest`：模拟注销抛异常后直接调用旧 wrapper，业务 callback 不得再执行。不要把两层对象混称为同一个 wrapper。

#### G11 [MEDIUM，第六轮新增，2026-10-22 完整实施] 流量监控跨线程发布缺可见性保证，且与 `onDestroy` 存在检查后动作竞态

- 实施状态：`b0c417d95`/`f6cbb985e` 先解决了可见性与永久泄漏，但仍按「CAS 发布 → `startMonitor()` → generation 复查」执行，因而不具备严格的停止栅栏。`fec53929a` 增加了基础握手测试，但没有覆盖「已发布、正在启动」时 stop 返回的交错。第十轮新增 `trafficMonitorLock`，将最终 generation/存活检查、发布、`startMonitor()` 及失败回滚与 `stopTrafficMonitor()` 的作废、取走和停止串行化。若 start 先获锁，stop 等待启动结束并在返回前停止；若 stop 先获锁，迟到 DNS 回调因 generation 失效而不再启动。
- 缺陷 1（可见性）：`InternetUtil.getIpsByHost(host, callback)` 的实现是 `thread { callback(getIpsByHost(host)) }`，因此 `networkMonitor` 在 DNS 后台线程写入、在主线程的 `onDestroy` 读取，而该字段是普通 `var`，没有 `@Volatile`。外层的 `AtomicReference` 只保证所存引用的原子性，不为字段本身建立 happens-before 边。主线程可能读到 `null`，于是 `stopTrafficMonitor()` 漏掉一个已经创建好的监控。
- 缺陷 2（检查后动作）：DNS 线程在 :258 通过 `isDestroyed || isFinishing` 检查之后、在 :262 赋值之前，主线程可以跑完整个 `onDestroy`（:157 的 `stopTrafficMonitor()` 此时把字段置 `null`，什么也没停）。随后 DNS 线程把新建的监控写入字段并在 :300 启动它，监控与其 `HandlerThread` 从此无人释放。:256-257 的注释说明作者已意识到顺序问题，但只补了前半截。
- 后果（实施前）：泄漏的是 `HandlerThread`、NetworkMonitor 持有的回调与 `NetworkUtil` 的静态回调表。G5 已使 `NetworkMonitor` 与 `TrafficStatHelper` 都只保留 application context，因此不应再声称 `TrafficStatHelper` 单例长期持有 Activity。demo `MainActivity.kt:222`（`onResume` 启动）与 :278（`onPause` 停止）的用法会频繁进入这个窗口。
- 最终修复：不再依赖启动后补偿。增加一把 `trafficMonitorLock`，让 DNS 线程的「最终 generation/存活检查 → 发布 → `startMonitor()` → 失败回滚」与 `stopTrafficMonitor()` 的「generation 作废 → 取走 → `stopMonitor()`」在同一锁下串行化。

<details>
<summary>第六轮原方案（已实施，但第九轮确认启动后补偿不足以提供 stop 栅栏）</summary>

```kotlin
// 字段：引用持有者本身改为 val，获得 CAS 与可见性；generation 用于作废在途的 start
private val networkMonitor = AtomicReference<NetworkMonitor?>(null)
private val trafficMonitorGeneration = AtomicLong(0)

fun stopTrafficMonitor() {
    // Invalidate any DNS lookup still in flight before tearing the current monitor down.
    trafficMonitorGeneration.incrementAndGet()
    networkMonitor.getAndSet(null)?.stopMonitor()
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun startTrafficNetwork(
    domain: String,
    callback: ((NetworkMonitor.NetworkMonitorResult) -> Unit)? = null
) {
    if (networkMonitor.get() != null) {
        LogContext.log.w(tag, "networkMonitor had already existed! Do NOT create it anymore!")
        return
    }
    val generation = trafficMonitorGeneration.get()
    LogContext.log.i(tag, "Monitor domain=$domain")
    InternetUtil.getIpsByHost(domain) { socketIps ->
        // This callback runs on a plain background thread started by InternetUtil.
        if (socketIps.isEmpty()) { /* 原有日志与 toast 不变 */ return@getIpsByHost }
        if (isDestroyed || isFinishing) { /* 原有日志不变 */ return@getIpsByHost }
        val monitor = NetworkMonitor(this@BaseActivity, socketIps[0]) { info ->
            // 原有回调体不变
        }
        // Publish, then re-check: stopTrafficMonitor() may have run on the main thread
        // while the lookup was in flight, and it would have found an empty slot.
        val published = trafficMonitorGeneration.get() == generation &&
            networkMonitor.compareAndSet(null, monitor)
        if (!published) {
            monitor.stopMonitor()
            return@getIpsByHost
        }
        monitor.startMonitor(defaultConfig.trafficConfig.frequencyInSecond)
        if (trafficMonitorGeneration.get() != generation) {
            // A stop landed between the publish and the start.
            networkMonitor.compareAndSet(monitor, null)
            monitor.stopMonitor()
        }
    }
}
```

</details>

剩余修复的关键临界区应改为（原回调体省略）：

```kotlin
private val trafficMonitorLock = Any()

fun stopTrafficMonitor() = synchronized(trafficMonitorLock) {
    trafficMonitorGeneration.incrementAndGet()
    networkMonitor.getAndSet(null)?.stopMonitor()
}

// DNS callback: construction may stay outside the lock; publication/start must be serialised.
synchronized(trafficMonitorLock) {
    if (trafficMonitorGeneration.get() != generation ||
        networkMonitor.get() != null ||
        isDestroyed || isFinishing
    ) {
        monitor.stopMonitor()
        return@getIpsByHost
    }
    networkMonitor.set(monitor)
    try {
        monitor.startMonitor(defaultConfig.trafficConfig.frequencyInSecond)
    } catch (error: Exception) {
        networkMonitor.compareAndSet(monitor, null)
        monitor.stopMonitor()
        LogContext.log.e(tag, "Failed to start networkMonitor.", error)
    }
}
```

- 配套：新增 `import java.util.concurrent.atomic.AtomicLong`；:238、:252 的重复存在性检查从 `networkMonitor?.get()` 改为 `networkMonitor.get()`（字段由可空 `var` 变为非空 `val`）。`onStop` 原先那处读取已随 G12 删除，无需再改。`networkMonitor` 为私有字段，公开签名不变，无需 KDoc 迁移说明；CHANGELOG 记「修复」一条。
- 复核要点：`NetworkMonitor.stopMonitor()` 对从未 `startMonitor()` 的实例是安全的（置 `monitorStopped`、推进 generation、注销 Wi-Fi 回调、释放 scheduler 均为空安全），因此补偿路径上的 `stopMonitor()` 可以直接调用。`stopTrafficMonitor()` 仍可重复调用并允许后续重新 `startTrafficNetwork()`，demo 的 `onPause`/`onResume` 用法不受影响。
- 验证：`BaseActivityTrafficMonitorTest` 在第十轮有 6 个用例。停止栅栏用例将 mock
  `startMonitor()` 阻塞在「已发布、启动未返回」位置，再从另一线程调用
  `stopTrafficMonitor()`，验证 stopper 会阻塞于 `trafficMonitorLock`，释放启动后才完成停止。
  该用例不是旧缺陷的确定性复现，原因见 §17.2；不能再把 stub 下的旧码失败写成生产交错证据。

#### G12 [LOW，第六轮新增，2026-10-22 已实施] `onStop` 的泄漏警告与设计意图相矛盾

- 位置（实施前）：`androidbase/.../framework/BaseActivity.kt:149-155`
- **实施状态**：`eaaa49e62` 已删除该判断块，`onStop()` 只保留框架日志与
  `super.onStop()`，与下述方案一致。该提交一度给注销路径增加 `ACCESS_NETWORK_STATE`
  权限契约，后经复核确认平台注销 API 不要求该权限，已由 `ba6ea5ed7` 撤销
  `stopTrafficMonitor()`/`stopMonitor()` 等注销入口的 `@RequiresPermission`，并删除
  `onDestroy()` 的 `@SuppressLint("MissingPermission")`。`eefaae161` 已为
  `startTrafficNetwork()` 与 `stopTrafficMonitor()` 补齐所有权、线程、阻塞与迁移 KDoc，防止后来者
  把泄漏警告加回去。
- 缺陷：`onStop()` 在 `networkMonitor != null` 时打印「Are you leaking networkMonitor? Don't forget to call stopTrafficMonitor()」。但 `onStop` 不是生命周期终点，按 home 键、锁屏、跳转到其他页面都会触发，随后经 `onRestart`/`onStart` 还会回来；而 `onDestroy`（实施后为 :157）已经无条件调用 `stopTrafficMonitor()` 兜底。于是这条警告既与基类自己的释放逻辑矛盾，也与「监控在 Activity 不可见期间继续轮询」这一期望行为（见 §13）矛盾：恰恰在按预期工作时反复误报。
- 修复：删除 :149-155 整个判断块，`onStop()` 只保留原有的框架日志与
  `super.onStop()`（**已实施**）。同时把所有权契约写进 KDoc，避免后来者再加回类似警告
  （**已实施**）。

```kotlin
override fun onStop() {
    LogContext.log.w(tag, "=====> onStop <=====", outputType = LogOutType.FRAMEWORK)
    super.onStop()
}

/**
 * Starts the traffic and latency monitor for [domain].
 *
 * Ownership: this Activity owns the monitor and releases it in [onDestroy]; subclasses do
 * not have to stop it. Monitoring deliberately continues while the Activity is invisible,
 * so being stopped is not a leak. Call [stopTrafficMonitor] only to end it earlier.
 */
```

- 影响：仅删除一条日志，无 API 变化。CHANGELOG 可并入 G11 的「修复」条目。
- 验证：无需行为测试；删除部分随 §14.1 的 `:androidbase:testDebugUnitTest` 一并编译通过，KDoc
  补充随第十一轮的 androidbase 编译与质量检查复核，见 §19。

#### G13 [MEDIUM，第六轮新增，2026-10-21 已实施] 回调在持锁状态下派发，KDoc 的死锁警告描述的是真实可达路径

- 实施状态：`b0c417d95` 已把结果计算留在 `monitorLifecycleLock` 内、把使用方回调移到锁外，并用一把不与生命周期锁嵌套的 `resultDispatchLock` 保证同一实例的回调不并发。`f6cbb985e` 进一步修正 KDoc，在 `BaseActivity` 主线程投递处过滤 Activity 销毁后的尾随结果，并新增「回调阻塞时 stop 仍可返回」、「stop 后最多一个尾随结果」、「跨 session 回调不重叠」三个用例。本机已对远程最终树重跑 `:androidbase:testDebugUnitTest`/`ktlintCheck`/`detekt --rerun-tasks`，162 个任务实际执行并全部通过。
- 缺陷：`startMonitor` 的 KDoc 写「Do not wait inside a callback for another thread that calls [startMonitor] or [stopMonitor]」，这不是保守措辞，而是真实可达的两方死锁：监控线程在 :127 持锁、于 :137 同步执行使用方回调；主线程此时进入 `stopMonitor()`（:214）等待同一把锁。若回调内对主线程做同步等待（`runBlocking(Dispatchers.Main)`、`Handler(mainLooper).post {}` 配 `latch.await()`、或任何等待主线程完成的 future），监控线程等主线程、主线程等锁，直接 ANR。`stopMonitor` KDoc :204-205 承诺的「An already running callback finishes before this method returns」正是这条死锁边的来源。
- 触发：使用方写出 `startTrafficNetwork("host") { info -> runBlocking(Dispatchers.Main) { render(info) } }`，随后退出页面触发 `onDestroy → stopTrafficMonitor() → stopMonitor()`。本库自身的回调（`BaseActivity.kt:264` 的 `runOnUiThread { it(info) }`）是异步投递、不等待，因此不会触发；风险全在使用方，且仅靠文档约束。
- 决定（2026-10-20，作者确认）：不止于文档约束，后续要改成结构上无法死锁，以防使用不当。
- 修复：在锁内只计算结果，出锁后再派发回调。`isCurrentMonitor()`（:160-163）只读 `monitorStopped`、`monitorGeneration`、`monitorScheduler` 三个 `@Volatile` 字段（:54、:57、:60），可以在锁外安全调用；`trafficStat.getSpeed()`（`TrafficStatHelper.kt:56`）只读计数器，留在锁内无妨。

```kotlin
// 修改前 :126-150
beforeResultDispatch()
synchronized(monitorLifecycleLock) {
    if (!isCurrentMonitor(generation, scheduler)) return
    var downloadSpeed: Long = 0
    var uploadSpeed: Long = 0
    if (ping > -1) { /* trafficStat.getSpeed() ... */ }
    callback.invoke(NetworkMonitorResult(/* ... */))
}

// 修改后
beforeResultDispatch()
val result = synchronized(monitorLifecycleLock) {
    if (!isCurrentMonitor(generation, scheduler)) return
    var downloadSpeed: Long = 0
    var uploadSpeed: Long = 0
    if (ping > -1) { // Network is online.
        trafficStat.getSpeed().let { (download, upload) ->
            downloadSpeed = download / interval
            uploadSpeed = upload / interval
        }
    }
    NetworkMonitorResult(
        downloadSpeed,
        uploadSpeed,
        ping,
        showPingLatencyStatus,
        wifiSignal?.linkSpeed ?: -1,
        wifiSignal?.rssi ?: -1,
        wifiSignal?.scoreIn5 ?: -1,
        wifiSignal?.score ?: -1,
        showWifiSignalStatus
    )
}
// Dispatched outside the lock on purpose: a consumer callback that blocks on another thread
// must not be able to stall startMonitor()/stopMonitor(). The re-check narrows, but cannot
// eliminate, a trailing result racing with a stop.
if (isCurrentMonitor(generation, scheduler)) callback.invoke(result)
```

- 代价与必须同步修改的 KDoc：出锁派发后，`stopMonitor()` 返回时可能仍有一个回调在执行，:204-205 的承诺不再成立。两处 KDoc 都要改写，把约束从「使用方不得阻塞」换成「可能收到一个尾随回调」：
  - `startMonitor`（:168-170）：删除「Do not wait inside a callback…」一句，改为「Callbacks run on the monitor thread and never hold the lifecycle lock, so blocking inside one cannot stall [startMonitor] or [stopMonitor]; it only delays subsequent results.」
  - `stopMonitor`（:204-205）：把「An already running callback finishes before this method returns」改为「A callback already in flight may still complete after this method returns; consumers must tolerate one trailing result and guard their own teardown state.」
- 影响：公开签名不变，但可观察行为变更（尾随回调），按「API 演进约定」在上述两处 KDoc 写明迁移说明，CHANGELOG 记「变更」一条。`BaseActivity` 原本虽异步投递回调，但尾随投递仍可在 `onDestroy()` 后执行；`f6cbb985e` 已在主线程执行前复查 `isDestroyed || isFinishing`。
- 验证：已新增三个并发回归测试，覆盖「回调阻塞时 stop 返回」、「stop 后最多一个尾随结果」和「跨 session 回调不重叠」；本机重跑均通过。真机可用 demo 写一个阻塞回调验证不再 ANR，本轮未执行真机验证。

### 11.3 网络、Compose、OpenGL 与重命名

#### N1 [MEDIUM] `AbsPref` 序列化失败保留旧值：补 KDoc 迁移说明与 CHANGELOG

- 位置：`pref/src/main/kotlin/com/leovp/pref/base/AbsPref.kt:36-37`、:68-69；KDoc :15-23、:44-55
- 事实：基线 `else -> put(key, v.toJsonString())`，`lib-json` 失败返回 `""`，于是写入 `""`，`getObject` 得 `null`、`getString` 得 `""`；现跳过写入，保留旧值。分支新增的 `AbsPrefTest.kt:8-14`（`failed object serialization does not overwrite stored value`）表明保留旧值是有意为之。
- 决定（2026-10-20，用户确认）：方案 A，保留现语义，不改代码与测试，只补文档。不采用 `Unit → Boolean` 返回值变更。
- 修复：`put` 与 `put4Java` 的 KDoc 各追加一段，CHANGELOG「变更」一条。

```kotlin
/**
 * ...
 * - Object except Set
 *
 * Migration: object values are serialized via [toJsonString]. On serialization failure
 * (logged by lib-json) the key is left untouched. Previously an empty string was written,
 * so `getString(key, null)` returned `""` and `getObject(key)` returned `null`; now the
 * previously stored value survives. Callers that relied on the "cleared on failure"
 * behaviour should call `put(key, null as String?)` themselves before storing.
 */
inline fun <reified T : Any> put(key: String, v: T) { /* body unchanged */ }
```

CHANGELOG `### 变更 (Changed)` 新增：「**`pref` 对象序列化失败不再清空键**：`AbsPref.put`/`put4Java` 序列化失败时保留旧值（此前写入空串，`getObject` 读到 `null`）。依赖「失败即清空」语义的调用方需自行 `put(key, null as String?)`。」

- 验证：无代码改动；`:pref:testDebugUnitTest` 维持现状。

#### N2 [MEDIUM] `DeviceUtil` → `DeviceUtilRestricted` 重命名缺迁移记录

- 位置：`android-restricted/src/main/kotlin/com/leovp/android/restricted/utils/DeviceUtilRestricted.kt:16-20`（`git diff -M` 相似度 88%）；`CLAUDE.md:76` 仍写 `DeviceUtil`
- 缺陷：与 `lib-common-android/.../DeviceUtil.kt:48` 解除同名冲突动机合理，但已发布公开类名变更无 KDoc 迁移说明、CHANGELOG 零命中。
- 修复：

```kotlin
/**
 * Battery capacity / serial helpers that need restricted permissions.
 *
 * Migration: renamed from `DeviceUtil` (5.15.x) to avoid clashing with
 * `com.leovp.android.utils.DeviceUtil`; replace the import and
 * `DeviceUtil.getInstance(ctx)` with `DeviceUtilRestricted.getInstance(ctx)`.
 */
class DeviceUtilRestricted private constructor(context: Context) {
```

CHANGELOG「变更」加粗条目「**`android-restricted` `DeviceUtil` 重命名为 `DeviceUtilRestricted`**」。`CLAUDE.md:76` 也有一处陈旧名称，但该文件默认只读；只有用户明确授权修改 Claude 配置时才同步，否则在交付中单独列为待更新文档。

#### N3 [MEDIUM] Net `ConvertException` 分支二次读取已消费的 body（预存）

- 位置：`lib-network/src/main/kotlin/com/leovp/network/http/net/ResultExt.kt:112-114`；来源 `net/converters/SerializationConverter.kt:46`（`in 200..299`）、:59（消费 body）、:66-70（body 进 `message`）、:75-91（非 2xx 不读 body）
- 缺陷：2xx 解析失败时 body 已被转换器 `string()` 消费，再读抛 `IllegalStateException("closed")`（若上游拦截器换成 `Buffer` 支撑的 body 则得 `""`），被 `payloadOperationOrNull` 吞掉后 `responseBodyString` 恒为 `null`，且每次 JSON 解析失败都多一条「Error while getting body string.」错误日志。基线已有同样的 `runCatching` 二次读，本轮迁入 `payloadOperationOrNull` 时未消除。generic 路径（`generic/ResultExt.kt:90,98`）用 `err.responseBodyString`，无此问题。
- 修复：

```kotlin
is ConvertException -> {
    // SerializationConverter consumes the body only on 2xx and puts it into message.
    val bodyString = if (err.response.code in 200..299) {
        err.message
    } else {
        payloadOperationOrNull("Error while getting body string.") { err.response.body.string() }
    }
    Result.Failure(
        ResultConvertException(
            statusCode = err.response.code,
            message = err.cause?.message ?: err.message,
            cause = err,
            responseBodyString = bodyString,
            tag = err.tag
        )
    )
}
```

- 影响：`ResultConvertException.responseBodyString` 从恒 `null` 变为实际 body；在该字段 KDoc 加一句，CHANGELOG「修复」。`err.tag` 已被当前代码直接引用并由 §14 的 `staticCheck` 编译通过，不再是待确认项。
- 验证：构造 `Response(code=200)` + 非法 JSON 触发 `ConvertException`，断言 `responseBodyString == rawBody` 且无「Error while getting body string.」日志。

#### N4 [MEDIUM] `lib-network` 日志拦截器仍整体缓冲请求与响应体（预存）

- 位置：`lib-network/src/main/kotlin/com/leovp/network/interceptors/HttpLoggingInterceptor.kt:172-180`（请求）、:235-250（响应）；:176、:242 两处 `!!`
- 缺陷：请求侧 `requestBody.writeTo(Buffer())` 会完整生成并缓冲 body，响应侧 `source.request(Long.MAX_VALUE)` 会完整读入 body 后再 clone 为字符串；任一方向的大 body 都可造成高峰内存或 OOM。`http` 模块同名拦截器已在本分支实现 256 KiB 双向上界（`http/.../okhttp/HttpLoggingInterceptor.kt:235-340`），而 CHANGELOG HTTP-3（:42-46）直接声称“请求与响应体日志各自最多 256 KiB”，对当前 `lib-network` 实现并不成立。两处 `!!` 也应在同步实现时消除。
- 修复：直接移植 `http` 模块已经过审查的边界策略，不另写一套简化版本：
  1. 请求 body：复用 `logRequestEnd()`、`logCapturedRequestBody()`、`CapturedRequestBody`、`captureRequestBodyForLogging()` 与无堆栈 sentinel `RequestBodyLimitExceededException`；duplex、one-shot、未知长度和声明超过上限的 body 不调用 `writeTo`，其余最多保留 256 KiB + 1 个探测字节。
  2. 响应 body：只 `source.request(MAX_LOGGABLE_BODY_BYTES + 1)`，从 clone 读取最多 256 KiB；原 `response.body` 的 source 不消费，业务读取保持完整。
  3. 两侧 charset 都改为 `contentType()?.charset(DEFAULT_CHARSET) ?: DEFAULT_CHARSET`，删除 `!!`。

关键响应片段如下；请求侧必须连同 `http` 模块的有限 sink 一起移植，不能仍用普通 `Buffer()`：

```kotlin
// companion object
private const val MAX_LOGGABLE_BODY_BYTES = 256L * 1024

// 修改前 :236-249
source.request(Long.MAX_VALUE)
val buffer = source.buffer
var charset = DEFAULT_CHARSET
val contentType = responseBody.contentType()
if (contentType != null) { charset = contentType.charset(DEFAULT_CHARSET)!! }
...
if (contentLength != 0L) { logger.log(" \n${buffer.clone().readString(charset)}") }

// 修改后
source.request(MAX_LOGGABLE_BODY_BYTES + 1)
val buffer = source.buffer
val charset = responseBody.contentType()?.charset(DEFAULT_CHARSET) ?: DEFAULT_CHARSET
if (!isPlaintext(buffer)) {
    logger.log(" \n<-- END HTTP (binary ${buffer.size}-byte body omitted)")
    return response
}
if (contentLength != 0L) {
    val logged = minOf(buffer.size, MAX_LOGGABLE_BODY_BYTES)
    logger.log(" \n${buffer.clone().readString(logged, charset)}")
    if (buffer.size > logged) logger.log("<-- (body truncated to $logged bytes for logging)")
}
```

- 影响：请求侧最多生成有限日志副本，响应侧只预取上限 + 1 个字节且不消费业务 source，实际 HTTP 请求/响应内容不变。CHANGELOG HTTP-3 条目补「`lib-network` 拦截器同步」。:176 的另一处 `!!` 一并消除。
- 验证：分别增加 1 MiB request/response 测试，断言记录的**body 片段**不超过 256 KiB 且含 truncated 提示（不能用包含 headers 的整份日志长度直接比较）；响应 `body.string()` 仍完整。另测 one-shot/duplex/未知长度 request body 的 `writeTo()` 没有因日志而被调用，以及一个会吞掉 sentinel `IOException` 的自定义 RequestBody 仍无法突破上限。

#### N6 [LOW] `rememberDebounceHandler` 忽略 `debounceTime` 变化（预存）

- 位置：`lib-compose/src/main/kotlin/com/leovp/compose/ext/DebounceExt.kt:78-79`
- 修复：`remember(debounceTime) { DebounceHandler(debounceTime) }`；切换时重置计时属可接受语义，KDoc 注明。`:101` `rememberMultiDebounceHandler()` 无参数，无需 key。

#### N7 [LOW] `GLRenderer.onSurfaceChanged` 锁外读写共享字段（预存）

- 位置：`opengl/src/main/kotlin/com/leovp/opengl/GLRenderer.kt:126-135`；`:67` `hasVisibility` 非 `@Volatile`
- 缺陷：读 `videoWidth/videoHeight`、写 `pointCoord/hasVisibility` 不在 `this` 锁内，而 `setVideoDimension`（:190，生产者线程）在锁内写同一组字段，`feedData`（:226）在锁内读 `hasVisibility`。
- 修复：只给 `hasVisibility` 加 `@Volatile` 只修一半；只锁 :126-135 也会把 `super.onSurfaceChanged()` 对 `outputWidth/outputHeight` 的写和 :113-114 日志对 `videoWidth/videoHeight` 的读留在锁外。应把共享状态快照、`super.onSurfaceChanged()` 以及 :126-135 的重算放进同一个 `synchronized(this)`；`glViewport` 因而也在锁内，但只在 surface 变更时执行，不构成热路径。日志使用锁内捕获的局部尺寸，避免锁外再读字段。与 :141、:190、:226 同用 `this` 监视器，无嵌套锁。

```kotlin
override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
    val videoSize = synchronized(this) {
        super.onSurfaceChanged(gl, width, height)
        if (videoWidth > 0 && videoHeight > 0) {
            pointCoord = createCustomFloatBuffer(
                videoWidth,
                videoHeight,
                keepRatio,
                outputWidth,
                outputHeight,
            )
        }
        hasVisibility = true
        videoWidth to videoHeight
    }
    LogContext.log.w(tag, "onSurfaceChanged=$width x $height video=${videoSize.first} x ${videoSize.second}")
}
```

#### N8 [LOW] generic `result` 的 `cause` 链变更未记入 CHANGELOG

- 位置：`lib-network/.../http/generic/ResultExt.kt:78`、:89（`cause = err.cause` → `cause = err`）、:119-126（新增 `is ApiException` 分支保留 `code/tag`）
- 修复：在 CHANGELOG :128-131 同一条目追加「`ResultServerException`/`ResultConvertException`/`ResultException.cause` 现为原始 `Api*Exception` 本身（此前为其 `cause`，可能为 `null`），根因需 `cause?.cause`；新增 `ApiException` 分支保留 `code`/`tag`」。同时在 generic `result` 的公开 KDoc 增加同一迁移说明，使只看 API 的调用方也能发现 cause 层级变化；不要仅依赖 CHANGELOG。

## 12. 被推翻的发现

- **N5 `lib-compose` release 变体单测清单**：审查者称 `createComposeRule()` 需要的 `ComponentActivity` 只由 `debugApi(ui-test-manifest)` 提供，`testReleaseUnitTest` 会失败。复核：`debugApi` 已覆盖 `testDebugUnitTest` 的运行时 classpath；根 `build.gradle.kts:446` `staticCheck` 只匹配 `^test[A-Z].*DebugUnitTest$`，CLAUDE.md 常用命令亦为 `testDebugUnitTest`，release 单测不在任何约定路径中；Robolectric 对未声明 Activity 通常只警告，「会失败」无法静态证明。不作为缺陷。若希望 `./gradlew test` 全变体可跑，`testImplementation(libs.compose.ui.test.manifest)`（别名 `compose-ui-test-manifest`，`libs.versions.toml:360`）是无害的可选项。
- **G10 `ForegroundComponent.instance` 缺 `@Volatile`**：当前文件 `lib-common-android/src/main/kotlin/com/leovp/android/ui/ForegroundComponent.kt:171-172` 已明确声明 `@Volatile private var instance`，DCL 的发布条件已满足。原发现与当前工作树不符，不作为缺陷。

## 13. 取舍事项的决定记录

以下事项修法不止一种或涉及设计意图，已于 2026-10-20 与作者确认，方案并入 §11：

| 事项 | 决定 | 落点 |
|---|---|---|
| `AbsPref` 序列化失败语义 | 保留旧值（方案 A），只补 KDoc 与 CHANGELOG；不改返回类型 | §11.3 N1 |
| `CameraAvcEncoder.queue` 可见性 | 收为 `private`，元素改为带时间戳的 `EncoderFrame`，到达即打戳 | §11.1 F5 |
| `keyFrameRate` 兼容路径 | 库侧与 demo 侧全删。原先担心的「`ScreenShareSetting` 经网络传递」经核实不成立，该对象只在进程内使用 | §11.1 F6 |
| 流量监控在 Activity 不可见期间是否继续轮询 | **继续轮询是期望行为**（2026-10-20 作者确认）。因此 `onStop` 不应把「仍有监控存活」当成泄漏，也不要改成 `onStart` 开、`onStop` 停 | §11.2 G12 |
| 回调死锁只靠 KDoc 约束是否足够 | **不够**（2026-10-20 作者确认）。改为出锁派发，使使用方即便在回调里阻塞也无法死锁；代价是允许一个尾随回调，并同步改写两处 KDoc | §11.2 G13 |

## 14. 验证状态与后续命令

Codex 已在同一代码 HEAD `e57608f70f84c9e52a795d2f2b44fc0300117cc3` 上执行：

```bash
./gradlew staticCheck --rerun-tasks --max-workers=1 --console=plain
```

结果为 `BUILD SUCCESSFUL in 7m 36s`，`1425 actionable tasks: 1425 executed`。这只能说明**当前代码**通过仓库聚合的静态检查、单测/lint 与仪器测试 APK 构建；它不验证本文尚未落地的修复方案，也不等于真机测试。

第三至第五轮只修订本文，没有再次取得 Gradle 通过结果。第三轮曾尝试执行窄范围 `:screencapture:detekt :floatview:detekt --rerun-tasks`，但在任务启动前即因沙箱无法写入 Gradle wrapper lock 文件而失败，因此不计作验证证据。第五轮重新对照 AOSP `FrameDropper.cpp`、`ConnectivityManager.CallbackHandler` 与当前 FloatView 控制流，并完成文档级检查：关键源码位置逐项对照；Kotlin 代码围栏成对；无行尾空白。历史 `staticCheck` 与本轮文档检查必须分开解读。

实现本文修复后仍需按受影响范围重新执行：

```bash
./gradlew --continue --rerun-tasks \
  :screencapture:testDebugUnitTest :camera2live:testDebugUnitTest :lib-mvvm:testDebugUnitTest \
  :floatview:testDebugUnitTest :lib-common-android:testDebugUnitTest :androidbase:testDebugUnitTest \
  :lib-network:testDebugUnitTest :lib-compose:testDebugUnitTest :pref:testDebugUnitTest :log:testDebugUnitTest \
  :demo:assembleDevDebug detekt ktlintCheck
```

真机项：F1 丢帧比例、F2 回调线程名与 StrictMode、F3 主线程 tick 序列、F7 HEVC + MP4 configure、G1 并发 toast、G3 的 `onActivityDestroyed` 时序（可用 Robolectric 补测替代）。

### 14.1 2026-10-21 实施后的验证

本节只记录第六轮之前确认的 26 项在 `e57608f70` 时的验证状态。当时 G12 的删除部分已随 `eaaa49e62` 落地，G11/G13 尚未实施；后续 `b0c417d95`/`f6cbb985e` 已改变该状态，最新结论以 §11.2 各条实施状态与 §15.5 为准。

§11 中第六轮之前 26 项的本轮实现完成后，Codex 对受影响范围重新执行了以下测试与 Demo 集成编译；
该结果不代表 §15.1 列出的部分落地项已经补齐：

```bash
./gradlew \
  :screencapture:testDebugUnitTest :camera2live:testDebugUnitTest \
  :androidbase:testDebugUnitTest :floatview:testDebugUnitTest \
  :lib-common-android:testDebugUnitTest :lib-compose:testDebugUnitTest \
  :lib-mvvm:testDebugUnitTest :lib-network:testDebugUnitTest \
  :opengl:testDebugUnitTest :pref:testDebugUnitTest \
  :android-restricted:testDebugUnitTest :demo:compileDevDebugKotlin \
  --rerun-tasks --max-workers=1 --console=plain
```

结果为 `BUILD SUCCESSFUL in 2m 45s`，`584 actionable tasks: 584 executed`。其中
`opengl:testDebugUnitTest` 与 `android-restricted:testDebugUnitTest` 为 `NO-SOURCE`，只能证明对应模块
编译进入该验证链路，不能表述为存在回归测试并通过。新增或扩展的测试覆盖：API 23+ 与模拟 API 21–22
的 MediaCodec callback 线程选择、倒计时完整 60 秒序列与早醒、相机入队时间戳、MP4 三阶段失败清理、
FloatView 有界 detach 重试、Toast 并发投递、NetworkMonitor 会话隔离、网络 callback 失败脱钩、
Compose 参数变化、Result 原始 body，以及 HTTP 日志的有界请求/响应读取。

所有改动库模块的 `ktlintCheck` 与 `detekt` 均以 `--rerun-tasks` 重新执行并通过；
`:demo:detekt --rerun-tasks` 通过。`:demo:ktlintCheck --rerun-tasks` 被未修改文件
`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/receiver/AudioReceiver.kt:165` 的既有
`function-expression-body` 规则问题阻断，本轮未越界修改该文件；本轮改动的两个 Demo 文件只删除了
已经移除的参数调用，并已由 `:demo:compileDevDebugKotlin` 验证。补丁还通过 `git diff --check`。

尚未执行真机验证。尤其 API 21–22 分支目前只在 Robolectric 的缓存 API 28 环境中临时覆盖
`Build.VERSION.SDK_INT` 进行控制流测试，未验证老设备上 MediaCodec 的真实 Looper 绑定；F1、F2、F7、
F8、G1、G3、N7 等涉及真实编码器、MediaProjection、窗口、OpenGL 或平台调度的行为仍须按本文对应
条目的设备矩阵执行。原“真机项”中的 F3 已增加 JVM 可控时钟回归测试，但主线程真实调度表现仍未
在设备上观察。

### 14.2 2026-10-21 公开 API 复核修正

实施后再次复核公开 API，补充两项修正：

- 删除 `Screenshot2H26xStrategy.Builder` 空壳子类，统一使用顶层
  `Screenshot2H26xBuilder`。原空壳无法保持 setter 的静态返回类型，既不能完整兼容旧调用方式，又会
  形成两个并存入口；公开 KDoc 与 CHANGELOG 已记录替换方式。
- 撤销注销链路上错误的 `ACCESS_NETWORK_STATE` 权限契约，包括
  `NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS()`、`NetworkMonitor.stopMonitor()` 和
  `BaseActivity.stopTrafficMonitor()`。Android 平台的 `unregisterNetworkCallback()` 不要求该权限；
  注册与启动入口仍保留权限声明，`BaseActivity.onDestroy()` 不再需要 `MissingPermission` 抑制。

修正后重新执行：

```bash
./gradlew \
  :screencapture:testDebugUnitTest :screencapture:ktlintCheck \
  :screencapture:detekt :screencapture:lintDebug \
  :lib-common-android:testDebugUnitTest :lib-common-android:ktlintCheck \
  :lib-common-android:detekt :lib-common-android:lintDebug \
  :androidbase:testDebugUnitTest :androidbase:ktlintCheck \
  :androidbase:detekt :androidbase:lintDebug \
  :demo:compileDevDebugKotlin \
  --rerun-tasks --max-workers=1 --console=plain
```

结果为 `BUILD SUCCESSFUL in 3m 12s`，`631 actionable tasks: 631 executed`；另通过
`git diff --check`。本轮未执行真机测试，这两项修正不改变编码、网络注册或平台回调的运行逻辑。

## 15. 第八轮：实施后核查（Claude，2026-10-21）

### 15.0 范围与方法

对第七轮（Codex，提交 `dd08a87c7` + `eaaa49e62`）的实施逐条核查：§11 的 26 项是否落地、落地是否与方案
一致、以及本轮是否引入新问题。基线取实施前的 `e4ca2e9dd`，diff 为 45 个文件、+2029/−620。

方法：按模块分四路并行核查（录屏 F1/F4/F6–F10；相机与倒计时 F2/F3/F5；悬浮窗、Toast、网络监控
G1–G9；网络库、Compose、OpenGL、重命名 N1–N4/N6–N8），每条结论要求给出实施后的文件:行号与代码
证据；其后对结论中影响判断的关键断言逐条回源码复核。全部结论为源码静态分析，**未执行任何 Gradle
命令**。

### 15.1 26 项实施结论

**26 项均有实施改动，但并非全部完整落地。** 21 项与方案一致；F8、N8 采用不同但完整的实现；
F6、F9、G7 仅部分落地，剩余项见本表及 §15.3。

| 条目 | 结论 | 要点 |
|------|------|------|
| F1 | 一致 | `Screenshot2H26xStrategy` 的 `KEY_MAX_FPS_TO_ENCODER` 整段删除；方案要求保留的 `ScreenRecordMediaCodecStrategy.kt:192` 确实保留 |
| F2 | 一致 | API 23+ 双参 `setCallback` 在 `configure()` 前；API 21–22 用 `FutureTask` 把 `createEncoderByType` 本身搬到 HandlerThread，不是只搬 `setCallback`。三处同构，失败/超时的所有权交接无双重 release |
| F3 | 一致 | 对外值只由 `nextTickAtMillis` 递减决定、完全不读时钟，早醒问题被结构性消除而非绕过；60 秒序列 61 个值不漏不重 |
| F4 | 一致 | `releaseHandler()` 先于 `h26xEncoder.release()`；未 `onStart()` 时 `onStop()` 提前返回但线程仍被 `quitSafely()` |
| F5 | 一致 | 采用泛型 `BoundedFrameQueue<T>` / `drain<T>`，避开 `EXPOSED_PROPERTY_TYPE`；`drain` 不再硬编码 `ByteArray` |
| F6 | 部分落地 | 删除清单 7 项完成 6 项；Builder 被提升为独立 `Screenshot2H26xBuilder`，带出方案未声明的 API 形状变化（见 §15.4.1） |
| F7 | 一致 | `EncodeType.H264` 守卫包住 profile/level；`fallBackToAvcIfMuxerCannotCarryHevc()` 在 `build()` 开头执行，读取时机正确 |
| F8 | 做法不同（更好） | 注册幂等化消除了重复注册缺陷，故保留 `takeScreenshot()` 里的注册调用比按方案删掉更正确；`builder.mediaProjection?.` 替代方案中的 `!!` |
| F9 | 部分落地 | 注释两点达标；`Screenshot2H26xStrategy.kt` 1232 → 1032 行，仍超 800 行上限 |
| F10 | 一致 | 所有权在可抛步骤前发布；`catch (Throwable)` 不重抛，`Error` 也走完清理；只有 `stop()` 成功才保留文件 |
| G1 | 一致 | `toastRequestToken` / `dismissToken` 分离，`removeCallbacksAndMessages(null)` 已彻底消失 |
| G2 | 一致 | `build(): Boolean`，`tryShow()` / `createAndShowFloatView()` 都按返回值短路 |
| G3 | 一致 | 首次 + 3 次重试；耗尽后 `owner.release()` 与 `onRemoved(this)` 无条件执行。第五轮被推翻的判断由两个测试分别锁住「不清重试」与「清了即补投」 |
| G4 | 一致 | 锁顺序 `monitorLifecycleLock → wifiNetworkCallbackLock` 单向，反向路径不存在；`@RequiresApi(S)` 类只在 SDK 分支内实例化 |
| G5 | 一致 | `NetworkMonitor` 构造处与 `TrafficStatHelper` 构造器两层都做了 `applicationContext` 兜底，没有只改一处 |
| G6 | 一致 | 与配置无关的计算留在 `try` 外，`config` 访问全部移入 `try`；`LeoToast` 只持 app context |
| G7 | 部分落地 | 六处行为变更完成 5 处，第 6 处 `cancelToast` 未落地（见 §15.3） |
| G8 | 一致 | `join(3_000)` + `assertFalse(isAlive)`，超时即 fail，不是加 sleep |
| G9 | 一致 | 会话级 lambda 替代固定 lambda，`unregisterWifiSignalCallback()` 先脱钩再注销。§15.2.2 的后续顾虑已随 G13 解决，且保留短锁可避免陈旧信号覆盖 |
| N1 | 一致 | 语义为作者选定的「A 保留旧值」；两处 `if (json.isNotEmpty())` 守卫 + 两处 KDoc + CHANGELOG |
| N2 | 一致 | KDoc 迁移说明 + CHANGELOG 加粗条目；`CLAUDE.md:76` 按方案仍列为待授权项（见 §15.4.3） |
| N3 | 一致 | 2xx 复用转换器已读的 `err.message`，非 2xx 才读 body；不新增内存副本。但引入新问题，见 §15.2.1 |
| N4 | 一致 | 响应侧 `source.request(MAX + 1)` + `buffer.clone()` 只读，不破坏下游 `body.string()`；请求侧有限 sink 三分支都先记截断再抛无栈 sentinel |
| N6 | 一致 | `remember(debounceTime)`；KDoc 注明重置语义 |
| N7 | 一致 | 日志改用锁内快照，锁外不再读共享字段；加锁范围覆盖全部四个字段，`hasVisibility` 因此不需要 `@Volatile` |
| N8 | 做法不同（更好） | CHANGELOG 落点改为「变更」节独立加粗条目，比塞进原条目清楚。但条目文本有一处事实错误，见 §15.3 |

### 15.2 本轮新引入的问题（MEDIUM）

#### 15.2.1 [MEDIUM] 2xx 转换失败时整份响应体被灌入异常 message

- 位置：`lib-network/src/main/kotlin/com/leovp/network/http/net/ResultExt.kt:124`
- 对比基线：`e4ca2e9dd` 为 `message = err.cause?.message`，本轮改为 `message = err.cause?.message ?: err.message`。
- 缺陷：同函数 `:112-113` 的注释已写明，2xx 路径下 `ConvertException.message` **按设计就是原始 body**
  （`net/converters/SerializationConverter.kt:59-71` 先 `response.body.string()` 再 `throw ConvertException(response, message = bodyString, cause = parseErr)`）。当解析器抛出的异常自身
  `message == null`（裸 `NullPointerException()`、`IllegalStateException()` 等），fallback 取到整份 body；
  再经 `exception/ResultException.kt:12` 的 `Exception("$code:$message", cause)` 固化进异常字符串，随
  `toString()`、堆栈与崩溃上报外泄。基线此场景下 `message` 为 `null`。
- 失败场景：2xx 响应体为 10 MB 含 PII 的 JSON + 解析器抛 `IllegalStateException()` → 该 10 MB 全文进入
  `ResultConvertException.message` 与崩溃上报。
- 修复：原始 body 已由 `responseBodyString` 承载，`message` 不应再装一遍。

```kotlin
// lib-network/.../net/ResultExt.kt，companion 或文件级常量
private const val MAX_CONVERT_FAILURE_MESSAGE_LENGTH = 512

// :121-128
Result.Failure(
    ResultConvertException(
        statusCode = err.response.code,
        // Do not fall back to err.message: on a 2xx the converter puts the whole raw body
        // there, and ResultException folds message into its own Exception message, which
        // would carry the payload into logs and crash reports. The body stays in
        // responseBodyString, which callers read deliberately.
        message = err.cause?.message?.take(MAX_CONVERT_FAILURE_MESSAGE_LENGTH)
            ?: "Response body conversion failed.",
        cause = err,
        responseBodyString = bodyString,
        tag = err.tag
    )
)
```

- 配套：`ResultExtTest.kt` 增一例 —— 2xx + 解析器抛 `IllegalStateException()`（无 message），断言
  `message` 不含 body 片段且 `responseBodyString` 仍完整。CHANGELOG「安全」一条。
- 验证：JVM 单测可完全覆盖。未验证。

#### 15.2.2 [MEDIUM，2026-10-21 已随 G13 解决] G9 的会话回调把 `monitorLifecycleLock` 暴露给 ConnectivityManager 回调线程

- 位置：`androidbase/.../utils/network/NetworkMonitor.kt:230-234`（会话 lambda）、
  `lib-common-android/.../utils/NetworkUtil.kt:307`（注册未传 Handler/Executor）、`:361-372`（framework
  wrapper 调用点）
- 对比基线：修复前该回调只做一次 `@Volatile` 写，不取任何锁。
- 缺陷：`sessionCallback` 体整体包在 `synchronized(monitorLifecycleLock)` 内，而它由
  `WifiSignalNetworkCallback.onCapabilitiesChanged` / `onLost` 调用；`registerNetworkCallback(request,
  networkCallback)` 没有 Handler/Executor 重载参数，故派发在 AOSP 的进程内单例 `ConnectivityThread`
  上。于此同时同一把锁在监控线程上被持有整个结果派发块（`:127-150`，含 G13 指出的 `callback.invoke`）。
- 失败场景：使用方在 `callback` 里对主线程同步等待（G13 已确认的死锁模式），主线程同时在
  `onDestroy → stopMonitor()` 等锁。原本冻结主线程与监控线程两条；现在 `ConnectivityThread` 第三条
  一并冻死，**整个进程**收不到任何 `NetworkCallback`（含应用其它网络监听）。
- 最终处理：不采用下述「会话回调去锁」方案。G13 已把使用方回调移出 `monitorLifecycleLock`，ConnectivityManager 回调现在只会等待 start/stop 或结果快照的短临界区，不再会被阻塞的使用方回调拖住，原死锁链已断开。保留这把短锁还避免了「旧 session 通过 generation 校验 → stop/start 清空并写入新信号 → 旧 session 再覆盖 `globalWifiSignal`」的检查后写入竞态。

```kotlin
// 最终实现：校验与写入仍在同一个短临界区内。
val sessionCallback: (NetworkUtil.WifiSignal) -> Unit = { signal ->
    synchronized(monitorLifecycleLock) {
        if (isCurrentMonitor(generation, scheduler)) globalWifiSignal = signal
    }
}
```

- 复核结论：原建议的去锁方案会把 generation 校验与 `globalWifiSignal` 写入拆成非原子的检查后动作，不应实施；「stop/start 会清空」不能阻止已经通过校验的旧回调在清空之后再写回。最终代码保留锁是正确选择；现有 `late Wi-Fi callback from an old session cannot overwrite the current signal` 用例与本次重跑结果均通过。

#### 15.2.3 [MEDIUM] F5 的核心行为零测试覆盖，新增用例为空通过

- 位置：`camera2live/src/test/kotlin/com/leovp/camera2live/codec/PendingInputBuffersTest.kt:54-72`
- 缺陷：用例把时间戳硬编码进 `EncoderFrame(byteArrayOf(1), 10_000L)` / `(byteArrayOf(2), 25_000L)`，
  只断言 `drain` 原样透传。F5 的核心是 `offerDataIntoQueue` **入队即打戳**（`CameraAvcEncoder.kt:265`），
  把打戳挪回 submit 时刻，该用例依然全绿。方案原本要求「注入慢 `submit`，断言两帧 PTS 差约等于入队
  间隔而非约 0」，未落地。
- 修复：`elapsedMicrosSinceFirstFrame()` 当前直接调 `System.nanoTime()`（`:338`），不可注入。按 F3 已
  采用的同一手法补一个 `internal` 构造参数：

```kotlin
// CameraAvcEncoder.kt:31-38 —— 追加 internal 时钟参数；公开构造签名不变
class CameraAvcEncoder internal constructor(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    private val frameRate: Int,
    private val iFrameInterval: Int = DEFAULT_KEY_I_FRAME_INTERVAL,
    private val bitrateMode: Int = DEFAULT_BITRATE_MODE,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    @JvmOverloads
    constructor(
        width: Int,
        height: Int,
        bitrate: Int,
        frameRate: Int,
        iFrameInterval: Int = DEFAULT_KEY_I_FRAME_INTERVAL,
        bitrateMode: Int = DEFAULT_BITRATE_MODE,
    ) : this(width, height, bitrate, frameRate, iFrameInterval, bitrateMode, System::nanoTime)

// :337-341
private fun elapsedMicrosSinceFirstFrame(): Long {
    val now = nowNanos()
    timestampOriginNanos.compareAndSet(UNSET_TIMESTAMP_ORIGIN, now)
    return (now - timestampOriginNanos.get()) / 1_000L
}
```

  新增用例：用可控 `nowNanos` 连续两次 `offerDataIntoQueue`，时钟推进 15 ms，断言队列中两帧的
  `presentationTimeUs` 为 `0` 与 `15_000`；再构造「两帧快速入队、submit 被延迟」的时序，断言 PTS 差仍
  约等于**入队**间隔而非约 0。该用例在打戳挪回 submit 时刻后必然失败。
- 风险：`init { initEncoder() }`（`:56-58`）在构造期就建编码器，单测需 `mockkStatic(MediaCodec::class)`，
  与现有 `CameraAvcEncoderCallbackThreadTest` 同一套桩可复用。
- 影响：公开构造签名不变（`@JvmOverloads` 保留在公开 secondary 上），无 KDoc 迁移说明需求。
- 验证：JVM/Robolectric。未验证。

#### 15.2.4 [MEDIUM] `timestampOriginNanos` 的 KDoc 与实际行为矛盾

- 位置：`camera2live/.../CameraAvcEncoder.kt:64-68`
- 缺陷：KDoc 仍写 `latched on the first frame handed to the encoder`，而 F5 之后原点是在帧**到达队列**时
  锁存（`:265` → `:337-341`）。同一文件 `:334-335` 的新 KDoc 写的是 `Called when a frame reaches
  [offerDataIntoQueue]`，两处自相矛盾。差别有实际后果：被 `BoundedFrameQueue`（容量 5）挤掉的首帧也会
  锁存原点，因此首个真正送进编码器的 PTS 会大于 0 —— 这正是 F5 预判的现象，读错 KDoc 的人会把它当 bug。
- 修复：

```kotlin
/**
 * Monotonic origin for presentation timestamps, latched when the first frame reaches
 * [offerDataIntoQueue] — not when a frame first reaches the encoder. A frame dropped by the
 * bounded queue still latches the origin, so the first timestamp actually submitted may be
 * greater than zero. Sentinel is `Long.MIN_VALUE` rather than `0`: `System.nanoTime()`'s
 * origin is arbitrary and a reading of exactly zero is legal, which would re-latch it every
 * frame.
 */
```

- 影响：仅注释。无 CHANGELOG 需求。

#### 15.2.5 [MEDIUM，可维护性] `http` 与 `lib-network` 两份有界日志实现完全重复

- 位置：`http/src/main/kotlin/com/leovp/http/okhttp/HttpLoggingInterceptor.kt` 与
  `lib-network/src/main/kotlin/com/leovp/network/interceptors/HttpLoggingInterceptor.kt`
- 缺陷：`MAX_LOGGABLE_BODY_BYTES`、`CapturedRequestBody`、`captureRequestBodyForLogging`、
  `RequestBodyLimitExceededException`、`isPlaintext`、`logRequestEnd`、`logCapturedRequestBody` 各约 120 行
  在两个模块各存一份，且已出现漂移（`hasReadableBody()` vs `promisesBody`、DIVIDER 宽度、日志文案）。
  后续「一侧改了上限另一侧没改」的复现风险高。
- 修复方向：`http/build.gradle.kts:19` 已 `implementation(projects.libNetwork)`，把上述共用件抽到
  `lib-network` 的 `api` 可见工具中，`http` 侧只保留自己的差异部分。
- 影响：模块间 API 新增，需 CHANGELOG「变更」。**本条改动面较大且非正确性问题，是否本轮一并做由作者
  决定**；若暂不做，建议在两份文件头部互相注明「与 X 保持同步」。

### 15.3 本轮新引入的问题（LOW）

| # | 位置 | 问题与修复 |
|---|------|-----------|
| L1 | `lib-common-android/.../exts/ToastExt.kt:359-361` | **G7 第 6 处未落地**。KDoc 仍为 master 原文 `This method also works in thread.`，但 `:362-375` 现在对非主线程调用改为 `mainHandler.post(cancelAction)`，后台线程调用返回时 toast 尚未取消。`grep cancelToast CHANGELOG.md` 零命中。修复：KDoc 改为「Safe to call from any thread. A call from a background thread is posted to the main thread, so the toast is not yet gone when this returns.」并补 CHANGELOG「变更」 |
| L2 | `lib-network/.../generic/ResultExt.kt:51-52`、`CHANGELOG.md:79-80` | **N8 文本有事实错误**。两处把 `ResultException` 并列进「cause 此前为其 cause」。核对 master：`generic/ResultExt.kt:77`、`:88` 两处确为 `cause = err.cause`，但 `:123` 的 `else -> ResultException(message = err.message, cause = err)` 本来就是 `err`，从未变更。照现文档调用方会去找不存在的一层 `cause?.cause`。修复：把 `ResultException` 从该句移出，单列「新增 `is ApiException` 分支保留 `code` 与 `tag`」 |
| L3 | `screencapture/src/test/.../Mp4TrackWriterTest.kt` | **F10 测试缺反向对照**。三个用例全部断言「文件被删」，没有「成功 `close()` 必须保留文件」的用例。`completed` 退化为恒 `false` 时三条全绿，而真实后果是正常录屏的 MP4 被静默删掉 —— 恰为 F10 要防问题的镜像。修复：补一例「写入样本后正常 `close()`，断言 `output.exists()` 且 `verify(exactly = 1) { muxer.stop() }`」。方案原列 6 例只实现 3 例，另缺 `start()` 抛 Exception、`addTrack()`/`stop()` 抛 Error |
| L4 | `lib-network/src/test/.../HttpLoggingInterceptorTest.kt:22-34` | **N4 的核心性质无回归保护**。两个大 body 用例都用 `String.toResponseBody()`（body 本就整份在内存），只验证了日志片段有界；把 `source.request(MAX + 1)` 改回 `Long.MAX_VALUE` 测试仍通过。修复：自定义 `ResponseBody`，其 `source()` 返回按需产生字节的 `Source` 并记录已读字节数，断言 ≈ 256 KiB |
| L5 | `screencapture/.../ScreenRecordMediaCodecStrategy.kt:478` + `:307-316` | **F8 残留时序缺口**。`onRelease()` 之后再调 `takeScreenshot()` 会在 API 34+ 重新注册 callback，此后无任何注销点。方案未覆盖此路径。修复：`takeScreenshot()` 在自身返回前注销它本次新注册的 callback，或在 `onRelease()` 后把实例标记为终态并拒绝 `takeScreenshot()` |
| L6 | `screencapture/.../ScreenRecordMediaCodecStrategy.kt:285` → `:296-298` | `onInit()` 中 `registerCallbackAboveAndroid14()` 在 `createVirtualDisplay` 之前；后者抛异常时 catch 只释放 encoder 与 callback thread，不注销已注册的 callback，`projectionCallbackRegistered` 留 `true`。调用方若在 `onInit()` 失败后直接放弃（不调 `onRelease()`），callback 悬挂。可辩护（为重试保留注册状态），但方案未讨论，建议在 catch 中显式注销或在 KDoc 写明「`onInit()` 失败后仍须调用 `onRelease()`」 |
| L7 | `screencapture/.../Screenshot2H26xBuilder.kt:46` | F6 要求「三个 Builder 的 `setFps()` KDoc 追加同一句迁移说明」，这一个漏了（另两个已写于 `ScreenCapture.kt:61`、`ScreenRecordMediaCodecStrategy.kt:136`）。该 Builder 正是 `BY_IMAGE_2_H26X` 与 demo 使用的链路 |
| L8 | `screencapture/.../Screenshot2H26xStrategy.kt` | F9 第 3 点未达标：1232 → 1032 行，仍超 `coding-style.md` 的 800 行上限（detekt 不卡此项）。F2 的 API 21–22 `FutureTask` 分支同轮又加回约 30 行 |
| L9 | `LeoTextureView.kt:207-212`、`CameraAvcEncoder.kt:239-244`、`Screenshot2H26xStrategy.kt:636-641` | 三处同构：`task.cancel(true)` 会中断正在执行原生 codec 创建的 callback 线程，中断位残留在这条继续 loop 的线程上；`catch (Throwable)` 转抛 `InterruptedException` 时未 `Thread.currentThread().interrupt()` 恢复状态。不致错，属健壮性瑕疵 |
| L10 | `lib-mvvm/.../ScreenCountdownManager.kt:152-162` | F3 在 duration 非整秒时最后一跳语义变了：`duration = 2500` 新序列为 `2500, 1500, 500, 0`（旧为 `…, 1000, 0`），总时长不变。文档「相邻差恒为 −1000」只在整秒 duration 下成立，建议在 KDoc 写明 |
| L11 | `CHANGELOG.md` 约 `:82`、`:280`、`:283` | 新增块与既有条目之间各插入一个空行，把「变更」「修复」两节的紧凑列表切成松散列表，渲染间距不一致。四路核查独立报出同样位置。修复：删除这三处空行 |
| L12 | `CHANGELOG.md:615`、`demo/.../examples/log/LogActivity.kt:21` | N2 的旧名残留两处：前者 AR 系列条目仍用 `DeviceUtil` 指代被改名的 android-restricted 类，与同一 `## [Unreleased]` 内 `:70` 的重命名条目自相矛盾；后者 KDoc 引用并不存在的 `DeviceUtilRestricted.getDeviceInfo(this)`（该方法在 `lib-common-android` 的 `DeviceUtil` 上，为 `ffb2c798f` 全量改名时的误伤，非本轮引入） |

### 15.4 待作者决定的事项

本节原列三条涉及取舍或配置文件授权的事项，**均已于 2026-10-21 处理完毕**：§15.4.1 与 §15.4.2 由
Codex 在提交 `ba6ea5ed7` 中实施（两处都选了与本文倾向一致的方案），§15.4.3 经作者授权后修改。
本节保留原选项与依据以备追溯，各条末尾记录实际落点。

#### 15.4.1 `Screenshot2H26xStrategy.Builder` 的空壳子类留不留（2026-10-21 已实施：删除）

F9 拆文件时把 `Builder` 实现提升为独立的 `open class Screenshot2H26xBuilder`，原嵌套类变成空壳子类：

```kotlin
// Screenshot2H26xStrategy.kt:109-111
/** Public API wrapper; implementation lives in [Screenshot2H26xBuilder]. */
class Builder(width: Int, height: Int, dpi: Int, screenDataListener: ScreenDataListener) :
    Screenshot2H26xBuilder(width, height, dpi, screenDataListener)
```

带来三点方案未声明的公开 API 变化：setter 声明在父类上并用 `apply` 返回接收者，故
`Builder(...).setFps(20f)` 的静态类型由 `Builder` 变为 `Screenshot2H26xBuilder`；
`Screenshot2H26xStrategy` 主构造器收为 `internal`（`:96`）；新增公开类 `Screenshot2H26xBuilder`。
链式 `…setFps(x).build()` 仍编译（`ScreenCapture.kt:104-118` 已验证），但
`val b: Screenshot2H26xStrategy.Builder = Screenshot2H26xStrategy.Builder(…).setFps(20f)` 这类把中间
结果声明为子类型的调用方会编译失败。CHANGELOG 无条目，`Builder` 的 KDoc 也只写「Public API wrapper」。

两个选项：

- **保留空壳**：须补 CHANGELOG「变更」加粗条目说明 setter 返回类型变化，并在 `Builder` KDoc 写迁移说明。
- **删除空壳**：调用方直接改用 `Screenshot2H26xBuilder`。更符合本项目「API 演进约定」——该空壳本身就是
  只为旧写法存在的兼容入口，而约定明确「不保留只为旧版本存在的重载、参数、分支或兼容开关」。同样须记
  CHANGELOG「变更」。

**实施结果（`ba6ea5ed7`）**：选了删除。空壳类移除；`Screenshot2H26xBuilder` 由 `open class` 收回
`class`（已无子类）；`ScreenCapture.kt:105` 与两处测试调用点切至 `Screenshot2H26xBuilder`；
`Screenshot2H26xBuilder` 的 KDoc 补了替换说明；CHANGELOG「变更」新增加粗条目「**截图式录屏 Builder
改为顶层 API**」。代码内零残留引用，测试与 Builder 同包故无需 import。

**该次提交引入并已修正的回归**：删除空壳时同时删掉了 `Screenshot2H26xStrategy.kt` 的
`import ...base.ScreenDataListener`（空壳构造参数是它在该文件唯一的代码使用点），但
`mp4Writer` 的 KDoc 仍引用 `[Builder.mp4OutputFile]` 与 `[ScreenDataListener]` —— 前者指向已删除的
类，后者因 import 移除而无法解析。两处均为 KDoc 链接，kotlinc 不校验，故编译、detekt、ktlint 全部
通过，属静默文档回归。已于同日修正为 `[Screenshot2H26xBuilder.mp4OutputFile]` 与全限定的
`[com.leovp.screencapture.screenrecord.base.ScreenDataListener]`（用全限定名而非加回 import，避免
引入只为 KDoc 存在的 import 触发 detekt 未用 import 判定）。

#### 15.4.2 注销路径上的 `@RequiresPermission` 要不要撤（2026-10-21 已实施：撤销）

`NetworkMonitor.kt:212` 给 `stopMonitor()` 加了 `@RequiresPermission(ACCESS_NETWORK_STATE)`，
`NetworkUtil.kt:332` 的 `unregisterWifiNetworkStatsAboveAndroidS` 同样；而该路径最终只调
`connectivityManager.unregisterNetworkCallback()`（`NetworkUtil.kt:380`），AOSP 对此方法无权限要求。

连带后果：`BaseActivity.stopTrafficMonitor()`（`:227`）也必须标注，`onDestroy()`（`:151`）必须加
`@SuppressLint("MissingPermission")` 才能过 lint。那个 `@SuppressLint` 本身即为注解加错的信号。
这是**公开基类的 API 契约变更**，所有继承 `BaseActivity` 并调用 `stopTrafficMonitor()` 的外部使用者都被
迫声明该权限或自行抑制，而 CHANGELOG 无对应条目。

两个选项：

- **撤销**（倾向）：去掉 `stopMonitor()`、`unregisterWifiNetworkStatsAboveAndroidS`、
  `stopTrafficMonitor()` 三处 `@RequiresPermission`，并删掉 `onDestroy()` 的 `@SuppressLint`。
- **保留**：须记入 CHANGELOG「变更」并加粗标为破坏性契约变更。

**实施结果（`ba6ea5ed7`）**：选了撤销，范围准确且比本文所列多覆盖一处。移除的注解为
`NetworkMonitor.stopMonitor()`、`NetworkMonitor.unregisterWifiSignalCallback()`（本文未列，同属注销
链路，撤销正确）、`NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS()`、
`BaseActivity.stopTrafficMonitor()`；`BaseActivity.onDestroy()` 的 `@SuppressLint("MissingPermission")`
与相应解释注释一并删除。注册路径的 `@RequiresPermission` 全部保留（`registerNetworkCallback` 确需
该权限）。CHANGELOG 对应条目由「两者只需 `ACCESS_NETWORK_STATE`」更正为「注册需要
`ACCESS_NETWORK_STATE`，注销不要求权限」。

复核过删注解最易引发的失效 import：三个文件的 `SuppressLint`、`RequiresPermission`、`Manifest`、
`RequiresApi` 在正文中均仍有使用点，无孤儿 import（detekt `maxIssues=0` 不会因此失败）。

#### 15.4.3 `CLAUDE.md:76` 的旧名 `DeviceUtil` 是否同步（2026-10-21 已完成）

该行描述 `android-restricted` 模块时仍列 `DeviceUtil`，而该类已改名为 `DeviceUtilRestricted`
（`ffb2c798f`）。因 `DeviceUtil` 仍是 `lib-common-android` 中真实存在的类，这一行不是单纯过时，而是会
把读者导向错误模块。改动仅一个词。按 N2 原方案与 `AGENTS.md`，`CLAUDE.md` 默认只读、需作者明确授权
才修改。**作者已于 2026-10-21 明确授权，`CLAUDE.md:76` 的 `DeviceUtil` 已改为 `DeviceUtilRestricted`**；
该文件中已无其它旧名引用。§15.3 L12 的另两处旧名残留（`CHANGELOG.md:615`、
`demo/.../examples/log/LogActivity.kt:21`）不属该文件，仍待实施。

### 15.5 验证状态

| 项 | 状态 |
|----|------|
| 26 项实施范围与一致性 | 源码静态核查完成；F6、F9、G7 为部分落地 |
| 关键断言回源码复核 | 完成（含三处对核查结论本身的纠正，见下） |
| 编译 / detekt / ktlint / 单测 | **未验证**（Claude 运行在远端，无法执行 Gradle） |
| G11/G13 后续提交的 Codex 本机验证 | 在 `origin/review/native-modules@f6cbb985e` 的独立临时副本执行 `:androidbase:testDebugUnitTest :androidbase:ktlintCheck :androidbase:detekt --rerun-tasks`；**BUILD SUCCESSFUL**，162 个任务实际执行 |
| G11 停止栅栏最终修复 | 2026-10-22 在当前工作树执行同一组 androidbase 单测、ktlint、detekt；**BUILD SUCCESSFUL in 32s**，162 个任务实际执行；详见 §17 |
| 真机 | **未验证** |

两项特别需要本机确认：`:camera2live:compileDebugKotlin`（`drain<T>` 的类型推断能否通过）；F2 在真机
API 21–22 上的 callback 线程归属 —— 现有三个 `*CallbackThreadTest` 是 Robolectric 下
`ReflectionHelpers.setStaticField(Build.VERSION, "SDK_INT", 22)` 的控制流测试，codec 被完全 mock，
证明不了真实 MediaCodec 的 Looper 绑定。§14.1 对此已如实声明，不属过度声明。

本轮核查中纠正的三处结论：

1. 「锁内有 `getSpeed()` 的阻塞 `/proc` I/O」**不成立**。`getSpeed()`（`TrafficStatHelper.kt:56`）走
   `downloadSpeed`/`uploadSpeed` → `TrafficStats.getTotalRxBytes()`/`getTotalTxBytes()`；`/proc/uid_stat/`
   的 `RandomAccessFile` 在 `sendTraffic`/`receiveTraffic`，只喂构造期一次性求值的 `totalTraffic`
   （标了 `@Suppress("unused")`）。§11.2 G13 中「只读计数器」的措辞准确，无需修改。
2. 「本轮引入/保留两处 `!!`」**不准确**。`ScreenRecordMediaCodecStrategy.kt` 的 `!!` 由基线 5 处减至
   4 处（`h26xEncoder!!.createInputSurface()` 已消除），剩余 4 处均为基线原有、仅因改动位移。
3. `camera2live/.../CameraAvcEncoder.kt:146` 的 `KEY_MAX_FPS_TO_ENCODER` **不属 F1 残留**。该类为
   ByteBuffer 输入编码器（`configure(fmt, null, null, ENCODE)` + `COLOR_FormatYUV420Flexible` +
   `queueInputBuffer`），该键只对 Surface 输入编码器生效，故为无效配置而非 F1 缺陷；且基线即存在
   （追溯至 `2d9b920cf`），非本轮引入。

附带发现（与本轮无关，备查）：`TrafficStatHelper` 的 `totalTraffic` 读取 `/proc/uid_stat/$uid/tcp_snd`
与 `tcp_rcv`，该路径自 Android 8 起已被移除，两次 `RandomAccessFile` 恒落入 `runCatching` 的
`getOrDefault(0)`，属死代码。

另：`10-configs/detekt.yml:482` 的 `UnsafeCallOnNullableType` 为 `active: true`，配合 `maxIssues: 0`，
按理应拦下主源码中的 `!!`；但仓库无 detekt baseline 文件、相关文件也无 `@Suppress`，而基线那 5 处一直
通过。推断原因是 `build.gradle.kts:413` 的 `tasks.withType<Detekt>` 未配置 `classpath`，detekt 在无类型
解析模式下运行，该规则不触发（此为推断，本机无法执行 detekt 验证）。实际效果是：项目的「禁用 `!!`」
目前只由 `.claude/rules/kotlin/coding-style.md` 的约定约束，detekt 并未真正拦截。是否给 Detekt 任务补
`classpath` 使该规则生效，为独立决定。

## 16. 第九轮：G11 与 G13 的实施（Claude，2026-10-21）

### 16.1 范围

实施 §11.2 的 G11（流量监控跨线程发布缺可见性保证 + 检查后动作竞态）与 G13（回调在持锁状态下派发
导致的死锁）。提交 `b0c417d95`（实施）、`f6cbb985e`（第一轮审查修正）及后续 LOW 修正。
**未执行任何 Gradle 命令**，编译、detekt、ktlint、单测、真机一律未验证。

### 16.2 相对方案的偏离：G13 多引入一把派发锁

方案是「锁内算结果、出锁后直接 `if (isCurrentMonitor(...)) callback.invoke(result)`」。实施时额外引入
实例级的 `resultDispatchLock` 包住派发。

原因是方案只计了「可能收到一个尾随回调」这一项代价，漏算了并发：
`HandlerThreadNetworkMonitorScheduler.close()` 只 `quitSafely()` 不 `join()`，旧会话的迭代可能存活到新
会话开始。改之前 `monitorLifecycleLock` 顺带把回调派发与会话替换串行化了，使用方回调永不被两个线程
同时进入；按方案直接出锁派发会丢掉这个保证，等于把线程安全负担推给每个使用方。

对抗性审查确认该并发场景真实可达，并给出了两条比上述更强的保留理由：

1. 没有这把锁，`stopMonitor()` 返回后可能飞 **2 个甚至更多**回调，新写的 KDoc「最多一个尾随结果」
   就是假命题。
2. 没有这把锁，旧会话回调与新会话回调之间**没有 happens-before 边**，使用方在回调里改普通字段会读到
   脏值；有锁才使「调用方无需自己加锁」成立。

同时确认「把 `close()` 改成 join」不是替代方案：`releaseMonitorScheduler()` 在 `monitorLifecycleLock`
内被调用，join 一个正在跑阻塞回调的线程，等于把被修的那个 ANR 原样搬回 `stopMonitor()`。故
「加第二把锁 or 放弃不重叠保证」是真二选一。

锁序：仅在未持 `monitorLifecycleLock` 时获取 `resultDispatchLock`。反向边只存在于「同步执行任务的
scheduler」这一假设实现上（`startMonitor` 持生命周期锁时调 `scheduler.post`），该要求已写入
`NetworkMonitorScheduler.post` 的 KDoc；生产实现与测试替身均为异步，当前不可达。

### 16.3 两轮审查修正的问题

第一轮（两个独立审查者，分别审 G11 与 G13）判 DEFECTIVE，核心并发逻辑未找到反例，修正项：

| 级别 | 问题 | 处理 |
|------|------|------|
| MEDIUM | `BaseActivity` 的回调未守卫 teardown：`runOnUiThread` 是投递不是执行，尾随结果可在 `onDestroy` 之后到达主线程，子类触 View 即崩 | 加 `if (!isDestroyed && !isFinishing)` 守卫（在 lambda 内、主线程求值） |
| MEDIUM | 「先发布后启动」使 `startMonitor()` 抛异常时字段永久卡在已死实例上（旧码 `.also{}` 先于赋值求值，抛了字段保持 `null`）；且 `InternetUtil.getIpsByHost(host, callback)` 是裸 `thread{}`、**无 try/catch**，异常会成为未捕获异常杀进程 | `try/catch (Exception)`，失败时记 error、释放槽位、停实例，不重抛 |
| MEDIUM | `startMonitor` KDoc 写「blocking ... only delays **this session's** subsequent results」，与新引入的实例级派发锁自相矛盾（该句写于加锁之前） | 改为「会拖住本实例之后的每个结果，包括后续 `startMonitor()` 创建的会话」 |
| MEDIUM | CHANGELOG 缺条目 | 「变更」加 `stopMonitor()` 契约削弱（破坏性、加粗），「修复」加死锁与漏停/泄漏两条 |
| MEDIUM | 新契约零测试覆盖 | 新增 3 条 `NetworkMonitor` 用例 |
| LOW | `!published` 是唯一无日志的早退路径 | 补 `log.w`，并区分「teardown 作废」与「另一个 start 占了槽位」，带上 generation |
| LOW | 注释「Creating a NetworkMonitor (which starts a HandlerThread) now would leak it」与事实相反 | 改为 "starts no thread"；补偿路径正依赖「构造不起线程」这一相反事实 |
| LOW | `stopMonitor` KDoc 遗漏：串行化只在实例内成立；未说明尾随回调跑在已退休线程上 | 两处均补 |
| LOW | 派发处注释 because 从句因果错位 | 拆句 |
| LOW | R→L 锁序不变量仅靠注释 | 写入 `NetworkMonitorScheduler.post` 的 KDoc |

第二轮复核判 10/11 真修好、**无新引入的正确性缺陷**，并验证三条新测试**均非空通过**、在旧码上确实
会红。其余 LOW 已全部处理：`InterruptedException` 恢复中断位；补偿分支补专属日志；测试 3 的
`Thread.yield()` 忙等改 `Thread.sleep(1)`；测试 2 改名（原名 `at most one` 言过其实）；CHANGELOG 的
「相互有序」降级为「互斥 + 前次写对后次可见」（派发锁是非公平 monitor，不保证 FIFO），并补记
`onStop` 泄漏警告的删除。

**一条审查结论未被接受**：第二轮称测试 3 的注释「两个线程同时在回调内」因果错误。该注释的反事实
对象是「去掉这把派发锁」（派发已移出生命周期锁、只是不串行化），那种情况下主线程的 `startMonitor()`
不被阻塞，第二个线程确实会立刻进回调。审查者分析的是 G13 修复**前**的旧码，那里主线程卡在生命周期
锁上，`maxInFlight == 2` 才是「第一个线程在 decrement 前抛错死掉」的假象。注释无误，但措辞易误读，
已把两个反事实都写明。

**F-1（liveness）决定不改**：DNS1 读到旧 generation 后被抢占、占住槽位又自行释放，期间一个合法的
`onResume` 重启请求会 CAS 失败被丢弃。窗口要求 pause/resume 恰好夹在 generation 读与 CAS 之间、且第二
次查找恰好落在释放窗口内，量级个位数毫秒；后果良性自愈（两个 monitor 最终都被停、无泄漏无崩溃，
下次 `startTrafficNetwork()` 必成功），丢的只是一轮诊断浮层。结构性修复需引入带 generation 的 holder
加重试循环，或在主线程发布占位符——后者会破坏 `networkMonitor.get() != null` 这个被公开入口与
`stopTrafficMonitor()` 共同依赖的「已存在」守卫。净负收益。已补日志使其在 bug report 中可见。

### 16.4 新增测试

`androidbase/src/test/.../network/NetworkMonitorLifecycleTest.kt` 新增 3 条（已验证在旧码上会红）：

| 用例 | 旧码失败点 |
|------|-----------|
| `stopMonitor returns while a consumer callback is blocked` | 旧码 `stopper` 阻塞在生命周期锁上 → `join` 超时 → `assertFalse(isAlive)` 红 |
| `an in-flight result completes after stop and nothing follows it` | 旧码回调 `await` 超时抛 `AssertionError` → `failures` 非空 → 红 |
| `callbacks of one instance never overlap across sessions` | 旧码第二线程非 `BLOCKED`（主线程被锁住、它跑完变 `TERMINATED`）→ 红 |

`androidbase/src/test/.../framework/BaseActivityTrafficMonitorTest.kt` 为新文件，5 条用例覆盖 G11 侧。
关键手法：用 mockk 捕获 `InternetUtil.getIpsByHost` 的回调**而不执行它**，使 DNS 时序完全由测试驱动、
不依赖真实线程竞争；`NetworkMonitor` 用 `mockkConstructor` 拦截，断言 `startMonitor`/`stopMonitor` 的
调用次数。其中第 1 条是**反向对照**（存活时必须真的发布并启动），没有它，另外几条在「永不发布」的
坏实现下也会全绿。

5 条中 3 条用 `buildActivity(...).get()` 绕过 `onCreate`，只有涉及 `onDestroy` 的 2 条走完整生命周期；
后者需要 AppCompat 主题，已在测试 Activity 的 `onCreateBeginning()`（基类在 `super.onCreate()` 之前
调用）里 `setTheme(Theme_AppCompat)` 解决。

**这 5 条用例未执行过**。静态核对已完成：行宽 ≤100 字符、大括号与圆括号配平、无失效 import、无 `!!`、
被测 API 签名逐一比对源码、`capture` 为 `every{}` 接收者成员故无需 import、同文件多顶层类有三处既有
先例（ktlint filename 规则不适用）、该目录在 `src/test/java` 下不属 detekt 扫描范围、AGP 的
`checkTestSources` 未开启故 lint 不分析测试源。运行期风险集中在 Robolectric 对未在 manifest 声明的
Activity 的处理与 `mockkConstructor`，须由能执行 Gradle 的一方验证。

### 16.5 §15.2.2 的严重度下调

§15.2.2（G9 的会话回调把 `monitorLifecycleLock` 暴露给 ConnectivityManager 回调线程）原定 MEDIUM，
理由是与 G13 叠加会让 `ConnectivityThread` 第三条线程一并冻死。G13 实施后生命周期锁内只剩结果计算
（`TrafficStats` 计数器读取，有界），不再包含使用方回调，该叠加路径已不存在。**降为 LOW**：会话回调
仍取该锁，在锁被短暂持有期间会让 `ConnectivityThread` 等待，仅此而已。方案（改为无锁的会话校验）
仍有效，优先级降低。

## 17. 第十轮：G11 停止栅栏补齐（Codex，2026-10-22）

### 17.1 复核结论与修复

`fec53929a` 在 `BaseActivityTrafficMonitorTest` 中新增了 5 个用例，覆盖存活时启动、明确 stop 后的
迟到 DNS 回调、启动异常回滚、`onDestroy` 前后的基础握手，但没有覆盖「引用已发布、`startMonitor()`
尚未进入」这个关键窗口。生产代码仍允许 `stopTrafficMonitor()` 先取走并停止尚未启动完成的实例、
然后返回；DNS 线程随后继续启动资源，只能依赖启动后的 generation 复查再停一次。

最终修复在 `BaseActivity` 增加 `trafficMonitorLock`：

- `stopTrafficMonitor()` 在锁内推进 generation、取走引用并调用 `stopMonitor()`。
- DNS 回调的最终 generation/存活/占位检查、引用发布、`startMonitor()` 和失败回滚均在同一锁内。
- 构造 `NetworkMonitor` 与 DNS 查询仍在锁外，不扩大慢操作临界区。
- `InterruptedException` 单独捕获并恢复中断位；其他启动异常继续清空持有者、停止实例并记录日志。

这使 `stopTrafficMonitor()` 成为严格的启动停止栅栏：start 先进入临界区时，stop 等待并在返回前完成停止；
stop 先进入时，迟到 DNS 回调不会再调用 `startMonitor()`。

### 17.2 测试与验证

新增 `stop waits for an in-progress monitor start before returning` 用例：mock `startMonitor()`
进入后用 latch 暂停，另一线程调用 `stopTrafficMonitor()`，断言它在启动被释放前不返回；修复后
stopper 稳定阻塞，只在释放启动线程后才停止实例并返回。

**该用例钉住的是 `trafficMonitorLock` 把拆除与「发布 + 启动」握手串行化，而不是复现修复前的缺陷。**
修复前的代码在这个位置本来就会阻塞：发布早于 `startMonitor()`，而 `stopMonitor()` 取的
`monitorLifecycleLock` 正是 `startMonitor()` 全程持有的那一把。用例之所以能在旧代码上变红，是因为
`stopMonitor()` 被 stub 成 `just Runs` 而不取任何锁 —— 红来自 stub，不来自缺陷。真正的缺陷窗口在
「已发布、尚未进入 `startMonitor()`」那几条指令上，而 `BaseActivity` 在发布（修复前是
`compareAndSet(null, monitor)`）与
`startMonitor()` 之间没有可注入的测试缝（`NetworkMonitor` 的 `beforeResultDispatch` 在此无对应物），
无法确定性复现。经作者裁定（2026-10-22）：不为此往生产代码加测试缝，也不加非确定性的竞态用例；
本轮修复的正当性依据 §17.1 的交错论证，不依据一个红测试。用例注释已同步说明这一点。

`fec53929a` 的新测试首次本机编译暴露了两个机械问题，均已修正：一是错误导入不存在的顶层
`io.mockk.anyConstructed`（它只是 `MockKMatcherScope` 的成员，顶层无此函数，属**编译错误**而非风格
问题）；二是 `getViewBinding()` 的表达式体换行违反 ktlint 的 `function-signature` 规则
（`functionBodyExpressionWrapping` 为 `default`，单行形式 98 列 ≤ `max_line_length = 100`，故必须与
签名同行）。该文件位于 `src/test/java`，不在 detekt 的 `source.from` 五项（`buildSrc`、根
`build.gradle.kts`、`settings.gradle.kts`、`src/main/kotlin`、`src/test/kotlin`）范围内，detekt
未参与这两处判定；`.editorconfig` 的 `ktlint_standard_indent` 为
`disabled`，故与缩进规则无关。

最终执行：

```bash
./gradlew :androidbase:testDebugUnitTest :androidbase:ktlintCheck :androidbase:detekt \
  --rerun-tasks --console=plain
```

结果：`BUILD SUCCESSFUL in 32s`，`162 actionable tasks: 162 executed`。现有 5 个基础握手用例与新增停止栅栏用例均
实际执行。本轮未执行真机验证；该竞态通过可控 Robolectric/MockK 线程交错回归覆盖。

## 18. 跨分支待办：Detekt 未启用类型解析（记录于 2026-10-22）

**本条不在本分支修复，日后另起分支单独处理。** 记录在此以免再次被当成「已被门禁覆盖」。

- 位置：`build.gradle.kts:413-429`（`tasks.withType<Detekt>().configureEach { }`）。
- 缺陷：该块只设 `jvmTarget`（上一行注释还写着「It is used for type resolution」）与 `reports`，
  **从未设置 `classpath`**。detekt 1.23.8 的类型解析需要 source 与 `classpath` 两者齐备；
  `detekt-core` 的 `BindingContextKt.generateBindingContext` 在 `classpath.isEmpty()` 时直接
  `return BindingContext.EMPTY`（字节码 offset 25/30/33）。随后 `Analyzer` 把标注
  `@RequiresTypeResolution` 的规则**整条跳过**（offset 91 的 `instanceof RequiresTypeResolution`），
  提示只走 `warnAboutEnabledRequiresTypeResolutionRules()` 内部的 `ProcessingSettings.debug{}`，
  默认看不见。仓库无 baseline 文件，`maxIssues = 0`（`detekt.yml:2`），detekt 的 `ignoreFailures`
  走默认值 `false`（仓库未显式配置；`build.gradle.kts:158` 那处 `ignoreFailures.set(false)`
  属 ktlint 块）。门禁看上去极严，实际一整类规则是哑的 —— 这比一个诚实的小规则集更危险，
  因为 `detekt.yml` 高估了真正被执行的范围。
- 注意**两种**失效方式，不要一概而论：
  - 标注了 `@RequiresTypeResolution` 的规则被整条跳过 —— **静默失效**。
  - 未标注却自行查询 binding context 的规则**仍会执行**，其行为取决于自己怎么处理取不到类型的
    情形：可能静默失效，也可能**失败开放**而照常上报。
- 已确认的受害者（静默失效）：
  - `UnsafeCallOnNullableType`（`10-configs/detekt.yml:482-484`，`active: true`）—— 即 `!!` 禁令，
    未被执行：类上带 `@RequiresTypeResolution`，被 `Analyzer` 跳过；即便执行，其
    `visitPostfixExpression` 在类型取不到时也走失败关闭分支（`40: ifnull 49` -> `50: aconst_null`
    -> `54: if_acmpne 95`，直接返回不上报）。这解释了 `!!` 为何能长期留在仓库中而不使构建失败：
    全仓 `src/main` 下有 56 个 `.kt` 文件出现 `!!` 字面，其中 12 个只出现在注释里，**实际使用该运算
    符的是 44 个**，例如 `ScreenRecordMediaCodecStrategy.kt` 的 4 处
    （:286/:381/:480/:509，见 §15.5）。
    另注意该规则只在 `!!` 作用于**可空类型**时上报，故并非每一处 `!!` 都由它负责。
    `Screenshot2H26xStrategy.kt` 不可用作例子：它在 master 上的 2 处 `!!` 都位于**被注释掉的代码**
    （`:96`、`:196`），规则本就不会上报，且该文件现为 0 处。
- 已确认的反例（失败开放，与直觉相反）：
  - `InstanceOfCheckForException`（`10-configs/detekt.yml:242-244`，`active: true`）**不是**受害者。
    反汇编 `detekt-rules-exceptions-1.23.8.jar`：类的 `RuntimeVisibleAnnotations` 只有
    `kotlin.Metadata`，**没有** `@RequiresTypeResolution`（上游只有源码级的
    `@Suppress("ViolatesTypeResolutionRequirements")`），故 `Analyzer` 不跳过它。取 binding context
    的是 `isCheckForSubTypeOf`（**不是** `visitCatchSection`，后者字节码只到 offset 78）：
    offset 150/172 调 `getBindingContext()`、153/175 取 `BindingContext.TYPE`，而两个类型任一为 null
    即 `197/202 ifnull -> 215 iconst_1`，**返回 `true`**；`visitCatchSection$lambda$0` 的
    `12: ifeq 58` 只在 false 时跳过上报。即**无类型解析时失败开放、照常报错**。
    因此 `catch (error: Exception) { ... if (error is InterruptedException) ... }` 在**当前配置下就
    已经违规**，`maxIssues: 0` 会使构建失败。该合并写法由 `fec53929a` 引入（`f6cbb985e` 及更早都
    没有），`6efc3dd47` 把它拆成 `catch (error: InterruptedException)` + `catch (error: Exception)`
    —— 最可能的因果就是被这条规则逼出来的（因果本身未验证：远端无法跑 Gradle，`6efc3dd47` 的提交
    正文为空，只有 subject）。补上 `classpath` 后仍然违规（`InterruptedException` 是
    `Exception` 的子类型），
    故现有拆分形式**必须保留**；若要消除重复，只能抽一个两个 catch 共用的私有 helper，不能合并。
  - 推论：`detekt.yml` 里需类型解析的其余规则必须逐条按上述两种方式判定，不能假定一律哑掉。
    本轮只验证了上述两条。
- 建议做法（中间路线）：同时加上 `classpath` 并生成 baseline，把既有违规祖父条款化，只让**新增**
  违规失败。这样一次提交即可落地，门禁从此真正工作。（Android 模块下生成 baseline 的任务按
  variant 分裂，具体任务名需本机 `./gradlew tasks` 确认。未验证。）
- 代价：开启后必然在 36 个模块里涌出一批新违规（以那 44 个文件里作用于可空类型的 `!!` 为主），
  而 `maxIssues = 0` 不允许分批落地；detekt 还会因此依赖编译产物而明显变慢。baseline 文件较大，
  且会改动每个模块的报告产物，故**不要**混进审查分支。
- 验证：`./gradlew detekt --rerun-tasks` 在加 `classpath` 前后对比报告条目数。未验证。

## 19. 第十一轮：拉取 `eefaae161` 后复核与迟到失败回调修复（Codex，2026-10-22）

### 19.1 复核范围与结论

本轮逐项复核 `6efc3dd47..eefaae161` 的 5 个文件，并对照 `NetworkMonitor`、`InternetUtil`、
Toast 实现与 Gradle 配置。`BaseActivity` 新 KDoc、停止栅栏实现、测试说明修正和 §18 的 Detekt
结论总体成立；通过临时只读诊断 action 实测 `:androidbase:detekt` 的 `classpath` 文件数确为 0。

发现并修复两类明确问题：

- G11 主条目仍声称停止栅栏用例能在旧生产交错上确定性变红，G12 又仍标为「KDoc 未实施」，均与
  §17.2 和 `eefaae161` 的当前源码矛盾；已在原条目就地纠正。
- `BaseActivity` 与 demo `NetworkMonitorActivity` 都先处理空 DNS 结果、再检查 generation 与
  Activity 存活状态。于是 DNS 失败若晚于 `onDestroy()` 返回，虽然不会启动监控，仍会从已销毁页面
  显示迟到 Toast。第一版修复只把失效检查移到 `toast()` 调用之前；本轮再次复核 `ToastExt` 后确认，
  后台线程调用 `toast()` 还会把真正的显示动作投递到主线程，因此检查与 UI 副作用之间仍有窗口。
  最终让空结果分支把处理整体投递到主线程，并在同一个主线程任务内检查 generation 与 Activity
  生命周期后才调用 `toast()`，使它与 `onDestroy()` 串行。锁内最终复查继续负责监控发布/启动竞态。

### 19.2 TDD 与验证

初版新增两个 Robolectric 竞态用例，分别钉住 `BaseActivity` 与 demo。它们只覆盖“先销毁 Activity、再交付
空 DNS 结果”，能证明明显的迟到回调被丢弃，但没有覆盖 Toast 扩展的主线程二次投递。复审后用例改为
暂停主 Looper，从工作线程交付空结果，等 DNS 回调返回后销毁 Activity，最后再清空主队列；这能稳定
复现“后台检查通过、Toast 尚未真正执行”窗口。修复前两个用例分别在
`BaseActivityTrafficMonitorTest.kt:150` 与 `NetworkMonitorActivityTest.kt:68` 的
`verify(exactly = 0)` 按预期失败；把生命周期最终检查移进主线程任务后转绿。复审又为两处各补一条
“Activity 存活时仍显示 Toast”的正向控制，避免直接删除错误提示也让负向测试假绿；Demo 测试同时在
`tearDown()` 停止全局 Koin，保证两个 Activity 用例可连续隔离执行。定向重跑 10 条用例全部通过。

最终工作树执行：

```bash
./gradlew :androidbase:testDebugUnitTest :androidbase:ktlintCheck :androidbase:detekt \
  :demo:testDevDebugUnitTest :demo:detekt :demo:ktlintTestSourceSetCheck \
  :demo:assembleDevDebug --rerun-tasks --console=plain
```

复审修正后的最终结果：`BUILD SUCCESSFUL in 1m 32s`，`635 actionable tasks: 635 executed`。其中
`BaseActivityTrafficMonitorTest` 8 条、`NetworkMonitorActivityTest` 2 条，均为 0 failure / 0 error。
`androidbase` 的完整 ktlint/detekt、demo detekt、demo 测试源 ktlint、两模块 JVM 单测及
`assembleDevDebug` 均通过。

Demo 主源码 ktlint 另行重跑后仍只报告
`demo/.../examples/audio/receiver/AudioReceiver.kt:165:54` 的既有
`function-expression-body` 违规；该文件由 `1b4f225627` 引入，本次 Claude 提交
`6efc3dd47..eefaae161` 未触及，因此不混入本轮修复。测试编译还保留
`SuspendTeardownGateTest` 的既有 `ExperimentalCoroutinesApi` opt-in 警告。本轮未执行真机验证。
