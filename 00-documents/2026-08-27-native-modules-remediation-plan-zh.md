# Native 模块代码级整改方案（2026-08-27）

## 1. 文档目的

本文统合 `review/native-modules` 分支上的 Codex 初审、Claude Code 独立盲审和 Claude Code 对原方案的复核结果，给出唯一的代码级实施方案。本文最初只描述整改方案；截至 2026-08-31，方案对应的生产代码已经开始实施，但仍不把 Gradle 构建通过等同于 Native 运行时问题已经解决。

交叉审查输入如下：

- Claude Code 独立盲审：`2026-08-27-native-modules-review_cc.md`
- Claude Code 对 Codex 方案的复核：`2026-08-27-native-modules-codex-plan-review_cc.md`
- Codex 对上述反馈的源码复核：重新检查 `lib-image`、`yuv`、`jpeg`、`ffmpeg-javacpp`、`ffmpeg-sdk`、三个 FFmpeg wrapper 的源码、Gradle 配置和预编译 `.so`

两份 `_cc.md` 保留为原始审查证据，不直接作为实施清单；若其表述与本文不同，以本文经源码复核并修正后的方案为准。已确认的主要修正是：引入 CRITICAL 级别、把 `lib-image` 内存安全提前到 P0、显式处理 `transformI420()` 的空返回契约和 `std::bad_alloc`。同时修正两处原始反馈中的实现表述：`lib-image` 当前没有显式启用 `-fno-exceptions`，但未捕获 C++ 分配异常越过 JNI 边界仍会终止进程；`AndroidBitmap_getInfo()` 失败时不能调用 unlock，只有 `AndroidBitmap_lockPixels()` 成功后才允许解锁。

### 1.1 实施状态与验证记录（2026-08-31）

当前工作区已按 A～G 批次完成主要代码改造，包括 YUV/JPEG/Bitmap JNI 边界校验、私有 Native handle 与幂等关闭、JavaCPP/FFmpeg 资源所有权、FFmpeg 输入 padding、热路径缓冲复用、三个 FFmpeg AAR 的三选一 capability、`ffmpeg-javacpp` arm64 限定，以及对应 README/CHANGELOG。三个 FFmpeg profile 已从 `ffmpeg-sdk` 源码重新生成并复制到发布模块，不是只修改 C++ 源码而继续沿用旧二进制。

本机已完成以下验证：

- 使用 JDK 17 对 `lib-image`、`yuv`、`jpeg`、`ffmpeg-javacpp`、三个 FFmpeg wrapper、`camerax` 和 `demo` 执行目标 `ktlintCheck`/`detekt`，全部通过。
- 对七个受影响库执行 `assembleDebug --rerun-tasks`，并重新编译 `camerax`、`demo`；构建通过。
- `yuv`、`lib-image`、`jpeg` 的仪器测试 APK 已成功组装，但尚未在设备上执行。
- 全仓库 `testDebugUnitTest --rerun-tasks --continue` 已执行完毕，没有单元测试失败；全仓库静态检查仍受本轮范围外的既有 ktlint/detekt 违规影响，不能标记为全仓库通过。
- 真实发布到临时 Maven 仓库后，三个 FFmpeg 模块的单模块正向消费均可组装；“音频+视频”“音频+组合”“视频+组合”三种负向组合均因共享 capability 冲突而失败。验证脚本位于 `10-configs/ffmpeg-runtime-consumer-test/verify.sh`。
- 三个 wrapper 的 release AAR 均包含预期的四 ABI 和各自 profile 所需库；68 个发布 `.so` 已检查 ELF 位数、SONAME 和 64 位 LOAD segment 的 16KB 对齐，未发现错误。
- `git lfs fsck` 通过；三个 wrapper 中的 Kotlin 重复实现保持逐字一致。
- `ffmpeg-javacpp` 的 Gradle Module Metadata 和 POM 只选择 `android-arm64` Native classifier；其 AAR 本体不内嵌传递依赖的 `.so`，Native 文件由发布元数据声明的 JavaCPP/FFmpeg classifier 依赖提供。
- `javap` 已确认 `BitmapProcessor` 不再暴露 `bitmapByteBuffer`，Native handle 为私有 `long`，并且兜底方法字节码名为真正的 `finalize()`。

当前仍未满足发布完成定义，原因如下：

- `adb devices -l` 当前没有已连接设备，因此新增仪器测试、FFmpeg 最小解码、并发 close、长时间循环和真实输出一致性尚未执行。
- 尚未取得 ASan/HWASan 运行证据，A～C 的 CRITICAL 项不能仅凭静态审查和构建结果关闭。
- 16KB 页兼容性目前只有 ELF 静态对齐检查，仍需在对应真机上验证所有依赖库可加载。
- YUV/JPEG/H.264/ADPCM 的同机型整改前后性能、Native heap 和 P95 数据尚未采集。

### 1.2 实现复审后的追加整改（2026-08-31）

Claude Code 对 `4a1d99d25` 的实现复审由维护者复制到
`2026-08-31-native-modules-implementation-review_cc.md` 后，Codex 又按当前源码和 FFmpeg 8.1.1
send/receive 契约复核。原复审新增问题中，两项库问题、两项 demo 问题和列出的 LOW 清理项均已处理：

- ADPCM encoder callback 改为返回继续/中止信号；Java callback 抛异常或 JNI 输出分配失败时，
  C++ 外层立即停止当前批次，不再继续消费剩余 PCM 并静默丢弃输出。
- H.264/HEVC 不再把 send/receive 的 `EAGAIN` 混为同一种失败。send 拒绝 packet 时会先排空输出并
  重送同一个 packet；send 成功后循环 receive 到 EAGAIN。新增 `decodeFrames()` 接收当前全部输出，
  `drain()` 在 EOS 获取 Kotlin pending 帧和 codec 内部的 B 帧延迟输出；既有 `decode()` 保持兼容并
  按序缓存额外帧。drain 开始和 drain 完成使用独立状态，异常路径不会重新开放输入，只允许重试 drain。
- H.264/H.265 demo 显式重抛 `CancellationException`，自然 EOF 时 drain；ADPCM demo 使用 `use`
  关闭 codec/流，并明确拒绝不足一个 chunk 的尾部输入。
- 删除两个未参与构建的旧 `.bak`，订正 ADPCM JNI header；YUV plane size/offset 改 64 位；JPEG
  `FindClass` 失败清理 pending exception；lib-image 注册函数改为内部链接；ADPCM decoder 输出 OOM
  路径显式清理。

追加整改后已重新完成三个 FFmpeg 8.1.1 profile 的四 ABI 构建和 wrapper 复制，并验证：

- `ffmpeg-sdk:assembleRelease --rerun-tasks` 四 ABI 通过；默认 `settings.gradle.kts` 已恢复为不激活
  `ffmpeg-sdk`。
- 受影响的七个模块与 `demo:assembleDevDebug --rerun-tasks` 通过；目标 ktlint/detekt 通过。
- 三个 wrapper 的 68 个 `.so` 通过 ABI/ELF class、SONAME、64 位 16KB LOAD 对齐和 LFS fsck；
  H.264/HEVC 四 ABI JNI 二进制均包含新的 `nativeDecodeFrames`/`nativeDrain` 注册。

同时纠正原复审的证据表述：没有证据支持“Claude Code 实编译 3 ABI + `-Wconversion` 零告警”；当前
JPEG Gradle 构建实际覆盖四 ABI，且编译参数不含 `-Wconversion`。LFS 实体当前已完整拉取，不能写成
“只有有效指针、实体未拉取”。完整纠正和验证边界以实现复审文档为准。

因此，本节表示“代码实现和本机构建验证已完成到当前阶段”，不表示第 14 节的全部完成条件已经满足，也不构成发布许可。

- 审查基线：`7f28edf8fb7b238d2a7a3500ffac4fb01f265d4c`
- 远端主线：`origin/master`
- 直接构建 Native 的模块：`lib-image`、`yuv`、`jpeg`
- 发布预编译 Native 二进制的模块：`h264-hevc-decoder`、`adpcm-ima-qt-codec`、`adpcm-ima-qt-codec-h264-hevc-decoder`
- JavaCPP 模块：`ffmpeg-javacpp`
- FFmpeg JNI 源码和二进制生成链：未纳入日常 Gradle 构建的 `ffmpeg-sdk`
- 不在本轮逐行修改的上游源码：FFmpeg、libjpeg-turbo、libyuv；只修改本项目的构建参数、JNI 集成和调用方式

## 2. 信息充分性与实施门槛

现有源码、JNI 注册表、Gradle 发布配置、FFmpeg 构建脚本、预编译 `.so` 和本地 FFmpeg 头文件已经足以确认内存越界、资源泄漏、输入 padding、生命周期竞态、重复分配和 ABI 声明不一致等问题，也足以制定全部代码级修复。Claude Code 复核中标为“未独立复核”的 FFmpeg padding、生命周期、AAR 冲突和 ABI 条目已经再次由 Codex 回到当前源码和二进制核实，仍然成立。

维护者已确认以下公开 API、运行库组合和输入处理决策。实现者应按“已确认方案”执行，不再自行切换到备选设计：

| 决策 ID | 问题 | 已确认方案 |
|---|---|---|
| D1 | `BitmapProcessor.bitmapByteBuffer` 当前是公开可写属性 | 本轮直接删除该公开属性，改为私有 Native handle；接受源码和二进制不兼容，并在 CHANGELOG/发布说明中标为 breaking change |
| D2 | 三个 FFmpeg AAR 的使用关系 | 三者严格三选一：只要音频使用 `adpcm-ima-qt-codec`，只要视频使用 `h264-hevc-decoder`，同时需要音视频使用组合模块；不实施 suffix/SONAME 隔离 |
| D3 | `ffmpeg-javacpp` 声明四 ABI，实际只依赖 `android-arm64` classifier | 当前只支持 `arm64-v8a`，Gradle、AAR 内容和文档统一收窄到 arm64 |
| D4 | `android420ToI420()` 无法表达完整 `YUV_420_888` plane 信息 | 增加完整 plane API，显式接收 Y/U/V buffer、row stride、pixel stride、宽高和变换参数；旧单数组 API保留但废弃 |
| D5 | 非法输入和生命周期错误如何暴露 | 参数非法抛 `IllegalArgumentException`，Native 执行失败抛 `IllegalStateException`，重复 `close/release` 幂等，close 后继续操作抛 `IllegalStateException` |
| D6 | JavaCPP mono 返回值和 ADPCM 非整帧输入 | mono 返回 `leftBytes to ByteArray(0)`；非完整 frame 输入抛参数异常，不静默丢尾部，也不默认维护 remainder buffer |

### 2.1 交叉复核处理结果

| Claude Code 反馈 | 源码复核结论 | 统一方案处理 |
|---|---|---|
| 三个直接构建模块都应 BLOCK | 确认。均存在可触发的 Native 内存安全问题 | A～C 设为发布前 P0 硬门禁 |
| 原方案未设置 CRITICAL | 接受。YUV 越界写/短数组、Bitmap crop/scale、JPEG 格式和返回值缺校验应升级 | 新增 CRITICAL 严重度并拆细 ID |
| `lib-image` 不应留到原批次 D | 接受。crop/scale 内存安全与 YUV/JPEG 同级 | 提前到批次 B |
| `scaleBitmap(-1, -1)` 会按 `0xFFFFFFFF` 次完整循环 | 风险成立，但该精确描述不严谨；`jint` 进入 `uint32_t` 后与有符号循环变量混算，会先产生错误小分配、越界写和有符号溢出 UB | 保留 CRITICAL，统一改 `jint` 先校验再转换，并使用 `size_t` 索引 |
| `transformI420()` 可能返回 null 给非空 Kotlin API | 确认 | NAT-YUV-04 显式处理 OOM/pending exception，并统一移除 critical array 路径 |
| `lib-image` 分配异常可越过 JNI | 问题确认；但“该模块 CMake 已 `-fno-exceptions`”不准确，只有 yuv/jpeg 显式设置 | 所有分配改 `new(std::nothrow)`/显式检查，不依赖编译器异常配置 |
| JPEG getInfo/lock 失败路径 | 问题确认；原反馈中的“getInfo 失败先 unlock”不正确 | 仅在 lock 成功后 unlock，并用 acquired 标志统一清理 |
| 输出路径可能路径穿越 | 不作为本库缺陷。该公开 API 的职责就是写入调用方明确提供的路径，文件系统授权属于调用方边界 | 不纳入整改；仍校验 null、UTF 获取和文件打开失败 |
| FFmpeg padding、生命周期、AAR 冲突、JavaCPP ABI 未独立盲审 | Codex 已重新核对当前源码、Gradle 与四 ABI 二进制；问题成立 | 保留 D/E/G，并补完整二进制闭环 |

Claude Code 对 `NAT-YUV-02` 和 JPEG `optimize` 顺序问题的反向确认也成立：前者是原 Codex 审查发现、Claude 盲审漏报的旋转 stride 问题，后者是 `jpeg_set_defaults()` 覆盖先前设置导致参数失效。两项均保留在统一方案中。

## 3. 问题与整改批次总览

本文中的 CRITICAL 是 Native 内存安全严重度，表示可通过公开 Kotlin/JNI API 的普通非法参数或受损输入触发越界读写、堆破坏或进程级崩溃；它不是对远程可利用性的 CVSS 定级。所有 CRITICAL 项均为发布硬门禁，必须先于生命周期、性能和打包整改完成。

| ID | 级别 | 模块 | 问题 | 批次 |
|---|---|---|---|---|
| NAT-YUV-01 | CRITICAL | yuv | `i420ToRgb24()` 把目标总长度当行 stride，第二行起越界写 | A |
| NAT-YUV-02 | CRITICAL | yuv | `nv12ToI420()` 在 90/270 度旋转后仍使用原宽度作为目标 stride，部分宽高关系下越界写 | A |
| NAT-YUV-03 | CRITICAL | yuv | 大多数旧 JNI API 缺尺寸、源数组长度、枚举和溢出检查 | A |
| NAT-YUV-04 | HIGH | yuv | `transformI420()` 取 critical array 失败时可返回 `nullptr`，违反 Kotlin 非空契约；底层状态码未统一上报 | A |
| NAT-BMP-01 | CRITICAL | lib-image | crop/scale 缺边界与防溢出校验，负数经无符号转换后可造成堆破坏 | B |
| NAT-BMP-02 | CRITICAL | lib-image | bilinear scale 对 1×N/N×1 输入生成负索引并越界读 | B |
| NAT-BMP-03 | HIGH | lib-image | 公开可写 Native 句柄、无效 finalizer 和并发 close/操作可导致伪造指针、泄漏或 UAF | E |
| NAT-BMP-04 | HIGH | lib-image | Native 分配、`NewDirectByteBuffer`、Bitmap/JNI 调用和 Kotlin 非空返回契约缺完整错误处理 | B |
| NAT-BMP-05 | MEDIUM | lib-image | Bitmap 读写忽略行 stride | B |
| NAT-JPEG-01 | CRITICAL | jpeg | 未校验 Bitmap 格式、getInfo/lock 结果、尺寸乘法和分配结果，可造成越界读写或崩溃 | C |
| NAT-JPEG-02 | HIGH | jpeg | libjpeg 错误路径泄漏 compressor、文件、路径字符串和整帧 RGB 缓冲 | C |
| NAT-JPEG-03 | MEDIUM | jpeg | 忽略 Bitmap stride、保留整帧 RGB 临时缓冲，`optimize` 设置被 defaults 覆盖 | C |
| NAT-JCPP-01 | HIGH | ffmpeg-javacpp | 每次成功解码泄漏 `AVFrame`，单声道访问不存在的右声道 | D |
| NAT-FFMPEG-01 | HIGH | ffmpeg-sdk | H.264/HEVC、ADPCM 输入没有 FFmpeg 要求的零 padding | D |
| NAT-LIFE-01 | HIGH | FFmpeg wrappers | init、decode/encode、release 并发可造成泄漏、UAF 或 double free | E |
| NAT-PERF-01 | MEDIUM | 多模块 | 热路径整帧临时分配和跨 Java/Native 重复复制 | F |
| NAT-PKG-01 | MEDIUM | 三个 FFmpeg AAR | 同名、不同内容的 FFmpeg `.so` 存在打包和加载冲突 | G |
| NAT-ABI-01 | MEDIUM | ffmpeg-javacpp | Gradle ABI 声明与实际 classifier 不一致 | G |

实施顺序固定为 A → B → C → D → E → F → G：A～C 是 P0 Native 内存安全，D～E 关闭资源所有权与生命周期风险，F 在安全边界稳定后做性能优化，G 最后处理发布和消费约束。A～C 可拆成独立提交，但在任一包含这些模块的版本发布前必须全部完成。

基线源码定位如下，行号以 `7f28edf8f` 为准，修改后应按函数名而不是旧行号继续追踪：

| ID | 基线位置 |
|---|---|
| NAT-YUV-01 | `yuv/src/main/cpp/YuvConvert.cpp:331-345`、`YuvUtilNative.cpp:422-437` |
| NAT-YUV-02 | `yuv/src/main/cpp/YuvConvert.cpp:244-272`、`YuvUtilNative.cpp:335-350` |
| NAT-YUV-03 | `yuv/src/main/cpp/YuvUtilNative.cpp:11-120,236-438`，异常后继续执行见 `:381-384` |
| NAT-YUV-04 | `yuv/src/main/cpp/YuvUtilNative.cpp:159-187` |
| NAT-BMP-01/02/04/05 | `lib-image/src/main/kotlin/com/leovp/image/BitmapProcessor.kt:33,40-145`、`lib-image/src/main/cpp/BitmapRotateNative.cpp:18-43,146-236,239-380` |
| NAT-JPEG-01/02/03 | `jpeg/src/main/cpp/JPEGNative.cpp:40-191` |
| NAT-JCPP-01 | `ffmpeg-javacpp/src/main/kotlin/com/leovp/ffmpeg/javacpp/audio/adpcm/AdpcmImaQTDecoder.kt:49-84` |
| NAT-FFMPEG-01 | `ffmpeg-sdk/src/main/cpp/h264_hevc_decoder/h264_hevc_decoder_all_in_one_file.cpp:226-245`、`adpcm_ima_qt_decoder/native_adpcm_ima_qt_decoder.cpp:47-69` |
| NAT-LIFE-01 | H264 Native `:65-224`；ADPCM decoder JNI `:19-69`；ADPCM encoder JNI `:19-62` |
| NAT-BMP-03 | `lib-image/src/main/kotlin/com/leovp/image/BitmapProcessor.kt:33,62-145`、`lib-image/src/main/cpp/BitmapRotateNative.cpp:146-236` |
| NAT-PKG-01 | 三个 wrapper 模块各自的 `build.gradle.kts` 和 `src/main/libs/<abi>/` |
| NAT-ABI-01 | `ffmpeg-javacpp/build.gradle.kts:11-15,63-79` |

## 4. 批次 A：YUV 内存安全和 JNI 边界统一

### 4.1 涉及文件

- `yuv/src/main/cpp/YuvUtilNative.cpp`
- `yuv/src/main/cpp/YuvConvert.cpp`
- `yuv/src/main/cpp/YuvConvert.h`
- `yuv/src/main/kotlin/com/leovp/yuv/YuvUtil.kt`
- 新增 `yuv/src/androidTest/.../YuvUtilInstrumentedTest.kt`

### 4.2 建立统一校验和安全计算函数

在 `YuvUtilNative.cpp` 增加仅供当前翻译单元使用的 helper，所有 JNI 入口必须先完成校验，再创建输出数组或取得 Java 数组地址：

```cpp
static bool CheckedMul(int64_t left, int64_t right, int64_t *result);
static bool CheckedI420Size(jint width, jint height, jsize *size);
static bool CheckedRgb24Size(jint width, jint height, jsize *size);
static bool IsRotationValid(jint degree);
static bool IsFilterModeValid(jint mode);
static bool RequireArrayLength(
        JNIEnv *env,
        jbyteArray source,
        int64_t required,
        const char *message);
static void ThrowIllegalStateException(JNIEnv *env, const char *message);
```

校验规则如下：

1. 任何宽高必须大于 0；I420、NV12、NV21 的宽高必须是偶数。
2. 旋转角只允许 `0/90/180/270`。
3. filter mode 只允许 `0..3`。
4. `width * height`、`* 3 / 2`、`* 3`、`* 2` 全部使用 `int64_t` 计算，并确认结果不超过 `jsize`/`INT32_MAX`。
5. I420/NV12/NV21 输入至少为 `width * height * 3 / 2`；YUY2 输入至少为 `width * height * 2`。
6. crop 必须满足 `left >= 0`、`top >= 0`、目标宽高为正偶数、`left/top` 为偶数，并用减法检查边界：`dstWidth <= srcWidth - left`、`dstHeight <= srcHeight - top`，避免 `left + dstWidth` 自身溢出。
7. `ScaleNV12()` 抛出异常后必须立即 `return nullptr`；不要在 pending exception 状态下继续调用 libyuv。
8. `NewByteArray()`、`GetByteArrayElements()` 返回空时立即退出；若 JVM 尚无 pending exception，则显式抛 `OutOfMemoryError`，保证 Native 不会把 `nullptr` 返回给 Kotlin 非空签名。

`android420ToI420()` 当前只接收一个扁平数组，却没有 row stride 和三个 plane offset，无法无歧义表达通用 `YUV_420_888`。按 D4 实施：

- 在 Kotlin 上将旧方法标记为废弃，说明不要用于任意 `Image.Plane` 数据。
- 旧 JNI 方法只接受可严格验证的紧密 planar I420 布局，即 `pixelStrideUV == 1`；传入 2 或其它值直接抛 `IllegalArgumentException`，不能继续假设 U/V plane 的 offset 和 row stride。
- 新增完整 API，参数至少包含 Y/U/V 三个 buffer、各自 row stride 和 pixel stride；支持 direct/heap `ByteBuffer`，无法取得 direct address 时使用有界 fallback copy，不猜测平面布局。
- 新 API支持 `pixelStride == 1/2` 的常见 planar/semiplanar `YUV_420_888`；若 U/V pixel stride 不相同，则走逐 plane 提取再变换路径，不能把不等 stride 强行传给只接受一个 UV pixel stride 的 libyuv API。

公开 Kotlin API 建议固定为以下语义：每个 plane 从传入 `ByteBuffer` 的当前 `position()` 开始读取，函数不得修改调用方的 position/limit；row stride 和 pixel stride 均以字节为单位。

```kotlin
fun android420ToI420(
    yBuffer: ByteBuffer,
    uBuffer: ByteBuffer,
    vBuffer: ByteBuffer,
    yRowStride: Int,
    uRowStride: Int,
    vRowStride: Int,
    yPixelStride: Int,
    uPixelStride: Int,
    vPixelStride: Int,
    width: Int,
    height: Int,
    verticallyFlip: Boolean,
    degree: Int = ROTATE_0,
): ByteArray
```

进入 Native 前分别计算每个 plane 最后一个可读字节：`(planeHeight - 1) * rowStride + (planeWidth - 1) * pixelStride + 1`，全程使用 64 位并验证不超过该 buffer 的 `remaining()`。Direct buffer 使用 address + position；heap buffer 使用 backing array 的 `arrayOffset + position`，read-only 或无可访问 backing array 的 heap buffer 通过有界临时副本处理。Y plane 常见 pixel stride 为 1，但完整 API 不能未经校验地假设这一点；无法直接交给 libyuv 时按 stride 逐像素提取到有界 scratch buffer。

### 4.3 消除源和目标整帧 Native 副本

旧入口普遍执行“Java 输入 → `new[]` 源数组 → `new[]` 目标数组 → Java 输出”。包括现有 `transformI420()` 在内统一改成：

1. 校验输入和目标长度。
2. 直接 `NewByteArray(outputLength)` 创建最终 Java 输出。
3. 通过 `GetByteArrayElements()` 分别取得 source/output 地址；移除当前 `transformI420()` 嵌套持有的两个 `GetPrimitiveArrayCritical()` 指针。
4. 只在两个数组均成功取得后调用 libyuv。
5. 所有退出路径按相反顺序释放；source 使用 `JNI_ABORT`，output 成功时使用 `0`。
6. 取得两个元素指针后只执行同步 libyuv 计算，再立即 release；中间不执行其它 JNI 调用、文件 I/O 或锁等待。

建议抽取小型 RAII guard，析构函数只调用 `ReleaseByteArrayElements()`；该 guard 不使用 C++ exception。`GetByteArrayElements()` 仍可能由 JVM 选择复制，但可消除代码中必然发生的两次显式 `new[]` 整帧分配，并避免 critical section 违规。当前 CMake 关闭异常，所有 scratch buffer 继续使用 `new(std::nothrow)` 或 `malloc` 并显式检查。

### 4.4 修复两个确定越界点

`YuvConvert.cpp` 的底层函数应返回 libyuv 的 `int` 状态码，不再是 `void`。JNI 层先保存状态码并释放 source/output array elements，随后才在非零时抛 `IllegalStateException`；不能带着尚未 release 的 JNI buffer 直接抛异常返回。

`i420ToRgb24()` 不再接收总 buffer 长度作为 stride：

```cpp
int i420ToRgb24(
        const uint8_t *source,
        jint width,
        jint height,
        uint8_t *destination) {
    return libyuv::I420ToRGB24(
            sourceY, width,
            sourceU, width / 2,
            sourceV, width / 2,
            destination, width * 3,
            width, height);
}
```

同时删除 `YuvUtil.i420ToRgb24()` 中“libyuv 缺少 jpeg 所以不可用”的错误注释，补充返回数据为每行连续的 RGB24、每像素 3 字节。

`nv12ToI420()` 的目标 plane offset 仍按总像素数计算，但目标 stride 必须取旋转后的宽度：

```cpp
const jint outputWidth =
        degree == libyuv::kRotate90 || degree == libyuv::kRotate270
        ? height
        : width;
const jint dstYStride = outputWidth;
const jint dstUStride = outputWidth / 2;
const jint dstVStride = outputWidth / 2;
```

### 4.5 YUV 测试

必须新增设备测试，因为普通 JVM 测试不能加载 Android JNI 库：

- 对每个公开函数覆盖 `2x2`、`4x2`、`1920x1080`。
- `nv12ToI420(1920, 1080, 90/270)` 验证返回长度、旋转后 plane stride 和已知像素位置。
- `i420ToRgb24()` 用纯黑、纯白、红/绿/蓝测试向量验证长度为 `width * height * 3`，并验证第二行数据紧邻第一行。
- 对 null、空数组、短 1 字节数组、奇数宽高、负数、`Int.MAX_VALUE`、非法旋转、非法 filter、越界 crop 断言抛 `IllegalArgumentException`，且进程不崩溃。
- 对 libyuv 返回失败的可构造输入断言为 `IllegalStateException`。
- 通过故障注入或可控包装模拟 `NewByteArray/GetByteArrayElements` 失败，确认只抛 `OutOfMemoryError`，不会从非空 API 返回 null 或在 pending exception 后继续调用 JNI。
- 完整 `YUV_420_888` API 覆盖 direct、heap、read-only buffer，非零 position/arrayOffset，Y/U/V row padding，pixel stride 1/2 和 U/V 不同 stride；验证调用后 position/limit 不变。
- 连续执行 1080p rotate/mirror/scale 各 1,000 次，记录 Native heap 峰值和稳定值；测试结束后 Native heap 不应线性增长。

## 5. 批次 B：lib-image 内存安全和 JNI 契约

### 5.1 涉及文件

- `lib-image/src/main/kotlin/com/leovp/image/BitmapProcessor.kt`
- `lib-image/src/main/cpp/BitmapRotateNative.cpp`
- `lib-image/src/main/cpp/BitmapRotateNative.h`
- 新增 `lib-image/src/androidTest/.../BitmapProcessorInstrumentedTest.kt`

### 5.2 直接消除可伪造的 DirectByteBuffer 句柄

按 D1 直接删除公开 `bitmapByteBuffer`，不要仅把它从 public 改成 private 后继续沿用零 capacity 的 `DirectByteBuffer`。统一改成私有 `Long` handle：

```kotlin
class BitmapProcessor(bitmap: Bitmap) : Closeable {
    private var nativeHandle: Long = nativeSetBitmapData(bitmap)
    private var closed = false

    private external fun nativeSetBitmapData(bitmap: Bitmap): Long
    private external fun nativeFreeBitmapData(handle: Long)
    private external fun nativeCropBitmap(handle: Long, left: Int, top: Int, right: Int, bottom: Int)
}
```

Native 侧通过 `reinterpret_cast<intptr_t>` 在指针和 `jlong` 之间转换；所有 JNI 注册描述符同步从 `Ljava/nio/ByteBuffer;` 改为 `J`。任何 Native 创建失败必须先清理已分配资源，再抛 `IllegalStateException` 并返回 `0L`；Kotlin 初始化后不得存在“非空类型实际收到 null”的状态。

这项会删除公开 getter/setter 及其 JVM 方法，并改变 private native 方法描述符，是已确认的 breaking change。README、CHANGELOG 和发布说明必须明确写出：调用方不再能读取或替换 Native handle，应只通过 `BitmapProcessor` 的公开操作和 `close()/use {}` 管理对象。

### 5.3 统一尺寸、边界和分配校验

所有变换在读取像素指针或分配前执行校验，并使用 `size_t`/`uint64_t` 安全计算：

1. Native JNI 参数使用 `jint` 接收；先校验正负，再转换为无符号或 `size_t`，禁止让 Kotlin 负数直接按位进入 `uint32_t`。
2. 抽取 `CheckedPixelCount(width, height, &count)` 和 `CheckedByteCount(count, sizeof(uint32_t), &bytes)`；拒绝零维度、乘法溢出和超过可创建 Java Bitmap 的尺寸。
3. crop 必须满足 `0 <= left < right <= oldWidth`、`0 <= top < bottom <= oldHeight`，边界比较使用减法形式避免加法溢出。
4. nearest-neighbour 和 bilinear scale 的目标宽高必须大于 0；循环索引和目标 offset 使用 `size_t`，不能用有符号 `int` 与 `uint32_t` 混合比较。
5. bilinear 对 `oldWidth == 1` 或 `oldHeight == 1` 必须通过 clamp 或退化为单轴/nearest-neighbour 采样；任何 `xTopLeft/yTopLeft` 均不得成为负数。
6. rotate/crop/scale 的所有 `new[]` 改为 `new(std::nothrow)` + `std::unique_ptr<uint32_t[]>`，显式检查失败并抛 `OutOfMemoryError`。`lib-image` 当前虽未显式关闭 C++ exception，也不能允许 `std::bad_alloc` 越过 JNI 边界。
7. 先在临时所有者中完成新图计算，成功后再替换 `JniBitmap` 的旧像素和宽高；失败时保留原图，避免对象处于半更新状态。

### 5.4 正确处理 Bitmap stride 和 JNI 异常

`SetBitmapData()` 和 `GetBitmapFromSavedBitmapData()` 都按行复制。Native 内部缓存仍可保持 `width * 4` 紧密布局，但 Android Bitmap 的每行地址必须用 `AndroidBitmapInfo.stride` 计算，不能对整张图一次 `memcpy(width * height * 4)`。

每个 JNI/Bitmap 操作必须逐项检查：

- `bitmap/handle` 非空，handle 非 0；
- `AndroidBitmap_getInfo()` 成功且格式为 `ANDROID_BITMAP_FORMAT_RGBA_8888`；
- `AndroidBitmap_lockPixels()` 成功后才设置 `locked = true`，只有该标志为 true 才调用 unlock；
- `FindClass()`、`GetStaticMethodID()`、`NewStringUTF()`、`CallStaticObjectMethod()` 的返回值和 `ExceptionCheck()`；
- 创建 Bitmap 失败或已有 pending exception 时立即停止，禁止继续 `AndroidBitmap_lockPixels()`；
- 所有 local ref 在正常和失败路径都释放。

Native 失败遵循 D5：参数问题抛 `IllegalArgumentException`，Bitmap/JNI/处理失败抛 `IllegalStateException`，分配失败保留或补充 `OutOfMemoryError`。不得只记日志并返回 null，也不得让 Kotlin 的非空 private external 返回值实际收到 null。

### 5.5 P0 测试

- 公开 API 覆盖负数、零、`Int.MAX_VALUE`、反向 crop、超边界 crop，断言抛 `IllegalArgumentException` 且原图仍可继续读取。
- 覆盖 1×1、1×N、N×1、2×2 的 nearest-neighbour/bilinear scale 和三种旋转，验证已知像素。
- RGBA_8888 输入输出逐像素比对；构造可用的非紧密 stride Bitmap 时验证逐行复制。
- 非 RGBA_8888、recycled/HARDWARE Bitmap 稳定抛异常，不能 silent no-op 或 native crash。
- 在 ASan/HWASan 可用的 arm64 设备上对非法 crop/scale 循环执行，确认无 heap-buffer-overflow、UAF 或 double free。
- 用 `javap`/binary API 检查确认 `getBitmapByteBuffer/setBitmapByteBuffer` 已移除，并把 breaking API 差异纳入发布检查。

## 6. 批次 C：JPEG 错误路径、stride 和峰值内存

### 6.1 涉及文件

- `jpeg/src/main/cpp/JPEGNative.cpp`
- `jpeg/src/main/kotlin/com/leovp/jpeg/JPEGUtil.kt`
- 新增 `jpeg/src/androidTest/.../JPEGUtilInstrumentedTest.kt`

### 6.2 重构资源状态和单一清理出口

`write_JPEG_file()` 当前通过 `setjmp/longjmp` 处理 libjpeg 错误。不能简单把 `FILE*`、row buffer 改成带析构函数的局部 C++ 对象，因为 `longjmp` 会绕过 C++ 析构。推荐使用堆上显式状态结构和单一 cleanup；堆对象地址在 `longjmp` 后仍稳定，也避免读取 setjmp 之后被修改的非 volatile 自动变量：

```cpp
struct JpegWriteState {
    jpeg_compress_struct compressor{};
    my_error_mgr error{};
    FILE *file = nullptr;
    uint8_t *row = nullptr;
    bool compressorCreated = false;
};
```

通过 `calloc` 在建立 `setjmp` 前创建并初始化状态结构。任何错误都跳到统一 cleanup，按以下顺序释放：

1. 若 compressor 已创建，调用 `jpeg_destroy_compress()`。
2. 若文件已打开，调用 `fclose()`。
3. `free(row)`。
4. `free(state)`。
5. 返回失败码；JNI 外层仅在本次调用确实打开/创建过目标文件时删除残缺文件。

`my_error_exit()` 不再直接用 `msg_code` 下标访问 `jpeg_message_table`；改用 libjpeg 的 `format_message()` 写入固定 `JMSG_LENGTH_MAX` 缓冲后记录日志，再执行 `longjmp`，避免 addon/异常消息编号导致错误表越界访问。

JNI 外层同样使用单一 cleanup，确保任何路径都会：

- 成功 lock 后执行 `AndroidBitmap_unlockPixels()`；
- 成功取得路径后执行 `ReleaseStringUTFChars()`；
- 不再保留整帧 `w * h * 3` 的 `tempData`；
- 保留 JVM pending exception，不在异常状态继续压缩。

`AndroidBitmap_getInfo()` 失败时尚未发生 lock，直接抛 `IllegalStateException`，不得调用 unlock；`AndroidBitmap_lockPixels()` 返回成功后才设置 outer cleanup 使用的 `bitmapLocked` 标志。路径字符串、Bitmap lock 和输出文件分别用独立 acquired/opened 标志跟踪，避免把“尝试获取”误当成“已经获得”。

### 6.3 改为逐行 RGBA→RGB

只分配 `width * 3` 字节 row buffer。第 `y` 行源地址必须按 `AndroidBitmapInfo.stride` 计算：

```cpp
const auto *sourceRow =
        static_cast<const uint8_t *>(bitmapPixels) + y * bitmapInfo.stride;
```

按 RGBA_8888 的字节布局逐像素写入 RGB row（`R=source[x*4]`、`G=source[x*4+1]`、`B=source[x*4+2]`），然后立即 `jpeg_write_scanlines()`；不要继续依赖把四字节像素转成 `uint32_t` 后的主机端序掩码。这把 12MP 图片的额外 Native RGB 缓冲从约 36 MB 降至一行大小，并正确处理非紧密 Bitmap stride。

在 lock 前检查：

- `bitmap`、`outFilePath` 非空；
- `AndroidBitmap_getInfo()` 返回 0；
- format 为 `ANDROID_BITMAP_FORMAT_RGBA_8888`；
- width/height 非零，`width * 3`、`stride * height` 等所有 offset/size 计算不溢出，并确认 `bitmapInfo.stride >= width * 4`；
- quality 在 `0..100`，否则抛 `IllegalArgumentException`；
- `AndroidBitmap_lockPixels()` 返回 0；
- `GetStringUTFChars()`、row buffer 分配成功。

为避免额外的二进制 API 破坏，`JPEGUtil.compressBitmap(...): Int` 暂保留现有 JVM 签名：成功只返回 `0`；参数错误、文件打开/编码失败和 OOM 分别按 D5/平台异常抛出，不再用 `-1` 与异常混合表达失败。KDoc 必须写清这一约定。

### 6.4 修复压缩参数顺序

顺序必须为：

```cpp
jpeg_set_defaults(&compressor);
compressor.optimize_coding = optimize == JNI_TRUE;
compressor.arith_code = FALSE;
jpeg_set_quality(&compressor, quality, TRUE);
```

不要根据 `optimize == false` 自动启用 arithmetic coding；`optimize` 参数只控制 Huffman table 优化，避免生成兼容性较差且与参数语义无关的 arithmetic JPEG。

### 6.5 JPEG 测试

- 使用小图和 12MP RGBA_8888 图压缩，验证文件可解码、宽高和关键颜色正确。
- 传入不存在/不可写目录，循环失败 100 次，确认文件描述符和 Native heap 不线性增长。
- 传入非法 quality、非 RGBA_8888 或 recycled Bitmap，确认稳定失败且不会泄漏 lock/path。
- 在 ASan/HWASan 可用的 arm64 设备上重复传入 RGB_565、ALPHA_8、recycled/HARDWARE Bitmap 和故障注入的分配失败，确认无越界读、null 解引用或错误 unlock。
- 对 `optimize=true/false` 各压缩一次，确认两者均可解码；只比较参数生效和合理性能，不要求文件一定更小。
- 用 Native Memory Profiler 对比整改前后 12MP 峰值，目标是移除约 `width * height * 3` 的额外缓冲。

## 7. 批次 D：FFmpeg 输入 padding 和 JavaCPP 资源所有权

### 7.1 JavaCPP ADPCM 解码器

涉及文件：`ffmpeg-javacpp/src/main/kotlin/com/leovp/ffmpeg/javacpp/audio/adpcm/AdpcmImaQTDecoder.kt`。

类应实现 `Closeable`，并把 `AVCodecContext`、`AVPacket`、`AVFrame` 都变为实例级、一次分配的资源：

```kotlin
class AdpcmImaQTDecoder(...) : Closeable {
    private var context: AVCodecContext? = null
    private var packet: AVPacket? = null
    private var frame: AVFrame? = null

    @Synchronized
    fun decode(adpcmBytes: ByteArray): Pair<ByteArray, ByteArray>? { ... }

    @Synchronized
    override fun close() { ... }
}
```

初始化必须逐项检查 codec、context、packet、frame 和 `avcodec_open2()`；任何一步失败都释放此前资源并抛 `IllegalStateException`，不能留下一个 `ctx != null` 但不可用的对象。

资源释放必须只走一套所有权 API：由 `av_frame_free()`、`av_packet_free()`、`avcodec_free_context()` 各释放一次，不再同时调用 JavaCPP `Pointer.close()` 和对应 `av_*_free()`。当前错误路径在 return 前 free packet，随后 finally 又 free，是需要一并消除的 double-free 风险。

每次解码：

1. `av_packet_unref(packet)`、`av_frame_unref(frame)`。
2. 调用 `av_new_packet(packet, adpcmBytes.size)`；该 API 负责分配 FFmpeg packet payload。复制完成后仍显式确认或归零 `data + size` 开始的 `AV_INPUT_BUFFER_PADDING_SIZE` 字节，避免把具体 JavaCPP/FFmpeg 版本的初始化细节当成隐含前提。
3. 把 Java 数据复制到 `packet.data()`，不要再使用 `BytePointer(*adpcmBytes)` 的 spread 构造。
4. `avcodec_send_packet()` 成功后再 receive；在 `finally` 中 `av_packet_unref()`。
5. 每声道有效 PCM 长度用 `frame.nb_samples() * av_get_bytes_per_sample(frame.format())`，不要使用可能包含对齐 padding 的 `linesize(0)`；若实际输出不是 planar 格式，则显式解交错，不能把 packed buffer 误当成两个 plane。
6. stereo 且 planar 时才访问 `extended_data(1)`；mono 返回 `leftBytes to ByteArray(0)`，并在 KDoc 写明右声道为空。
7. `close()` 先 free frame、packet，最后 free context，并把字段置空；重复调用无效果；close 后 decode 抛 `IllegalStateException`。

### 7.2 H.264/HEVC Native 输入

涉及 `ffmpeg-sdk/src/main/cpp/h264_hevc_decoder/h264_hevc_decoder_all_in_one_file.cpp`。

替换当前精确长度 `new uint8_t[videoRawLen]`：

```cpp
av_packet_unref(decoderCtx->pkt);
int ret = av_new_packet(decoderCtx->pkt, videoRawLen);
if (ret < 0) { ... }
env->GetByteArrayRegion(
        videoRawByteArray,
        0,
        videoRawLen,
        reinterpret_cast<jbyte *>(decoderCtx->pkt->data));
memset(
        decoderCtx->pkt->data + videoRawLen,
        0,
        AV_INPUT_BUFFER_PADDING_SIZE);
```

所有 send/receive 错误路径都必须 `av_packet_unref()`；不得把 Java 数组或无 padding 的临时指针直接挂到 `AVPacket.data`。

同时补充：

- `videoRawByteArray` 非空且长度大于 0；
- `av_packet_alloc()`、`av_frame_alloc()`、`av_malloc()`、`sws_getContext()`、`av_image_get_buffer_size()`、`NewByteArray()` 全部检查返回值；
- `ctx->extradata` 分配失败时走统一 context cleanup；
- `av_image_get_buffer_size()` 和 `av_image_fill_arrays()` 使用相同 align，建议都用 `1`；
- 若发生 RGB 转换，`DecodedVideoFrame.format` 返回实际 `bmpFormat`，而不是原始 `frame->format`；
- `written_image_bytes <= 0` 时不创建 Java 数组。

### 7.3 ADPCM Native 输入

涉及：

- `ffmpeg-sdk/src/main/cpp/adpcm_ima_qt_decoder/native_adpcm_ima_qt_decoder.cpp`
- `ffmpeg-sdk/src/main/cpp/adpcm_ima_qt_decoder/adpcm_ima_qt_decoder.cpp/.h`

把 packet 的分配、填充和 unref 放到 `AdpcmImaQtDecoder::decode()` 内部：

1. `av_packet_unref(pkt)`。
2. `av_new_packet(pkt, adpcmLength)`。
3. `memcpy(pkt->data, input, adpcmLength)`。
4. 显式把 `pkt->data + adpcmLength` 后的 `AV_INPUT_BUFFER_PADDING_SIZE` 字节置 0。
5. send 成功或失败后均在返回前 `av_packet_unref(pkt)`。

JNI 层可以通过 `GetByteArrayElements()`/critical pointer 临时读取 Java 输入，不再额外 `new[]` 一份；持有指针期间只调用同步 Native 解码，不执行 JNI 回调。输出仍由独立 buffer 返回，避免引用 FFmpeg-owned frame 数据。

### 7.4 padding 回归

- H.264、HEVC 分别覆盖正常帧、截断帧、只有 start code 的帧、随机损坏帧；期望返回失败或空结果，不能 SIGSEGV。
- ADPCM 覆盖正确 34/68 字节以及边界损坏内容。
- 在 arm64 HWASan/ASan 可用环境执行损坏输入循环；重点观察 packet 末尾越界读取。
- 通过本地 FFmpeg 头文件再次确认 `AV_INPUT_BUFFER_PADDING_SIZE`，测试代码不要硬编码 64 以外的自定义常量。

## 8. 批次 E：Native handle 生命周期

### 8.1 Kotlin/Native 生命周期统一

以下公开类都应实现 `Closeable`：

- 两个模块副本中的 `H264HevcDecoder`
- 两个模块副本中的 `AdpcmImaQtDecoder`
- 两个模块副本中的 `AdpcmImaQtEncoder`
- `BitmapProcessor` 已实现 `Closeable`，需修正内部实现

公开方法保留现有 JVM 签名，但不再直接声明为 `external`。使用同步的 Kotlin wrapper 包住私有 Native 方法：

```kotlin
@Synchronized
fun release() {
    if (nativeHandle == 0L) return
    nativeRelease()
}

@Synchronized
override fun close() = release()
```

`init/decode/encode/release` 必须在同一个实例 monitor 下互斥。这样 release 不能在另一个线程仍使用 handle 时删除 Native 对象。Native 注册表同步改为 `nativeInit/nativeDecode/nativeEncode/nativeRelease`，公开 API 名称保持不变。

H264/HEVC 的 public `init()` 在 `nativeHandle != 0L` 时抛 `IllegalStateException("Decoder is already initialized")`。Native `init()` 仍保留相同检查作为防御，不能再次 `SetLongField()` 覆盖旧指针。

ADPCM 构造函数必须检查 Native init 返回值；非 0 时抛 `IllegalStateException`。channels 只允许 1 或 2，sample rate、bit rate 必须为正，不能把任意非 2 channel 静默当 mono。

三个包装模块中的重复 Kotlin 源码必须同步修改。建议新增一个脚本或 Gradle 校验任务，比对独立模块和组合模块对应文件的 SHA-256，防止后续只修一份。

### 8.2 H264 Native context 的统一销毁函数

在 `h264_hevc_decoder_all_in_one_file.cpp` 增加：

```cpp
static void DestroyDecoderContext(H264HevcDecoderContext *context);
```

它负责释放 `SwsContext`、缓存图像 buffer、`AVFrame`、`AVPacket`、`AVCodecContext`，最后 delete context。初始化中任何一步失败都调用该函数；成功 `SetLongField()` 之前，所有资源只由局部 context 所有。release 时先把 Java handle 置 0，再销毁 context；Kotlin monitor 保证此时没有并发 decode。

H264/HEVC init 还必须同步收口临时资源和 JNI 错误路径：

- 校验 SPS/PPS 非空且非空数组，VPS/SEI 为空时按 codec 规则处理；所有 CSD 长度求和使用 checked 64 位运算。
- 直接 `av_mallocz(csdLength + AV_INPUT_BUFFER_PADDING_SIZE)` 分配 `extradata`，再按 offset 用 `GetByteArrayRegion()` 写入，删除 VPS/SPS/PPS/SEI 和 `csd_array` 的多份 `new[]` 临时副本；每次 JNI copy 后检查 pending exception。
- 检查 `av_frame_alloc()`、`av_packet_alloc()`、可选 `bmpFrame`、`FindClass/GetMethodID/NewStringUTF/NewObject`；任一步失败都删除 local ref 并调用统一销毁函数。
- `av_get_pix_fmt_name()` 可能返回 null，构造 Java 字符串前必须检查；Java 返回对象成功创建且没有 pending exception 后，才能写入 `nativeHandle`。

ADPCM decoder 构造函数只有在 `AVCodecContext`、`AVFrame`、`AVPacket` 和 `avcodec_open2()` 全部成功时才能设置 `valid = true`；当前 `av_frame_alloc()/av_packet_alloc()` 未检查却直接标记 valid 的路径必须修复。encoder 保持同样的逐项分配检查和失败回滚。

Demo 中的 `cancel()` + `Thread.sleep(100)` 不能作为生命周期同步。`DecodeH264RawFileByFFMpeg.kt` 和 `DecodeH265RawFileByFFMpeg.kt` 都应保存实际工作任务，并在后台等待任务结束后调用 `close()`；主线程不得 sleep，也不得在 worker 仍运行时 release。

### 8.3 BitmapProcessor 生命周期和 fallback cleanup

涉及：

- `lib-image/src/main/kotlin/com/leovp/image/BitmapProcessor.kt`
- `lib-image/src/main/cpp/BitmapRotateNative.cpp/.h`

整改要求：

1. 批次 B 已把句柄改为私有 `Long`，本批只处理对象状态和并发，不再保留或创建 `DirectByteBuffer` handle。
2. 所有公开操作、`setBitmap()` 和 `close/free()` 在同一个实例锁下执行。除重复 close/free 外，任何方法在 closed 状态调用都抛 `IllegalStateException`，不能 silent no-op。
3. `setBitmap()` 先成功创建新 Native handle，再原子替换字段并释放旧 handle；若新建失败，原图和旧 handle 保持可用。
4. close/free 在锁内先保存旧 handle、把字段清零并标记 closed，再调用 Native free；重复 close/free 直接返回，保证幂等且不再暴露已释放 handle。
5. 当前 `internal fun finalize()` 编译为名称被修饰的方法，不是 JVM finalizer。过渡期改成真正的 `protected fun finalize()`，内部只调用幂等 `close()`；用 `javap` 验证字节码方法名确实为 `finalize()`。finalizer 只作为最后兜底，KDoc 和调用点仍要求 `use {}`，不得依赖 GC 及时释放大图。

内部调用点改成：

```kotlin
BitmapProcessor(bitmap).use { processor ->
    processor.rotateBitmapCw90()
    processor.bitmap
}
```

即使 `bitmap` getter 或转换抛异常，也能及时释放 Native handle。

### 8.4 生命周期测试

- 每个 wrapper：未 init 使用、重复 init、正常 close、重复 close、close 后调用。
- 两个线程对同一实例连续 decode/close 或 encode/close 10,000 次，不允许 tombstone、double free 或卡死。
- 两个独立 decoder 实例并发工作，确认同步是实例级而不是全局串行。
- `BitmapProcessor` 覆盖异常中断下的 `use {}`；补充 1x1、1xN、Nx1、非法 crop、零尺寸 scale。
- finalizer 只验证字节码签名，不以 `System.gc()` 是否及时触发作为功能正确性的必要条件。

## 9. 批次 F：热路径分配和复制优化

本批次只在 A～E 完成后实施，避免性能重构掩盖内存安全和生命周期问题。

### 9.1 YUV

批次 A 已移除绝大多数 JNI 源/目标 Native 副本。`transformI420()` 仍可能为 rotate+mirror+NV12 分配一到两个 scratch buffer。第一阶段保留现有单调用 API 并记录基准；若 1080p/4K 仍有明显 Native allocation churn，再新增不破坏旧 API 的目标缓冲接口：

```kotlin
fun transformI420(
    source: ByteBuffer,
    destination: ByteBuffer,
    ...
)
```

要求 DirectByteBuffer、显式 capacity 校验，并由调用方复用 destination/workspace。旧 `ByteArray` API委托新实现，保持兼容。

### 9.2 H264/HEVC 输出

在 `H264HevcDecoderContext` 缓存 `imageBuffer` 和 `imageBufferCapacity`：只有分辨率或输出格式导致容量增大时才 `av_realloc`，release 时统一释放。每帧仍创建最终 Java `ByteArray`，但不再每帧 `av_malloc/av_free` 一块 3 MB 级 Native buffer。

后续可新增 `decodeInto(encodedBytes, output: ByteBuffer)`，让高帧率调用方复用 DirectByteBuffer；旧 `decode()` 保留并复制，避免直接破坏公开 API。

### 9.3 ADPCM encoder

`AdpcmImaQtEncoder::encode()` 不再每次创建 `out0/out1`。在 `av_frame_make_writable()` 后直接把交错 PCM 拆到 `frame->data[0]`/`frame->data[1]`。有效帧大小按：

```text
frame->nb_samples * channels * av_get_bytes_per_sample(ctx->sample_fmt)
```

按 D6，输入长度不是 frame size 的整数倍时直接抛 `IllegalArgumentException`，不静默丢弃尾部，也不默认维护 remainder buffer。测试必须覆盖少 1 字节、多 1 字节和多帧输入，确认失败不会改变 encoder 的后续可用状态。

### 9.4 性能验收指标

固定同一台真机、同一 release 构建、预热后比较整改前后：

- YUV：1080p rotate+mirror+NV12，连续 1,000 帧；统计平均/95 分位耗时和每帧分配。
- JPEG：12MP quality 90；统计峰值 Native heap、总耗时和输出大小。
- H264：1080p YUV 和 RGBA 各解码 3 分钟；统计 FPS、Native heap 稳定值、GC 次数。
- ADPCM：mono/stereo 连续解码和编码 10 分钟；Native heap 不随帧数增长。

性能补丁的最低验收不是“更快”，而是：无新的线性内存增长、无输出变化、无明显 P95 回退；每项记录整改前后数据。

## 10. 批次 G：FFmpeg AAR 冲突和 JavaCPP ABI

### 10.1 三个 FFmpeg AAR 的互斥关系

当前独立音频版、独立视频版和组合版都发布 `libavcodec.so`/`libavutil.so`，但前两者启用的 codec 集不同。`pickFirsts` 只能选一个重复文件，不能证明被选中的库同时包含 ADPCM、H.264 和 HEVC。

按 D2 不修改 FFmpeg SONAME，不允许任意两个模块同时出现在同一消费项目中：

- 仅 ADPCM 音频功能：`adpcm-ima-qt-codec`。
- 仅 H.264/HEVC 视频功能：`h264-hevc-decoder`。
- 同时需要音频和视频：`adpcm-ima-qt-codec-h264-hevc-decoder`。
- README、各模块说明和 CHANGELOG 都明确写出三选一关系。
- 为三个 published component 增加同一个互斥 capability；Gradle 消费项目如果同时依赖任意两个，应在依赖解析阶段报告 capability conflict，而不是进入 Native 打包阶段。
- 增加三个负向消费端集成测试，分别组合“音频+视频”“音频+组合”“视频+组合”，要求依赖解析或构建明确失败。
- 增加三个正向集成测试，分别验证音频模块、视频模块和组合模块的最小功能。
- Maven/POM 消费端可能不识别 Gradle Module Metadata capability，因此文档约束和 AAR 内容检查仍必须保留。
- 审核并删除会掩盖同名、不同内容 FFmpeg runtime 的 `pickFirsts`；不能把成功任选一个 `.so` 当作兼容方案。

三个模块的 release API/runtime variants 使用完全相同的 capability 坐标，示意配置如下；实际实现应抽到根构建 helper，避免三份脚本漂移：

```kotlin
configurations.matching {
    it.name == "releaseApiElements" || it.name == "releaseRuntimeElements"
}.configureEach {
    outgoing.capability("com.leovp.android:ffmpeg-native-runtime:${project.version}")
}
```

集成 fixture 必须从本地 Maven 仓库按真实发布坐标消费，不能用 `project()` 依赖替代发布元数据验证。负向测试断言 capability conflict，正向测试分别解包/加载三种 AAR；同时检查生成的 `.module` 文件确实包含 capability，而不只检查 Gradle 配置对象。

每个模块仍需用 `readelf -d lib*.so` 检查自身 `SONAME` 和 `NEEDED`，并验证裁剪版只包含目标 codec、组合版同时包含 ADPCM、H.264 和 HEVC。

### 10.2 JavaCPP ABI

按 D3，把 `ffmpeg-javacpp` 的 `abiFilters` 明确收窄为 `arm64-v8a`，只保留 FFmpeg 和 JavaCPP 的 `android-arm64` classifier，并在 README/CHANGELOG 标注当前不支持 armeabi-v7a、x86、x86_64。

发布前解包 AAR，确认 `lib/arm64-v8a/` 同时包含 JavaCPP loader 和 FFmpeg 所需 `.so`，且不存在会让消费者误判为受支持的其它 ABI 目录；在 arm64 真机执行最小 ADPCM 解码和资源释放测试，不只检查 Gradle resolve。

## 11. FFmpeg 源码到发布二进制的闭环

修改 `ffmpeg-sdk/src/main/cpp` 不会自动改变三个当前发布模块。完成 D/E/F/G 中任何 FFmpeg JNI、wrapper 或发布配置改动后，必须执行完整闭环：

1. 确认 `config.sh` 使用 NDK `29.0.14206865`、minSdk 21 和 FFmpeg 8.1.1。
2. 临时把 `ffmpeg-sdk` 加入本地 Gradle 构建，但不要把该 settings 变更提交到远端。
3. 按顺序运行音频、视频、组合构建脚本；每个脚本会重建对应 codec 集并把四 ABI `.so` 复制到目标 wrapper 模块。由于 `prebuilt/` 会被下一种 profile 覆盖，每一步复制后立即校验目标文件。
4. 确认 wrapper 模块中的 Kotlin 副本和 Native 注册签名一致。
5. 对每个 ABI 检查：ELF 架构、`SONAME`、`DT_NEEDED`、导出/隐藏符号和 LOAD segment `0x4000` 对齐。
6. 对三个 AAR 分别解包，确认没有缺文件、旧文件或意外重复 runtime。
7. 执行 `git lfs status`、`git lfs ls-files`、`git lfs fsck`，确认所有新增/替换 `.so` 是有效 LFS 对象而不是损坏指针。
8. 比较修改前后 `.so` SHA-256 和大小；在提交说明中列出变更原因、FFmpeg profile 和 ABI。

建议同时给三个 shell 脚本增加 `set -euo pipefail` 和明确的源/目标存在性检查，防止某个 ABI 构建失败后仍复制上一轮旧产物。

## 12. 建议提交拆分

为便于 Claude Code 二次审查和问题回滚，避免把所有 Native 变更压成一个提交：

1. `fix(yuv): validate native buffers and strides`
2. `fix(lib-image): validate bitmap operations and hide native handles`
3. `fix(jpeg): validate and stream native compression`
4. `fix(ffmpeg-javacpp): own decoder frame resources`
5. `fix(ffmpeg): pad decoder input packets`
6. `fix(native): serialize handle lifecycle`
7. `perf(native): reduce frame buffer churn`
8. `fix(ffmpeg): enforce codec artifact exclusivity`
9. `fix(ffmpeg-javacpp): limit published ABI to arm64`
10. `test(native): add sanitizer and device regression coverage`
11. `docs: document native runtime compatibility and breaking API`

包含 `.so` 的提交应与生成它的 C++/构建脚本提交紧邻，并在提交正文写清生成命令；不要只提交 C++ 源码，也不要只提交无法追溯来源的二进制。

## 13. 验证矩阵

### 13.1 构建与静态检查

在允许本地构建的环境中，对刚修改的任务使用 `--rerun-tasks`：

```bash
./gradlew \
  :lib-image:assembleDebug \
  :yuv:assembleDebug \
  :jpeg:assembleDebug \
  :h264-hevc-decoder:assembleDebug \
  :adpcm-ima-qt-codec:assembleDebug \
  :adpcm-ima-qt-codec-h264-hevc-decoder:assembleDebug \
  :ffmpeg-javacpp:assembleDebug \
  --rerun-tasks --continue

./gradlew ktlintCheck detekt --rerun-tasks
./gradlew testDebugUnitTest --rerun-tasks
```

新增设备测试后运行对应 `connectedDebugAndroidTest`；如果测试放入 demo，则使用 `connectedDevDebugAndroidTest`。A～C 的非法输入测试必须用包含新 Native 二进制的 APK 真正执行，不能只停留在 Kotlin/JVM 层 mock。

### 13.2 设备和 ABI

- arm64 真机：必测，覆盖所有 Native 功能、并发 close、长时间循环和内存曲线。
- armeabi-v7a：验证三个预编译 FFmpeg wrapper AAR；`ffmpeg-javacpp` 按 D3 不测试、不声明支持。
- x86、x86_64：验证三个预编译 FFmpeg wrapper AAR；`ffmpeg-javacpp` 按 D3 不测试、不声明支持。
- 16KB 页设备：验证所有发布 `.so` 可加载，不能只看 wrapper 自身；FFmpeg runtime、JavaCPP runtime、`libc++_shared.so` 全部检查。

### 13.3 稳定性工具

- 至少一种可用的 ASan/HWASan arm64 构建覆盖 Bitmap、YUV、JPEG 以及损坏 H.264、HEVC、ADPCM 输入；没有 sanitizer 运行证据时不能关闭 CRITICAL 项。
- `adb shell dumpsys meminfo <package>` 定时采样 Native Heap。
- tombstone/logcat 中不得出现 `SIGSEGV`、`SIGABRT`、double free、FORTIFY 或 Scudo 报错。
- 长测结束后主动 close 全部对象，内存应回落到稳定区间；不要把 GC 后偶然下降当作唯一证据。

## 14. 完成定义

只有同时满足以下条件，整改才可标记完成：

1. A～C 的全部 CRITICAL 问题有源码补丁、设备回归和至少一种 ASan/HWASan 运行证据；任何一项缺失都不得发布包含对应模块的新版本。
2. D～E 的全部 HIGH 资源所有权和生命周期问题有源码补丁及并发/失败路径回归。
3. 所有 JNI 入口在非法参数下抛约定的 Java 异常，不返回违反 Kotlin 非空契约的 null，也不发生进程级崩溃。
4. 所有 Native handle 有唯一所有者、幂等 close、并发互斥和初始化失败回滚。
5. JPEG、JavaCPP frame、FFmpeg packet 和 Bitmap handle 的失败路径无可复现线性泄漏。
6. F 的性能整改无输出变化、无线性内存增长且无明显 P95 回退，并记录同机型前后数据。
7. C++ 源码、Kotlin JNI 声明和注册表签名一致；三个 FFmpeg wrapper 的四 ABI `.so` 与源码一致，`ffmpeg-javacpp` 只发布并声明 arm64。
8. FFmpeg 二进制通过 LFS、ELF、SONAME、codec 能力和 16KB 对齐检查，G 的三选一冲突测试和正向消费测试通过。
9. D1～D6 的已确认方案全部落地，CHANGELOG/README 与实际行为一致；D1 明确标注为 breaking change。
10. 构建、静态检查、单元测试、设备测试和未覆盖项分别记录；不得用“assemble 成功”替代真机结论。

## 15. 统一方案实施后二次审查清单

请二次审查时重点回答以下问题，而不是直接实现：

1. YUV 的每个 size/offset/stride 计算是否在取得指针前完成，并且使用 64 位防溢出？
2. `nv12ToI420()` 在 90/270 度时，目标 Y/U/V stride 是否都基于旋转后的宽度？
3. `i420ToRgb24()` 是否使用 `width * 3` 行 stride，而不是总数组长度？
4. 完整 `YUV_420_888` API 是否校验每个 plane 的 row/pixel stride、position、remaining，并覆盖 direct/heap/read-only buffer？
5. JNI pending exception、OOM 和 libyuv 非零返回是否都有立即退出路径，非空 Kotlin API 是否仍可能收到 null？
6. Bitmap 的负数、溢出、1 像素边界、stride、`new(std::nothrow)` 和 Java exception 路径是否完整？公开 DirectByteBuffer getter/setter 是否已经从 binary API 删除？
7. JPEG 的格式/getInfo/lock/size 校验是否在读像素前完成？`setjmp/longjmp` 是否会绕过资源清理，所有 file/compressor/row/path/bitmap lock 是否恰好清理一次？
8. JavaCPP 的 packet/frame/context 是否恰好释放一次？mono 是否还会访问 `extended_data(1)`，packed/planar 输出长度是否正确？
9. H.264/HEVC 和 ADPCM packet 是否由 `av_new_packet()` 或等价方式保证 FFmpeg padding 全部为 0？
10. release 与 decode/encode 是否通过同一个实例锁互斥？是否仍存在 handle 先读出、随后被另一线程删除的窗口？
11. H264 重复 init 是否会覆盖旧 handle？初始化中任一步失败是否回滚所有已分配资源？
12. 性能优化是否引入共享 scratch buffer 的跨实例/跨线程竞态？缓存容量是否有上限和 release？
13. 三个 FFmpeg AAR 是否通过 capability 和消费端测试落实三选一关系，并移除了会用 `pickFirst` 掩盖错误组合的配置？
14. `ffmpeg-javacpp` 的 ABI 声明、依赖 classifier、AAR 内容和真机矩阵是否都只包含 arm64？
15. 所有修改过的 `ffmpeg-sdk` 源码是否已经真实反映到三个发布模块的四 ABI `.so`，并通过 LFS 和 16KB 对齐校验？

二次审查若提出替代方案，应同时说明公开 API/ABI、minSdk 21、三个预编译 wrapper 的四 ABI、JavaCPP 的 arm64 限定、AAR 体积、线程安全和二进制重建成本，不能只评价单个 C++ 函数。
