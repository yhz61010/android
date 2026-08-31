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
2. 原复审新发现的两个库问题、两个 demo 问题和列出的 LOW 清理项均已在当前工作区整改。
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
  恢复送包，只允许重试 drain；release 会清空 pending 队列并保持幂等。

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
  ADPCM encoder 二进制包含新的完整 frame 契约。
- 三个 wrapper 共 68 个 `.so`：ABI/ELF class、SONAME、64 位 LOAD segment 16KB 对齐检查通过；
  数量分别为音频 20、视频 20、组合 28。
- `git lfs fsck` 通过。

### 5.2 尚未完成

- 当前没有把构建通过写成真机通过；仍需确认 `adb devices -l` 并执行设备测试。
- H.264/H.265：带 B 帧、单 packet 多帧、损坏 packet、自然 EOS drain、主动取消、重复 drain。
- ADPCM：Java callback 在批次中途抛异常后，确认后续 PCM 未被消费；非完整 frame/chunk；长时间循环。
- yuv/lib-image/jpeg：新增仪器测试、非法输入、并发 close、recycled/HARDWARE Bitmap 和输出一致性。
- ASan/HWASan、16KB 页设备真实加载、P95/Native heap/长跑数据。
- 本轮没有执行或宣称 JPEG `-Wconversion` 零告警验证。

## 6. 最终结论

基线提交可以继续进入设备验证阶段；原复审新增的代码问题已在当前工作区修复，复审文档中不准确的
FFmpeg 状态机、严重度、编译和 LFS 表述也已纠正。发布门禁仍未达到：在完成真机、sanitizer、16KB
加载和真实媒体输出验证前，只能标记为“源码整改和本机构建验证完成”，不能标记为“发布验证完成”或
“已证明无内存安全回归”。

交叉参考：

- `2026-08-27-native-modules-remediation-plan-zh.md`
- `2026-08-27-native-modules-review_cc.md`
- `2026-08-27-native-modules-codex-plan-review_cc.md`
