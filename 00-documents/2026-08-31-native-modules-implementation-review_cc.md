# Native 模块实现复审与整改闭环（2026-08-31，_cc）

> 本文最初由 Claude Code 对提交 `4a1d99d25 fix(native): harden media modules and packaging`
> 进行独立复审。维护者将原文复制到 `review/native-modules` 分支后，Codex 又以当前源码、仓库内
> FFmpeg 8.1.1 头文件、真实 Gradle/NDK 构建和 LFS/ELF 结果逐条核对。本版保留原复审中成立的结论，
> 修正不准确的 FFmpeg `EAGAIN` 分析、验证证据和严重度，并记录后续代码整改状态。

## 1. 审查范围与结论边界

复审基线提交共 88 个文件、`+3241/-2079`，覆盖统一方案
`2026-08-27-native-modules-remediation-plan-zh.md` 的 A～G 批次：

- yuv、lib-image、jpeg 的 JNI/Native 内存安全和错误路径；
- ffmpeg-sdk 中 ADPCM encoder/decoder、H.264/HEVC decoder 的输入、资源所有权和生命周期；
- ffmpeg-javacpp 的 frame/packet 生命周期与声道输出；
- 三个 FFmpeg wrapper 的 Kotlin 生命周期、预编译 `.so`、三选一 capability 和 ABI；
- demo 的异步释放和 Native 对象使用方式；
- README、CHANGELOG、统一方案和发布验证链路。

当前可以得出的结论是：

1. 对基线提交进行静态复审时，没有发现新的 CRITICAL/HIGH 内存安全问题；原统一方案中的
   CRITICAL/HIGH 源码修复均能在当前代码中定位到对应实现。
2. 原复审新发现的两个库问题、两个 demo 问题和列出的 LOW 清理项均已整改；后续复审中成立且不涉及
   公开语义选择的 H.264/HEVC JNI 与 drain 问题也已修复。
3. 不能写成“已证明没有内存安全回归”。真机、ASan/HWASan、16KB 设备加载、B 帧真实输出、
   ADPCM 回调异常和长时间内存曲线仍是发布门禁。
4. 本文记录的是当前工作区状态；后续提交时仍须重新核对 commit、LFS 对象和远端状态。

## 2. 原复审结论的核对结果

| 模块 | 核对结果 | 当前状态 |
|---|---|---|
| yuv | 原 7 项修复成立；另有跨文件尺寸类型可维护性问题 | 已把 `YuvConvert.cpp` plane offset/size 中间值改为 64 位 |
| lib-image | 原 10 项修复成立 | JNI 注册函数改为内部链接；公开 handle 仍保持删除状态 |
| jpeg | 原 8 项修复成立 | `FindClass` 失败返回 `JNI_ERR` 前清理 pending exception |
| adpcm native | 原 9 项修复成立；回调异常会继续消费批次的问题成立 | 已让 callback 返回继续/中止信号，异常后立即停止外层批次 |
| h264/hevc native | 原 13 项内存/生命周期修复成立；send/receive 状态机不完整 | 已实现送包重试、循环收帧、多帧返回和 EOS drain |
| Kotlin/demo | 生命周期修复成立；两项 demo 风险成立但严重度不同 | 已补取消重抛、`use`/完整 chunk 校验和 EOS drain |

### 2.1 仍然成立的关键实现结论

- **yuv**：RGB24 stride 使用 `width * 3`；NV12 旋转 90/270 度后使用旋转宽度；JNI 入口先校验
  再分配/取数组；libyuv 非零状态通过异常上报；完整 plane API 支持 direct、heap、只读 buffer、
  非零 position 和不等 U/V pixel stride。
- **lib-image**：crop/scale 使用受检尺寸；1×N/N×1 双线性缩放不再产生负索引；Native handle 为
  私有 `Long`；close 幂等且先清空 handle；Native 分配和 stride 拷贝均有检查。
- **jpeg**：只接受 RGBA_8888；检查 getInfo/lock/尺寸/分配；libjpeg longjmp 状态位于堆上；所有路径
  统一释放 compressor、文件、行缓冲、UTF 字符串和 Bitmap lock；失败删除不完整文件。
- **adpcm**：输入 packet 带 FFmpeg padding；packet/frame/context 在成功和失败路径均释放；输入必须是
  完整 codec frame；mono/stereo 参数双层校验；Kotlin 对象以私有 handle 和实例锁管理生命周期。
- **ffmpeg-javacpp**：成功路径 unref frame；mono 不访问第二声道；packed/planar PCM 长度按
  `nb_samples * bytes_per_sample` 计算；close 不再重复 free。
- **打包**：三个预编译 wrapper 仍严格三选一；`ffmpeg-javacpp` 只声明 `android-arm64` classifier；
  wrapper 继续发布四 ABI。

## 3. 对原复审文档的纠正

### 3.1 JPEG “实编译 3 ABI + `-Wconversion`”不能作为证据

原文中的该表述已删除，原因如下：

- 当前 JPEG Gradle/CMake 编译参数包含 `-Wall`，但不包含 `-Wconversion`；
- 当前模块明确构建 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 四 ABI；
- 原复审没有保存可复现的三 ABI `-Wconversion` 命令、日志或产物；
- 本轮 Codex 已用 JDK 17 执行 `:jpeg:assembleDebug --rerun-tasks`，四 ABI 均通过，但这只能作为
  本轮独立验证，不能反向补成 Claude Code 的构建证据。

### 3.2 H.264/HEVC 的两类 `EAGAIN` 必须分开

FFmpeg 8.1.1 的 send/receive 契约是：

- `avcodec_send_packet()` 返回 `AVERROR(EAGAIN)`：当前 packet **尚未被接受**。调用方必须先读取
  已有输出，再重送同一个 packet；旧代码直接 unref 会真正丢掉输入。
- `avcodec_receive_frame()` 返回 `AVERROR(EAGAIN)`：已接受的输入当前没有可取输出，需要继续送入
  新 packet；这不是硬失败，也不表示刚送入的 packet 已丢失。
- 一个 packet 可以产生 0、1 或多帧；不能固定为“一次 send + 一次 receive”。
- B 帧等延迟输出必须在 EOS 时发送 null packet 并 receive 到 `AVERROR_EOF`；旧 API 没有 drain，
  流尾延迟帧无法完整取回。

因此，“区分 EAGAIN 或文档化再次调用”不足以修复。当前实现采用完整状态机：send 被拒绝时先排空并
重送同包；send 成功后循环 receive 到 EAGAIN；硬错误抛 `IllegalStateException`；EOS 通过 drain
进入不可继续送包的状态。

### 3.3 `CancellationException` 应定为 LOW

两处局部 `try` 内没有挂起点，而且 `ensureActive()` 位于该 catch 外，所以它不是当前可稳定触发的
MEDIUM 故障。它仍是未来维护陷阱，现已显式先捕获并重新抛出 `CancellationException`。

### 3.4 LFS 状态应描述真实对象而不是只描述指针

当前三个 wrapper 的 68 个 `.so` 工作树实体均存在，`git lfs fsck` 通过。正确结论是“LFS 对象完整、
ELF 静态检查通过，但运行功能仍需设备验证”。LFS 指针格式正确本身不能证明二进制包含预期实现，也不能
代替 codec 能力和真机输出检查。

## 4. 后续问题的代码整改

### 4.1 ADPCM encoder 回调异常立即中止当前批次

原问题：JNI callback 只设置 `callback_failed`，C++ `do_encode()` 和外层 frame 循环无法感知，因而
继续消费剩余 PCM、推进 codec 状态并丢弃输出。

当前修复：

- `EncoderCallback` 从 `void` 返回改为 `bool`；
- Java callback 成功返回 `true`，存在 pending exception、数组分配失败或回调抛异常时返回 `false`；
- `do_encode()` 在释放当前 packet 后返回 `AVERROR_EXIT`；
- 外层 `encode()` 立即停止，不再消费同一批次剩余 frame；
- JNI 保留原 Java 异常，不用第二个异常覆盖回调根因。

### 4.2 H.264/HEVC 完整输出 API

公开 Kotlin API保持兼容，并新增完整输出能力：

- `decode(packet)`：保留原签名，返回当前最早可用的一帧；单 packet 的额外输出进入实例内队列，
  后续 `decode()` 或 `drain()` 按顺序返回。
- `decodeFrames(packet)`：返回历史 pending 输出与当前 packet 处理后所有可用帧，适合需要完整消费的调用方。
- `drain()`：发送 EOS，返回 Kotlin pending 帧和 codec 内部延迟帧；重复调用返回空列表且保持幂等。
- drain 开始后拒绝新输入；Kotlin 单独记录 drain 是否完成，因此 Native drain 中途抛异常时也不会错误地
  恢复送包，只允许重试 drain；Native drain 成功后才清空 Kotlin pending 队列，因此异常重试不会丢失
  已缓存帧；release 会清空 pending 队列并保持幂等。

Native 层新增 `nativeDecodeFrames`/`nativeDrain`，每次 receive 成功都会先复制为独立 Java
`DecodedVideoFrame`，再 unref FFmpeg frame，因此不会把可变 AVFrame 内存暴露给 Kotlin。

### 4.3 Demo 生命周期与输入完整性

- H.264/H.265 demo 对 `CancellationException` 显式重抛；自然读到 EOF 时调用 `drain()` 并渲染
  延迟输出；用户主动关闭或协程取消时不做无意义 drain。
- ADPCM encode/decode 对 Native codec、输入流和输出流使用 `use`；任何 callback、文件或 decode
  异常都能释放资源。
- ADPCM 文件长度必须为完整 chunk 倍数；不足 chunk 明确抛参数错误，不再依靠
  `copyOfRange()` 的越界异常间接失败。

### 4.4 LOW 项清理

- 删除 decoder/encoder 两个未参与 CMake 的旧 `*_all_in_one_file.cpp.bak`，避免误复活旧实现；
- `native_adpcm_ima_qt_decoder.h`、`native_adpcm_ima_qt_encoder.h` 声明与实际 `Native*` 注册函数一致；
- `YuvConvert.cpp` 的 plane size/offset 中间值统一用 `int64_t`，不再依赖 `jint` 乘法；
- JPEG `JNI_OnLoad` 的 `FindClass` 失败路径清除 pending exception 后返回 `JNI_ERR`；
- lib-image 仅经 `RegisterNatives` 使用的函数改为 `static`；
- ADPCM decoder 的输出 `NewByteArray` 失败路径显式保持/补充 OOM，并在退出前释放 Native PCM。

### 4.5 H.264/HEVC 后续复审整改

Claude Code 对 `50c9949ed` 的后续复审经源码和 JNI 契约复核后，实际处理如下：

- `NativeInit()` 的两次 `NewStringUTF()` 分别立即检查 pending exception，第一次失败后不再调用第二次；
- `drain()` 改为 Native 成功后才合并并清空 Kotlin pending 队列，异常时已缓存帧保持可重试；
- `JNI_OnLoad()` 缓存 decoder、返回类型和 `ArrayList` 的 global class ref、构造器、`add()` 与 handle 字段，
  解码热路径不再逐帧 `FindClass()`/`GetMethodID()`；`JNI_OnUnload()` 负责释放 global ref；
- send 返回 `AVERROR_EOF` 时使用明确的“decoder 已到输入末尾”错误信息。

原复审中另外两项建议未采用：`JNI_OnLoad()` 失败返回 `JNI_ERR` 前不强制清除类/方法查找产生的原异常，
这与 Android NDK 示例一致；发生 pending exception 后也不能再次调用 `SetLongField()` 尝试把 handle 写 0。
完整理由见 `2026-08-31-native-h264-drain-followup-fixes_cc.md`。

仍有两项公开语义需要维护者决定：Native 在同一次调用中先产生部分帧再发生硬错误时，是继续采用强失败
语义还是新增“帧 + 错误”结果类型；兼容 API `decode()` 的 pending 队列是否增加上限及采用何种溢出策略。

### 4.6 真机暴露的 Demo Annex-B packet 边界错误

P3H 真机回归最初在 `FFMpegH264Activity.onCreate()` 抛出 `Unable to send video packet`。Native 返回码
`-1094995529` 为 `AVERROR_INVALIDDATA`。核对样本后确认根因不在新的 send/receive 状态机，而在旧 Demo：

- 固定按 NAL 序号解释参数集，实际把 H.264 的 SEI 当 SPS、SPS 当 PPS；H.265 也被开头两个 prefix SEI
  整体错位；
- 只识别 4 字节 start code，漏掉 H.264 样本中的 3 字节 start code；
- 初始化后把 CSD 当普通视频 packet 再发送；
- 把重复 PPS/VPS/SPS/SEI 单独送入 `avcodec_send_packet()`，没有恢复它们与后续 IDR 共同组成的 packet
  边界；
- 旧 Native 吞掉 send 硬错误，导致这些输入错误此前没有向上暴露。

当前 Demo 新增顺序式 Annex-B reader，按 NAL type 获取实际参数集，同时支持 3/4 字节 start code；CSD
只用于 decoder 初始化；后续将 VCL 图像前的非图像 NAL 与该图像合并为一个 packet，再通过
`decodeFrames()` 完整消费输出，自然 EOF 调用 `drain()`。Activity 初始化失败会记录异常、关闭资源并
结束页面，不再形成未捕获启动异常。详细样本 offset、两阶段定位和验证证据见
`2026-08-31-native-h264-drain-followup-fixes_cc.md` 第 6 节。

### 4.7 H.264 初始化尺寸为 0 导致 OpenGL 黑屏

packet 修复后的第一轮 P3H 检查只确认 H.264/H.265 日志持续输出 1920×800 帧，没有检查 Surface 的实际
画面。用户随后发现 H.264 页面仍为黑色背景，而 H.265 正常。对比日志确认 H.264 在首帧之前的
`DecodeVideoInfo` 为 `0×0`、像素格式 `-1`；H.265 初始化时已经是 `1920×800 yuv420p`。

重构前的 H.264 Demo 硬编码渲染尺寸 1920×800；删除硬编码后，代码直接把 H.264 初始化返回的 0×0 交给
OpenGL。`GLRenderer` 忽略无效尺寸，YUV 缓冲保持 0 容量，即使后续帧已经成功解码，`onDrawFrame()` 也
不会上传纹理或绘制。这解释了“解码日志正常、Surface 持续 requestRender、实际仍黑屏”的组合现象。

当前 H.264/H.265 Demo 只在初始化尺寸有效时预配置渲染器，并在每个实际解码帧到达时按帧宽高完成首次
配置或处理分辨率变化。`GLRenderer.setVideoDimension()` 同时改为与喂帧、绘制共用实例锁，且宽或高任一
变化都会重建 YUV 缓冲。详细因果链与重新验证见
`2026-08-31-native-h264-drain-followup-fixes_cc.md` 第 7 节。

### 4.8 ADPCM Demo 样本尾帧不完整

P3H 点击 ADPCM Demo 的编码按钮后出现：

```text
java.lang.IllegalArgumentException: PCM input must contain a whole number of encoder frames
    at com.leovp.ffmpeg.audio.adpcm.AdpcmImaQtEncoder.nativeEncode(Native Method)
    at com.leovp.ffmpeg.audio.adpcm.AdpcmImaQtEncoder.encode(AdpcmImaQtEncoder.kt:40)
    at com.leovp.demo.basiccomponents.examples.audio.ADPCMActivity.onEncodeToADPCMClick(ADPCMActivity.kt:54)
```

这是完整帧契约正确暴露了 Demo 输入问题，并非 Native frame size 计算错误。仓库 PCM 样本大小为
1,894,464 字节；IMA-QT encoder 的 `frame_size` 为每声道 64 个 sample，当前双声道 S16 交错输入每帧需要
`64 × 2 × 2 = 256` 字节。样本包含 7,400 个完整帧后还剩 64 字节，只相当于 16 个双声道 sample，不能
直接作为一个 64-sample encoder frame 提交。旧实现会静默丢弃这 64 字节；本轮严格校验按设计拒绝输入，
但 Demo 没有同步处理尾帧。

修复保留 library 的严格输入契约，不在通用 encoder 内默认缓存或静默补齐：

1. `AdpcmImaQtEncoder` 新增公开的 `inputFrameBytes()`，从实际 Native encoder context 查询当前配置所需的
   交错 PCM 帧字节数；独立音频 wrapper 和音视频组合 wrapper 保持逐字一致；
2. Kotlin `encode()` 在进入 Native 前检查非空和 frame-byte 对齐，错误信息包含实际要求的字节数；Native
   继续保留相同防御性校验；
3. Demo 的一次性文件转换明确选择“尾部补静音”策略：按 `inputFrameBytes()` 将最后不足一帧的数据补 0，
   本样本由 1,894,464 补到 1,894,656 字节，增加 192 字节静音，不再丢弃原有 64 字节 PCM；
4. 增加 JVM 测试覆盖已对齐输入不复制、不完整输入补 0 和非法 frame size；
5. 新 JNI 方法已按四 ABI 重建，并只替换两个 wrapper 中对应的 `libadpcm-ima-qt-encoder.so`，避免改变
   两个互斥 profile 各自不同的 FFmpeg runtime 配置。

### 4.9 Audio Demo 停止 AAC 录音时出现 teardown 伪错误

P3H 点击 Audio Demo 的“Record AAC”开始录音，再次点击停止时功能正常、文件能够生成，也没有闪退，
但日志稳定出现两组错误：

```text
LEO-MicRec E AudioRecord.read error=-3
LEO-MediaCodecAsync E Input buffer callback failed: java.lang.IllegalStateException
```

这不是麦克风在录音过程中自行失效，也不是 AAC 数据编码失败，而是主动停止路径中的两个生命周期竞态：

1. `MicRecorder.stopRecord()` 先把 `stopped` 设为 `true`，再调用 `AudioRecord.stop()`。部分设备会用
   `AudioRecord.ERROR_INVALID_OPERATION(-3)` 唤醒此时阻塞在 Native 的 `read()`；原录音循环把所有负值
   都当成真实读取故障，因而误记 Error 并从录音线程再次进入失败清理。日志中的 `Recording released.`
   来自录音线程，证明本次正是该错误分支抢先取得幂等 release 权限。
2. Audio Demo 仍在调用已经弃用的非挂起 `stopRecord()`。该兼容入口只取消录音 Job，不能等待录音线程和
   encoder 完成退出；与此同时，异步 `MediaCodec.Callback` 注册在创建 encoder 的主线程 Looper 上，
   已经进入消息队列的 `onInputBufferAvailable()` 不会因为另一个线程释放 codec 而自动从队列中消失。
   迟到回调随后调用 `getInputBuffer()` 或 `queueInputBuffer()`，codec 已处于 released 状态，所以抛出
   `IllegalStateException`。异常虽被 `runCatching` 捕获而没有造成闪退，但释放过程并不干净。
3. 此前 R-4 的共享 codec 操作锁只覆盖同步 worker；`BaseMediaCodecAsynchronous` 的输入、输出回调没有
   使用该锁，也没有在 `releasing` 状态下提前退出。因此仅把日志降级或吞掉异常不能解决实际并发访问。

当前修复保持公开兼容入口不变，并在 library 与 Demo 两层闭环：

- `MicRecorder` 收到负读取状态时先检查 `stopped`。主动停止后的负值只结束循环，不再触发失败清理；
  录音期间出现的真实负错误码仍记录 Error，并继续走 `onStop(false)` 的故障释放路径。
- `BaseMediaCodecAsynchronous` 的输入和输出回调改为与 `stop()`/`flush()`/`release()` 共用
  `withCodecOperationLock`。release 若在回调执行期间开始，会等待当前 codec 操作完成；release 已开始后
  才到达的回调则在接触 codec 前退出，消除检查状态与实际调用之间的 TOCTOU 窗口。
- 输入、输出及 codec error 回调仅在非主动释放状态上报异常；输出 buffer 改在 `finally` 中归还，避免
  外部 `onOutputData()` 抛异常时占住 codec buffer。
- `AudioActivity` 在主线程先摘除当前 `MicRecorder` 引用，再由 IO scope 调用
  `stopRecordAndJoin()`，等待录音 Job 退出并走 encoder 的确定性释放入口。PCM、AAC、OPUS 三个录音按钮
  和 Activity `onStop()` 共用同一停止方法。
- 新增异步 codec JVM 并发测试：一条覆盖 release 开始后的迟到输入回调不再访问 codec；另一条用 latch
  证明 release 会等待正在执行的输入回调归还 input buffer 后才释放底层 codec。

本修复没有把所有 `IllegalStateException` 静默处理为正常情况：只有 `releasing=true` 的主动 teardown
窗口会抑制预期噪声；录音或编码运行期间的同类异常仍会保留完整日志，便于发现真实设备/codec 故障。

### 4.10 Audio Demo 的 PCM 尾包与 OPUS 自然结束处理错误

完成 AAC 停止竞态修复后，又在 P3H 上复测了 PCM 和 OPUS 的完整录制、停止与文件播放链路。停止阶段没有
再次出现 teardown 错误，但播放日志暴露出两个独立问题：

1. PCM 播放循环使用 8192 字节复用缓冲读取文件，却无条件把整个缓冲传给 `AudioPlayer.play()`。当最后一次
   `read()` 小于 8192 时，缓冲后部仍是上一次读取留下的数据，因而会重复播放旧 PCM，并使输出长度超过文件
   的真实长度。
2. Demo 的 OPUS 文件格式是重复的 `[|leo|][payload]`，最后一个音频 payload 后没有结束标记。旧播放器必须
   找到“下一个”标记才解码当前帧，因此自然 EOF 被误记成 `Read file. EOF` 和
   `Can't find start code` Error，最后一个 OPUS 音频帧也不会送入 decoder。
3. OPUS 的正常逐帧队列状态使用 Error 级别输出，造成日志噪声；输入 `RandomAccessFile` 没有在自然结束或
   `stop()` 时确定关闭；播放协程使用不可取消的 `queue.take()`，共享完成标志也不是线程安全类型。

修复方法如下：

- PCM 最后一包按 `readSize` 截取后再播放；完整 8192 字节读取仍直接复用原缓冲，不增加常规路径分配。
- 新增可单测的 `OpusFramedFileReader`。它校验当前位置的起始标记，后续找到标记时以该标记作为 payload
  末端，找不到时则把文件自然结尾作为最后一个 payload 的末端。因此 EOF 成为合法边界，不再是异常。
- OPUS 播放循环逐个提交 reader 返回的 payload，并记录已提交和已播放帧数；文件读取完成后最多等待 3 秒
  排空 decoder 输出和 PCM 队列。成功时记录 `Playback completed: submitted=N played=N`，超时才记录 Error。
- 逐帧队列状态降为 Debug 并限频到每 50 帧最多一条；输入文件在读取协程 `finally`、幂等 `stop()` 和
  同步初始化失败回滚中关闭；播放队列改用带超时的 `poll()`，完成标志和计数改为原子类型，协程取消后
  不再永久阻塞在 `take()`。
- 新增 `OpusFramedFileReaderTest`，覆盖配置帧、中间音频帧、无尾标记的最后音频帧以及起始标记缺失。

对应到代码级别，本次修改为：

- `AudioActivity.kt` 的 PCM 文件循环继续复用 8192 字节缓冲，但仅在 `readSize == 8192` 时直接传递原数组；
  短读时使用 `copyOf(readSize)` 生成精确长度的尾包。不能通过给剩余区域补 0 解决，因为那仍会人为延长
  音频；也不能把每次读取都改成复制，否则会给正常 PCM 播放增加持续分配。
- `OpusFramedFileReader.kt` 将“当前位置必须是 start code”和“payload 的结束位置”分开处理。下一 start code
  存在时返回其位置，不存在时返回 `null` 并使用 `file.length()`，从格式层面表达“这是最后一个 payload”，
  而不是依靠捕获 `EOFException` 猜测是否正常结束。
- reader 对 start code、payload 长度和位置溢出进行显式校验。位置计算没有使用 Android API 24 才可直接
  依赖的 `Math.addExact()`，而是使用手工上界检查，保持仓库 minSdk 21 契约。
- `OpusFilePlayer.kt` 只在成功解析配置并启动 decoder 后进入三个工作协程；同步初始化任一步骤失败都会
  调用幂等 `stop()`，关闭文件并释放已创建的 decoder/`AudioTrack`，避免损坏文件留下半初始化资源。
- 文件读取协程、PCM 播放协程和完成观察协程分别负责提交、播放和收尾。完成条件不再是非原子的普通
  `Boolean + queue.isEmpty()`，而是读取完成标志、提交帧计数、播放帧计数和 PCM 队列共同满足；3 秒超时
  只用于发现 decoder 没有返回全部输出，不能把丢帧静默当成正常完成。

### 4.11 主动停止期间的系统 AudioRecord 与 Codec2/FMQ 日志

最终 P3H 复测中，应用层 teardown 错误已经消失，但按进程过滤完整 logcat 后仍可看到：

```text
AudioRecord-JNI: Error -38 during AudioRecord native read
CCodecConfig: query failed ... (BAD_INDEX)
CCodecConfig: config failed => CORRUPTED
FMQ: grantorIdx must be less than 3
```

它们与此前修复的应用层错误不是同一个问题：

1. `AudioRecord-JNI -38` 与 `MicRecorder` 主动停止处于同一时间点。录音协程正阻塞在 Native `read()` 时，
   另一协程调用 `AudioRecord.stop()` 使读取立即返回负状态；Framework/JNI 在结果回到 Kotlin 之前已经输出
   自己的 Error，因此应用无法通过调整 `LogContext` 级别消除它。`MicRecorder` 仍需调用 `stop()` 来及时
   唤醒读取并保证 `stopRecordAndJoin()` 能确定结束；若为消除这条系统日志而只设置标志、被动等待读取，
   会重新引入停止延迟甚至设备异常时无法 join 的风险。
2. Codec2 的 `BAD_INDEX`/`CORRUPTED` 与 FMQ `grantorIdx` 出现在 OPUS encoder/decoder 初始化阶段，是该
   P3H 平台 codec 实现探测配置项和创建队列时输出的底层诊断。当前测试中 codec 随后正常产出数据，且
   OPUS 达到 `submitted == played`，因此只能判定它们在本设备、本次路径上是非致命平台日志，不能据此
   在库中伪造参数、屏蔽系统日志或切换 codec。

本轮对此采取“保留确定性生命周期语义，不做无依据代码规避”的处理。判断其升级为真实缺陷的门槛是：
同时出现应用层 `AudioRecord.read error`、MediaCodec callback 异常、编码/解码失败、提交与播放帧数不一致、
按钮无法复位、崩溃或资源持续增长。最终两轮 PCM/OPUS 回归均未出现这些伴随现象，所以文档将系统日志
单独记录，但不把它们列为未修复问题。

### 4.12 Audio MediaCodec 生命周期后续复审整改

Claude Code 对音频 teardown 修复再次复审后指出 4 个非阻塞问题。按当前源码核对，4 项均成立，其中 OPUS
CSD 问题不只是“空安全的 benign race”：确定性 `releaseAndJoin()` 原本不会经过 `OpusEncoder.release()`，
因此释放完成后仍可能保留旧 CSD；旧 `release()` 又在公共 codec 锁外先清空 3 个字段，正在执行的配置帧回调
可能随后把旧值写回来。

本轮修复如下：

1. `BaseMediaCodecAsynchronous.onOutputFormatChanged()` 和 `onError()` 与输入、输出 buffer 回调统一使用
   `withCodecOperationLock`，并在锁内再次检查 teardown 状态。这样即使回调通过第一次快速检查后才被
   release/stop 抢先，也不会在 teardown 开始后修改共享 format 或调用外部错误处理。
2. `BaseMediaCodec.stop()` 在等待公共锁之前设置 teardown 标志。已经排队但尚未进入锁的异步回调会退出，
   同步 worker 也不会把 stop 引起的 `IllegalStateException` 当成运行期 codec 故障或伪造正常 EOS。
3. `BaseMediaCodec` 新增锁内 `onCodecReleased()` 清理钩子，由 `releaseCodecOnce()` 唯一调用，因此兼容
   `release()` 和确定性 `releaseAndJoin()` 两条释放路径，且只在底层 codec 首次释放时执行。
4. `OpusEncoder` 把 `csd0/csd1/csd2` 三次独立写入改为一个 `@Volatile OpusCsd` 快照。公开 getter 名称和
   JVM getter 保持不变；配置回调一次发布完整快照，释放钩子一次清空，不再暴露三字段的部分更新状态。
5. `AudioActivity` 不再在主线程直接停止 PCM、AAC 和 OPUS 文件播放器。主线程只摘除当前引用并发出停止
   请求，Codec、AudioTrack 和文件释放均在 IO scope 执行；PCM 路径还会先中断并 join 文件读取线程，再
   释放播放器，避免读取线程与 AudioTrack/decoder 释放并发。
6. `BaseMediaCodecAsynchronousTest` 新增 stop 后迟到 input/format/error 回调测试，以及
   `releaseAndJoin()` 必须执行子类清理钩子的回归测试。

这里没有把所有 callback 异常都吞掉：仅 stop/release 已开始后的迟到回调会被忽略；正常运行期间的
`onError()` 仍传给实现方，input/output 异常仍保留 Error 日志。

### 4.13 Screenshot2H26xStrategy 的 API 21 EGL 兼容

“Start Recording Screen by Screenshot”在当前 P3H 上选择 HEVC 时可能因为设备没有 H.265 encoder 而无法
创建 codec；这是设备能力问题，按维护者决定不在本轮增加 H.264 自动降级。代码审查同时发现另一个独立的
低版本兼容问题：`Screenshot2H26xStrategy` 直接引用 `EGLExt.EGL_RECORDABLE_ANDROID`，该公开字段从 API 26
才可直接使用，导致类和入口被 `@RequiresApi(26)` 限制，和仓库 minSdk 21 不一致。

`EGL_RECORDABLE_ANDROID` 对应 `EGL_ANDROID_recordable` 扩展属性，其固定 token 为 `0x3142`。当前实现改为在
模块内声明该常量，只把它作为 `eglChooseConfig()` 的属性传给 EGL，不再链接 API 26 才公开的 Java 字段；
因此移除了 `initEgl()`、`onInit()` 和 `startRecord()` 的 API 26 注解。`EGLExt.eglPresentationTimeANDROID()`
仍保留，因为它本身不构成本次 API 26 字段引用问题。

同时补强了 EGL config 选择结果校验：必须同时满足 `eglChooseConfig()` 返回成功、`eglGetError()` 为
`EGL_SUCCESS`、配置数量大于 0 且首个配置非空，否则抛出包含 success/count/EGL error 的明确异常，避免
后续用空 config 创建 context 时才得到难定位的失败。该修改解决的是 API 链接与错误报告问题，不保证任意
API 21 设备一定提供满足录制要求的 EGL config，也不代表设备一定具备 H.265 硬件编码器。

### 4.14 BaseMediaCodec 收敛为一次性会话

后续复审指出，`BaseMediaCodec.stop()` 会把 teardown 标志置位，而第二次 `start()` 既不复位标志，又会
创建新的 MediaCodec。这会让新 codec 启动后被回调守卫静默拦截，同时旧 codec 在字段被覆盖前没有释放。
Codex 复核后确认，简单复位 `releasing`/`codecReleased` 不能正确解决问题：一轮编解码还包含输入输出队列、
PTS、帧计数、CSD、EOS、回调和同步 worker；若复用同一包装器，还必须阻止上一轮迟到回调或 worker 访问
下一轮 codec。

本轮在明确不考虑旧版 `stop() -> start()` 兼容性的前提下，选择“一实例一会话”，而不是实现跨会话复用：

```text
NEW -> STARTING -> RUNNING -> RELEASING -> RELEASED
```

理由如下：

1. 新会话直接创建新的 encoder/decoder 包装器，可以天然隔离队列、时间戳、CSD 和回调所有权；功能上仍能
   连续执行任意数量的编解码会话。
2. `ioScope` 在 release 时永久取消，复活原对象会要求重建 scope、worker 和所有子类状态，复杂度与收益
   不成比例。
3. 当前会话内需要丢弃积压数据时仍可使用 `flush()`；如未来实测 codec 创建延迟成为瓶颈，应设计独立资源池
   或显式 `resetAndReconfigure()`，而不是让 `start()` 隐式承担两种生命周期语义。

代码改动：

- `BaseMediaCodec.start()` 固定为一次性入口，不再允许子类覆写；第二次调用、释放后调用或启动失败后重试均
  明确抛出 `IllegalStateException`。
- 基类删除 `stop()`；AAC/OPUS 子类的计数和 CSD 初始化迁到 `onBeforeCodecStart()`，队列和 CSD 清理迁到
  只执行一次的 `onCodecReleased()`。
- 启动流程与 release 共用 codec 操作锁。创建、配置或启动任一步骤失败时，会释放已经部分初始化的 codec
  并进入 `RELEASED`，不保留可重试的半初始化对象。
- `release()`/`releaseAndJoin()` 可从未启动、启动中或运行中进入终态；确定性释放使用不可取消清理区，并
  在获得 codec 锁后再次读取同步 worker 引用，覆盖 start/release 并发安装 worker 的窗口。
- AAC/OPUS encoder 不再公开可绕过生命周期的输入队列，改用 `encode()`；encoder/decoder 仅在
  `RUNNING` 状态接受数据，释放开始后返回失败。

回归测试新增一次性启动、启动失败清理、未启动即释放和 start/release 并发用例；既有迟到回调测试改为验证
terminal release。该策略是有意的源码/API 行为收敛：调用方开始新会话时必须创建新的 codec 包装器实例。

### 4.15 AAC 文件播放停止时提前释放 MediaExtractor

一次性会话改造后的真机复测中，AAC 文件播放点击停止会记录：

```text
Codec illegal state, stopping: java.lang.IllegalStateException
    at android.media.MediaExtractor.readSampleData(Native Method)
    at com.leovp.audio.aac.AacFilePlayer.onInputData(AacFilePlayer.kt:59)
```

异常虽然由同步 codec worker 捕获而没有导致闪退，但它不是 MediaCodec 自身状态错误。原
`AacFilePlayer.stop()` 先调用 `MediaExtractor.release()`，最后才调用基类 release；worker 在停止通知前
仍可能执行 `readSampleData()`，从而访问已经释放的 extractor。由于基类此时仍为 `RUNNING`，该异常也不会
被 teardown 守卫识别为主动停止噪声。

本轮没有通过吞掉 `IllegalStateException` 或降低日志级别掩盖竞态，而是明确资源所有权顺序：

1. `AacFilePlayer.stop()` 改为挂起函数并增加一次性停止门控；
2. 先调用 `AudioTrack.stop()`，唤醒可能阻塞在 Native 写入中的 codec worker；
3. 调用 `releaseAndJoin()`，切换到 `RELEASING`、取消 worker、等待其退出并释放 MediaCodec；
4. worker 完全退出后再释放 `MediaExtractor` 和 `AudioTrack`，并清除完成回调；
5. 播放初始化失败使用独立清理路径，使未启动或部分启动的 codec、extractor 和 AudioTrack 同样进入终态；
6. 没有有效音轨时改为明确失败并进入清理路径，不再从内联 `runCatching` 中非局部返回而遗漏资源释放。

这是有意的公开 API 行为变更：直接调用 `AacFilePlayer.stop()` 的调用方必须从协程或其它挂起上下文调用。
这样停止返回时能够保证 worker 已退出，避免继续保留一个无法兑现资源释放完成语义的同步入口。

修复 APK 在 P3H 上完成自动连续回归后，维护者又按实际使用流程手动复测 AAC 播放与主动停止，确认功能
恢复正常，停止时不再出现上述异常。本问题的代码、构建、自动真机回归和人工验收均已闭环。

## 5. 本轮真实验证记录

### 5.1 已通过

- JDK：JBR 17.0.14。
- 临时启用 `ffmpeg-sdk` 后执行 `:ffmpeg-sdk:assembleRelease --rerun-tasks`，四 ABI JNI 编译通过；
  验证后已恢复 `settings.gradle.kts`，`ffmpeg-sdk` 仍不是默认激活模块。
- 依次执行音频、视频、音视频组合三个 FFmpeg 8.1.1 构建脚本；每个 profile 均重建四 ABI，随后
  重新编译 JNI 并复制到对应 wrapper。上游裁剪构建存在 unused/deprecated warning，但无本项目
  JNI 编译错误。
- `yuv`、`lib-image`、`jpeg`、三个 FFmpeg wrapper 和 `demo:assembleDevDebug` 使用
  `--rerun-tasks` 构建通过，共执行 702 个任务。
- 上述受影响模块的 ktlint/detekt 均通过。首次 detekt 发现新增 KDoc 超过仓库 100 字符限制，已换行并
  重新执行通过。
- 两个 wrapper 中的 `H264HevcDecoder.kt` 逐字一致。
- 四 ABI 的 `libh264-hevc-decoder.so` 均包含 `nativeDecodeFrames` 和 `nativeDrain` 注册字符串；
  后续重建的 8 个视频 decoder 二进制还导出 `JNI_OnUnload`；ADPCM encoder 二进制包含新的完整 frame 契约。
- 三个 wrapper 共 68 个 `.so`：ABI/ELF class、SONAME、64 位 LOAD segment 16KB 对齐检查通过；
  数量分别为音频 20、视频 20、组合 28。
- `git lfs fsck` 通过。
- `AnnexBNalUnitReaderTest` 覆盖混合 start code、NAL type 和参数集/图像组合 packet，执行通过；
  `demo` 的 ktlint、detekt 与 Kotlin 编译重新通过。
- `:demo:assembleDevDebug --rerun-tasks` 再次通过，600 个任务实际执行；APK 已安装到 P3H。
- P3H 上仓库 H.264/H.265 原始样本均持续输出 1920×800 帧；第一轮只检查日志，未发现 H.264 Surface
  仍为黑屏，因此该轮不能算完整播放验证；
- 动态帧尺寸修复后重新安装 APK，分别查看并截图确认 H.264 与 H.265 页面都有实际视频画面；最终日志未
  出现 `Unable to send video packet`、`Unable to receive video frame` 或 `FATAL EXCEPTION`。
- ADPCM encoder JNI 使用 JBR 17 和 NDK 29 对四 ABI 重建通过；两个 wrapper 的 encoder `.so` 均已替换，
  同 ABI 文件 SHA-256 一致；新增 `nativeInputFrameBytes` 注册字符串可在八个二进制中检出，64 位 ELF 的
  LOAD segment 仍保持 16KB 对齐；
- `PcmFramePaddingTest`、Demo 和两个 ADPCM wrapper 的 ktlint/detekt 强制重跑通过；
  `:demo:assembleDevDebug --rerun-tasks` 通过，600 个任务实际执行；两个 wrapper 的 release AAR 也已强制
  重建通过。
- `BaseMediaCodecAsynchronousTest` 新增两条 teardown 并发回归；`audio:ktlintCheck` 与
  `audio:testDebugUnitTest` 使用 `--rerun-tasks` 执行通过。
- `audio:detekt`、`demo:ktlintCheck`、`demo:detekt`、`demo:testDevDebugUnitTest` 和
  `demo:assembleDevDebug` 使用 `--rerun-tasks` 执行通过；Demo 组合任务实际执行 621 个任务。
- 新 APK 安装到 P3H 后，AAC 连续完成 3 轮开始/停止，PCM 和 OPUS 各完成 1 轮开始/停止；另在 AAC
  录音期间直接返回上一页，`onStop()` 也完成确定性清理。各路径均出现正常的
  `Stop recording audio` 和 `Recording released.`，没有 `AudioRecord.read error=-3`、
  `Input buffer callback failed`、`Output buffer callback failed` 或 `FATAL EXCEPTION`。最后生成的 AAC
  文件能够持续解码为 4096 字节 PCM、播放到 EOS，播放按钮自动复位；ADB 只能确认播放链路与状态，
  实际听感仍以人工验收为准。
- PCM/OPUS 播放修复后，使用 JBR 17 将 `audio` 和 `demo` 的 ktlint、detekt、单元测试及
  `:demo:assembleDevDebug` 合并执行并加 `--rerun-tasks`，641 个任务全部实际执行并通过。
- 新 APK 安装到同一台 P3H 后重新录制并播放 PCM。最后一次文件读取为 3584 字节，紧随其后的
  `AudioTrackPlayer` 日志为 `PCM[3584] Play[3584]`，证明尾包没有扩成 8192 字节；录制停止、文件播放和
  自动复位期间均无 `AudioRecord.read error`、MediaCodec 错误或崩溃。
- 同一 APK 重新录制并自然播放 OPUS，最终日志为
  `Playback completed: submitted=194 played=194`。未出现 `Read file. EOF`、
  `Can't find start code`、`Decode OPUS file failed`、输出排空超时、MediaCodec teardown 错误或崩溃。
- 完成 minSdk 21 兼容处理、同步初始化失败回滚和队列日志限频后，最终 APK 再次安装到 P3H。新录制 PCM
  的尾包为 4608 字节，读取与 `AudioTrack` 写入长度均为 4608 字节；新录制 OPUS 自然播放完成，最终为
  `submitted=192 played=192`，192 帧期间仅输出 3 条队列状态 Debug 日志，验证每 50 帧限频生效。PCM 与
  OPUS 播放按钮均自动复位，应用进程保持存活。
- 最终日志仍包含系统 `AudioRecord-JNI: Error -38 during AudioRecord native read` 以及设备 Codec2/FMQ 的
  `BAD_INDEX`、`CORRUPTED`、`grantorIdx` 诊断。这些日志分别出现在主动 `AudioRecord.stop()` 唤醒 Native
  阻塞读取和厂商 codec 初始化阶段；应用层没有 `AudioRecord.read error`、失败回调、MediaCodec 异常或
  功能中断，PCM/OPUS 均完整结束。因此它们属于本机平台实现噪声，不通过改变库的确定性停止语义规避。
- Audio 生命周期后续修复新增的 stop 回调守卫与释放钩子测试通过；`audio`、`screencapture` 和 `demo`
  相关的 lint、ktlint、detekt、单元测试与 `:demo:assembleDevDebug` 合并使用 `--rerun-tasks` 强制重跑，
  802 个任务全部实际执行并通过；`screencapture` 当前没有 JVM 单元测试源码，因此该模块测试任务为
  `NO-SOURCE`。
- `BaseMediaCodec` 一次性会话改造后，`audio:testDebugUnitTest`、`audio:ktlintCheck` 和
  `audio:detekt` 使用 `--rerun-tasks` 强制重跑并通过，共执行 110 个任务；audio 模块共执行 20 个单元
  测试且无失败。新增用例覆盖重复启动、启动失败后的部分资源释放、未启动即释放以及 start/release 并发。
- 直接下游验证执行 `audio:lintDebug`、`audio:assembleDebug`、`demo:ktlintCheck`、`demo:detekt`、
  `demo:testDevDebugUnitTest` 和 `demo:assembleDevDebug`，同样使用 `--rerun-tasks` 强制重跑，723 个任务
  全部实际执行并通过。输出仅包含仓库已有弃用 API 和平台 API 警告，没有新增失败。
- AAC 文件播放停止顺序修复后，`audio:testDebugUnitTest`、`audio:ktlintCheck`、`audio:detekt` 和
  `demo:compileDevDebugKotlin` 使用 `--rerun-tasks` 强制重跑，355 个任务全部实际执行并通过。首次检查仅
  发现新增 KDoc 超过 100 字符限制，换行后复跑通过；随后完整执行 `audio:lintDebug`、
  `audio:assembleDebug`、Demo 静态检查、单元测试和 `demo:assembleDevDebug`，723 个任务全部实际执行并
  通过。其余输出为仓库已有弃用 API 警告。新 APK 安装到 P3H 后，使用已有 AAC 文件完成 4 轮播放中
  主动停止，其中后 3 轮连续执行；日志对应出现 4 次启动和 4 次停止，进程保持存活，未出现
  `Codec illegal state`、`MediaExtractor.readSampleData()` 或 `FATAL EXCEPTION`。维护者随后手动执行同一
  使用流程，确认 AAC 播放和主动停止功能正常。

### 5.2 尚未完成

- H.264/H.265 仓库样本与组合 wrapper 已完成 P3H 真机回归；仍需覆盖带 B 帧、单 packet 多帧、
  多 slice access unit、损坏 packet、主动取消、重复 drain 和独立视频 wrapper。
- ADPCM：Java callback 在批次中途抛异常后，确认后续 PCM 未被消费；非完整 frame/chunk；长时间循环。
- ADPCM Demo：当前验证时 P3H 已从 `adb devices` 断开，修复后的 Encode/Play 点击回归仍待设备重新连接后
  完成；上述结论只覆盖源码、JVM 测试、四 ABI Native 重建和 Demo APK 构建。
- Audio Demo：PCM/AAC/OPUS 的停止日志、PCM/OPUS 自然文件播放和 AAC 录音期间直接退页已在 P3H 通过，
  AAC 文件也已完成解码播放链路验证；本轮将文件播放器释放移到 IO 后，仍需复测三种格式播放中主动停止、
  自然结束、快速重复进入退出及实际听感，并确认没有主线程卡顿或 teardown 错误。一次性 codec 会话的
  JVM、静态检查和 APK 构建已通过；AAC extractor 释放顺序以及“结束旧会话后创建新包装器再开始”已完成
  4 轮 P3H 主动停止回归，并由维护者手动验收通过。PCM/OPUS 的同类重复会话和实际听感仍需人工复测。
- Screenshot 录屏：API 21 兼容代码已完成编译和静态检查；仍需至少一台 API 21～25 真机用 H.264 路径验证
  EGL config、首帧画面、停止释放和重复进入退出。P3H 的 H.265 encoder 缺失不作为本轮代码回归失败。
- yuv/lib-image/jpeg：新增仪器测试、非法输入、并发 close、recycled/HARDWARE Bitmap 和输出一致性。
- ASan/HWASan、16KB 页设备真实加载、P95/Native heap/长跑数据。
- 本轮没有执行或宣称 JPEG `-Wconversion` 零告警验证。

## 6. 最终结论

基线提交可以继续进入剩余设备验证阶段；原复审新增的代码问题和 P3H 回归暴露的 Demo packet 边界错误
和后续 H.264 OpenGL 黑屏均已在当前工作区修复，仓库 H.264/H.265 样本已完成组合 wrapper 真机可视播放
验证。发布门禁仍未达到：在完成其它 Native 模块真机、sanitizer、16KB 加载、异常媒体和长时间输出验证前，
不能标记为“发布验证完成”或“已证明无内存安全回归”。ADPCM Demo 尾帧修复已完成源码、Native、JVM 和
APK 构建验证，但设备在安装前断开，Encode/Play 真机回归仍须补做。Audio Demo 停止 AAC 录音的 teardown
竞态已完成源码、并发 JVM 测试、静态检查、APK 构建以及 P3H 上 PCM/AAC/OPUS 停止、AAC 退页和 AAC
播放链路验证。后续发现的 PCM 尾包重复数据与 OPUS 自然 EOF 伪错误/末帧丢失也已修复，并完成单测、
静态检查、APK 构建及 P3H 文件播放验证；三种格式的实际听感仍待维护者人工确认。
后续复审发现的 format/error 回调锁窗口、stop-only 守卫、OPUS CSD 发布/清理竞态和 Demo 主线程播放器
释放均已完成代码整改与 JVM/编译验证，仍需补播放器停止真机回归。Screenshot 录屏已消除对 API 26
`EGLExt.EGL_RECORDABLE_ANDROID` Java 字段的直接依赖并加强 EGL config 校验，但 API 21～25 真机验证尚未
完成，且该兼容修改不改变设备是否支持 HEVC encoder 的硬件能力边界。`BaseMediaCodec` 已在不保留旧版
复用兼容性的前提下改为“一实例一会话”，并完成并发单测、静态检查和直接下游构建；这项破坏性收敛以
隔离队列、PTS、CSD、EOS、回调和 worker 所有权为优先。后续发现的 AAC 文件播放停止顺序竞态也已修复，
P3H 上 4 轮 AAC 播放中主动停止及新建包装器重复会话均未再出现 extractor 非法状态或崩溃，维护者手动
复测也确认功能正常。

交叉参考：

- `2026-08-27-native-modules-remediation-plan-zh.md`
- `2026-08-27-native-modules-review_cc.md`
- `2026-08-27-native-modules-codex-plan-review_cc.md`
