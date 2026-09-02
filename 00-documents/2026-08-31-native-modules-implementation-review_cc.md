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

### 5.2 尚未完成

- H.264/H.265 仓库样本与组合 wrapper 已完成 P3H 真机回归；仍需覆盖带 B 帧、单 packet 多帧、
  多 slice access unit、损坏 packet、主动取消、重复 drain 和独立视频 wrapper。
- ADPCM：Java callback 在批次中途抛异常后，确认后续 PCM 未被消费；非完整 frame/chunk；长时间循环。
- ADPCM Demo：当前验证时 P3H 已从 `adb devices` 断开，修复后的 Encode/Play 点击回归仍待设备重新连接后
  完成；上述结论只覆盖源码、JVM 测试、四 ABI Native 重建和 Demo APK 构建。
- yuv/lib-image/jpeg：新增仪器测试、非法输入、并发 close、recycled/HARDWARE Bitmap 和输出一致性。
- ASan/HWASan、16KB 页设备真实加载、P95/Native heap/长跑数据。
- 本轮没有执行或宣称 JPEG `-Wconversion` 零告警验证。

## 6. 最终结论

基线提交可以继续进入剩余设备验证阶段；原复审新增的代码问题和 P3H 回归暴露的 Demo packet 边界错误
和后续 H.264 OpenGL 黑屏均已在当前工作区修复，仓库 H.264/H.265 样本已完成组合 wrapper 真机可视播放
验证。发布门禁仍未达到：在完成其它 Native 模块真机、sanitizer、16KB 加载、异常媒体和长时间输出验证前，
不能标记为“发布验证完成”或“已证明无内存安全回归”。ADPCM Demo 尾帧修复已完成源码、Native、JVM 和
APK 构建验证，但设备在安装前断开，Encode/Play 真机回归仍须补做。

交叉参考：

- `2026-08-27-native-modules-remediation-plan-zh.md`
- `2026-08-27-native-modules-review_cc.md`
- `2026-08-27-native-modules-codex-plan-review_cc.md`
