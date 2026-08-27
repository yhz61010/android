# Native 模块代码级整改方案（2026-08-27）

## 1. 文档目的

本文基于 `review/native-modules` 分支的 Native 模块代码审查结果，给出可供实现和二次审查的代码级修改方案。本文只描述整改方案，不修改生产代码，也不把 Gradle 构建通过等同于 Native 运行时问题已经解决。

- 审查基线：`7f28edf8fb7b238d2a7a3500ffac4fb01f265d4c`
- 远端主线：`origin/master`
- 直接构建 Native 的模块：`lib-image`、`yuv`、`jpeg`
- 发布预编译 Native 二进制的模块：`h264-hevc-decoder`、`adpcm-ima-qt-codec`、`adpcm-ima-qt-codec-h264-hevc-decoder`
- JavaCPP 模块：`ffmpeg-javacpp`
- FFmpeg JNI 源码和二进制生成链：未纳入日常 Gradle 构建的 `ffmpeg-sdk`
- 不在本轮逐行修改的上游源码：FFmpeg、libjpeg-turbo、libyuv；只修改本项目的构建参数、JNI 集成和调用方式

## 2. 信息充分性与实施门槛

现有源码、JNI 注册表、Gradle 发布配置、FFmpeg 构建脚本、预编译 `.so` 和本地 FFmpeg 头文件已经足以确认内存越界、资源泄漏、输入 padding、生命周期竞态、重复分配和 ABI 声明不一致等问题，也足以制定前四批安全修复。

维护者已确认以下公开 API、运行库组合和输入处理决策。实现者应按“已确认方案”执行，不再自行切换到备选设计：

| 决策 ID | 问题 | 已确认方案 |
|---|---|---|
| D1 | `BitmapProcessor.bitmapByteBuffer` 当前是公开可写属性 | 本轮直接删除该公开属性，改为私有 Native handle；接受源码和二进制不兼容，并在 CHANGELOG/发布说明中标为 breaking change |
| D2 | 三个 FFmpeg AAR 的使用关系 | 三者严格三选一：只要音频使用 `adpcm-ima-qt-codec`，只要视频使用 `h264-hevc-decoder`，同时需要音视频使用组合模块；不实施 suffix/SONAME 隔离 |
| D3 | `ffmpeg-javacpp` 声明四 ABI，实际只依赖 `android-arm64` classifier | 当前只支持 `arm64-v8a`，Gradle、AAR 内容和文档统一收窄到 arm64 |
| D4 | `android420ToI420()` 无法表达完整 `YUV_420_888` plane 信息 | 增加完整 plane API，显式接收 Y/U/V buffer、row stride、pixel stride、宽高和变换参数；旧单数组 API保留但废弃 |
| D5 | 非法输入和生命周期错误如何暴露 | 参数非法抛 `IllegalArgumentException`，Native 执行失败抛 `IllegalStateException`，重复 `close/release` 幂等，close 后继续操作抛 `IllegalStateException` |
| D6 | JavaCPP mono 返回值和 ADPCM 非整帧输入 | mono 返回 `leftBytes to ByteArray(0)`；非完整 frame 输入抛参数异常，不静默丢尾部，也不默认维护 remainder buffer |

## 3. 问题与整改批次总览

| ID | 级别 | 模块 | 问题 | 批次 |
|---|---|---|---|---|
| NAT-YUV-01 | HIGH | yuv | `i420ToRgb24()` 把总长度当行 stride，导致跨行写越界 | A |
| NAT-YUV-02 | HIGH | yuv | `nv12ToI420()` 在 90/270 度旋转后仍使用原宽度作为目标 stride | A |
| NAT-YUV-03 | HIGH | yuv | 大多数 JNI API 缺少尺寸、数组长度、枚举和溢出检查 | A |
| NAT-JPEG-01 | HIGH | jpeg | libjpeg 错误路径泄漏 compressor、文件、路径字符串和整帧 RGB 缓冲 | B |
| NAT-JCPP-01 | HIGH | ffmpeg-javacpp | 每次成功解码泄漏 `AVFrame`，单声道访问不存在的右声道 | C |
| NAT-FFMPEG-01 | HIGH | ffmpeg-sdk | H.264/HEVC、ADPCM 输入没有 FFmpeg 要求的 64 字节零 padding | C |
| NAT-LIFE-01 | HIGH | FFmpeg wrappers / lib-image | init、decode/encode、release 并发可造成泄漏、UAF 或 double free | D |
| NAT-BMP-01 | MEDIUM | lib-image | 无效 `finalize`、公开 Native 句柄、stride 和参数校验问题 | D |
| NAT-PERF-01 | MEDIUM | 多模块 | 热路径整帧临时分配和跨 Java/Native 重复复制 | E |
| NAT-JPEG-02 | MEDIUM | jpeg | 忽略 Bitmap stride，`optimize` 设置被 defaults 覆盖 | B |
| NAT-PKG-01 | MEDIUM | 三个 FFmpeg AAR | 同名、不同内容的 FFmpeg `.so` 存在打包和加载冲突 | F |
| NAT-ABI-01 | MEDIUM | ffmpeg-javacpp | Gradle ABI 声明与实际 classifier 不一致 | F |

建议按 A → B → C → D → E → F 的顺序实施。A～D 先关闭越界、泄漏和生命周期风险；E 在安全边界稳定后做性能优化；F 涉及发布结构，应最后单独评审。

基线源码定位如下，行号以 `7f28edf8f` 为准，修改后应按函数名而不是旧行号继续追踪：

| ID | 基线位置 |
|---|---|
| NAT-YUV-01 | `yuv/src/main/cpp/YuvConvert.cpp:331-345`、`YuvUtilNative.cpp:422-437` |
| NAT-YUV-02 | `yuv/src/main/cpp/YuvConvert.cpp:244-272`、`YuvUtilNative.cpp:335-350` |
| NAT-YUV-03 | `yuv/src/main/cpp/YuvUtilNative.cpp:11-120,236-438`，异常后继续执行见 `:381-384` |
| NAT-JPEG-01/02 | `jpeg/src/main/cpp/JPEGNative.cpp:40-191` |
| NAT-JCPP-01 | `ffmpeg-javacpp/src/main/kotlin/com/leovp/ffmpeg/javacpp/audio/adpcm/AdpcmImaQTDecoder.kt:49-84` |
| NAT-FFMPEG-01 | `ffmpeg-sdk/src/main/cpp/h264_hevc_decoder/h264_hevc_decoder_all_in_one_file.cpp:226-245`、`adpcm_ima_qt_decoder/native_adpcm_ima_qt_decoder.cpp:47-69` |
| NAT-LIFE-01 | H264 Native `:65-224`；ADPCM decoder JNI `:19-69`；ADPCM encoder JNI `:19-62` |
| NAT-BMP-01 | `lib-image/src/main/kotlin/com/leovp/image/BitmapProcessor.kt:33,62-145`、`lib-image/src/main/cpp/BitmapRotateNative.cpp:18-43,146-236,239-380` |
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
8. `NewByteArray()`、`GetByteArrayElements()` 返回空时立即退出并保留 JVM 已设置的 OOM。

`android420ToI420()` 当前只接收一个扁平数组，却没有 row stride 和三个 plane offset，无法无歧义表达通用 `YUV_420_888`。按 D4 实施：

- 在 Kotlin 上将旧方法标记为废弃，说明不要用于任意 `Image.Plane` 数据。
- 旧 JNI 方法只接受可严格验证的 planar I420 布局，即 `pixelStrideUV == 1`。
- 新增完整 API，参数至少包含 Y/U/V 三个 buffer、各自 row stride 和 pixel stride；支持 direct/heap `ByteBuffer`，无法取得 direct address 时使用有界 fallback copy，不猜测平面布局。
- 新 API支持 `pixelStride == 1/2` 的常见 planar/semiplanar `YUV_420_888`；若 U/V pixel stride 不相同，则走逐 plane 提取再变换路径，不能把不等 stride 强行传给只接受一个 UV pixel stride 的 libyuv API。

### 4.3 消除源和目标整帧 Native 副本

旧入口普遍执行“Java 输入 → `new[]` 源数组 → `new[]` 目标数组 → Java 输出”。改成：

1. 校验输入和目标长度。
2. 直接 `NewByteArray(outputLength)` 创建最终 Java 输出。
3. 通过 `GetByteArrayElements()` 分别取得 source/output 地址；不要像当前 `transformI420()` 一样嵌套持有两个 `GetPrimitiveArrayCritical()` 指针。
4. 只在两个数组均成功取得后调用 libyuv。
5. 所有退出路径按相反顺序释放；source 使用 `JNI_ABORT`，output 成功时使用 `0`。
6. 取得两个元素指针后只执行同步 libyuv 计算，再立即 release；中间不执行其它 JNI 调用、文件 I/O或锁等待。

建议抽取小型 RAII guard，析构函数只调用 `ReleaseByteArrayElements()`；该 guard 不使用 C++ exception。`GetByteArrayElements()` 仍可能由 JVM 选择复制，但可消除代码中必然发生的两次显式 `new[]` 整帧分配，并避免 critical section 违规。当前 CMake 关闭异常，所有 scratch buffer 继续使用 `new(std::nothrow)` 或 `malloc` 并显式检查。

### 4.4 修复两个确定越界点

`YuvConvert.cpp` 的底层函数应返回 libyuv 的 `int` 状态码，不再是 `void`；JNI 层检查非零返回值并抛 `IllegalStateException`。

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
- 连续执行 1080p rotate/mirror/scale 各 1,000 次，记录 Native heap 峰值和稳定值；测试结束后 Native heap 不应线性增长。

## 5. 批次 B：JPEG 错误路径、stride 和峰值内存

### 5.1 涉及文件

- `jpeg/src/main/cpp/JPEGNative.cpp`
- `jpeg/src/main/kotlin/com/leovp/jpeg/JPEGUtil.kt`
- 新增 `jpeg/src/androidTest/.../JPEGUtilInstrumentedTest.kt`

### 5.2 重构资源状态和单一清理出口

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
5. 返回失败码；JNI 外层删除可能已生成的残缺文件。

JNI 外层同样使用单一 cleanup，确保任何路径都会：

- 成功 lock 后执行 `AndroidBitmap_unlockPixels()`；
- 成功取得路径后执行 `ReleaseStringUTFChars()`；
- 不再保留整帧 `w * h * 3` 的 `tempData`；
- 保留 JVM pending exception，不在异常状态继续压缩。

### 5.3 改为逐行 RGBA→RGB

只分配 `width * 3` 字节 row buffer。第 `y` 行源地址必须按 `AndroidBitmapInfo.stride` 计算：

```cpp
const auto *sourceRow =
        static_cast<const uint8_t *>(bitmapPixels) + y * bitmapInfo.stride;
```

逐像素写入 RGB row，然后立即 `jpeg_write_scanlines()`。这把 12MP 图片的额外 Native RGB 缓冲从约 36 MB 降至一行大小，并正确处理非紧密 Bitmap stride。

在 lock 前检查：

- `bitmap`、`outFilePath` 非空；
- `AndroidBitmap_getInfo()` 返回 0；
- format 为 `ANDROID_BITMAP_FORMAT_RGBA_8888`；
- width/height 非零，`width * 3` 不溢出；
- quality 在 `0..100`，否则抛 `IllegalArgumentException`；
- `AndroidBitmap_lockPixels()` 返回 0；
- `GetStringUTFChars()`、row buffer 分配成功。

### 5.4 修复压缩参数顺序

顺序必须为：

```cpp
jpeg_set_defaults(&compressor);
compressor.optimize_coding = optimize == JNI_TRUE;
compressor.arith_code = FALSE;
jpeg_set_quality(&compressor, quality, TRUE);
```

不要根据 `optimize == false` 自动启用 arithmetic coding；`optimize` 参数只控制 Huffman table 优化，避免生成兼容性较差且与参数语义无关的 arithmetic JPEG。

### 5.5 JPEG 测试

- 使用小图和 12MP RGBA_8888 图压缩，验证文件可解码、宽高和关键颜色正确。
- 传入不存在/不可写目录，循环失败 100 次，确认文件描述符和 Native heap 不线性增长。
- 传入非法 quality、非 RGBA_8888 或 recycled Bitmap，确认稳定失败且不会泄漏 lock/path。
- 对 `optimize=true/false` 各压缩一次，确认两者均可解码；只比较参数生效和合理性能，不要求文件一定更小。
- 用 Native Memory Profiler 对比整改前后 12MP 峰值，目标是移除约 `width * height * 3` 的额外缓冲。

## 6. 批次 C：FFmpeg 输入 padding 和 JavaCPP 资源所有权

### 6.1 JavaCPP ADPCM 解码器

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

每次解码：

1. `av_packet_unref(packet)`、`av_frame_unref(frame)`。
2. 调用 `av_new_packet(packet, adpcmBytes.size)`；该 API 负责分配 FFmpeg packet payload。复制完成后仍显式确认或归零 `data + size` 开始的 `AV_INPUT_BUFFER_PADDING_SIZE` 字节，避免把具体 JavaCPP/FFmpeg 版本的初始化细节当成隐含前提。
3. 把 Java 数据复制到 `packet.data()`，不要再使用 `BytePointer(*adpcmBytes)` 的 spread 构造。
4. `avcodec_send_packet()` 成功后再 receive；在 `finally` 中 `av_packet_unref()`。
5. 输出有效 PCM 长度用 `frame.nb_samples() * av_get_bytes_per_sample(frame.format())`，不要使用可能包含对齐 padding 的 `linesize(0)`。
6. stereo 才访问 `extended_data(1)`；mono 返回 `leftBytes to ByteArray(0)`，并在 KDoc 写明右声道为空。
7. `close()` 先 free frame、packet，最后 free context，并把字段置空；重复调用无效果；close 后 decode 抛 `IllegalStateException`。

### 6.2 H.264/HEVC Native 输入

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

### 6.3 ADPCM Native 输入

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

### 6.4 padding 回归

- H.264、HEVC 分别覆盖正常帧、截断帧、只有 start code 的帧、随机损坏帧；期望返回失败或空结果，不能 SIGSEGV。
- ADPCM 覆盖正确 34/68 字节以及边界损坏内容。
- 在 arm64 HWASan/ASan 可用环境执行损坏输入循环；重点观察 packet 末尾越界读取。
- 通过本地 FFmpeg 头文件再次确认 `AV_INPUT_BUFFER_PADDING_SIZE`，测试代码不要硬编码 64 以外的自定义常量。

## 7. 批次 D：Native handle 生命周期和 BitmapProcessor

### 7.1 Kotlin/Native 生命周期统一

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

### 7.2 H264 Native context 的统一销毁函数

在 `h264_hevc_decoder_all_in_one_file.cpp` 增加：

```cpp
static void DestroyDecoderContext(H264HevcDecoderContext *context);
```

它负责释放 `SwsContext`、缓存图像 buffer、`AVFrame`、`AVPacket`、`AVCodecContext`，最后 delete context。初始化中任何一步失败都调用该函数；成功 `SetLongField()` 之前，所有资源只由局部 context 所有。release 时先把 Java handle 置 0，再销毁 context；Kotlin monitor 保证此时没有并发 decode。

Demo 中的 `cancel()` + `Thread.sleep(100)` 不能作为生命周期同步。`DecodeH264RawFileByFFMpeg.kt` 应保存实际工作任务，并在后台等待任务结束后调用 `close()`；主线程不得 sleep，也不得在 worker 仍运行时 release。

### 7.3 BitmapProcessor 句柄和 fallback cleanup

涉及：

- `lib-image/src/main/kotlin/com/leovp/image/BitmapProcessor.kt`
- `lib-image/src/main/cpp/BitmapRotateNative.cpp/.h`

整改要求：

1. 按 D1 直接删除公开 `bitmapByteBuffer`，新增只供类内部使用的私有 handle；公开 API 不再暴露任何可伪造的 DirectByteBuffer。该项是明确接受的源码和二进制不兼容变更，必须写入 CHANGELOG/发布说明。
2. 所有操作、`setBitmap()` 和 `close/free()` 在同一个锁下执行；close 先取出并清空 Kotlin handle，再调用 Native free，保证重复 close 幂等。
3. 当前 `internal fun finalize()` 编译为名称被修饰的方法，不是 JVM finalizer。过渡期改成真正的 `protected fun finalize()`，内部只调用幂等 `close()`；用 `javap` 验证字节码方法名确实为 `finalize()`。finalizer 只作为最后兜底，KDoc 和调用点仍要求 `use {}`。
4. `SetBitmapData()`、`GetBitmapFromSavedBitmapData()` 按 `AndroidBitmapInfo.stride` 逐行复制，不得把整张图假定成 `width * 4` 紧密布局。
5. `NewDirectByteBuffer()` capacity 使用 `sizeof(JniBitmap)`，并检查返回值；失败时立即释放刚创建的 pixel buffer 和 `JniBitmap`。
6. crop 校验 `0 <= left < right <= width`、`0 <= top < bottom <= height`；scale 宽高必须大于 0，乘法必须防溢出。
7. bilinear scale 正确处理原图宽或高为 1 的情况，所有相邻像素索引通过 clamp 得到，禁止 `xTopLeft--`/`yTopLeft--` 变成负数。
8. `AndroidBitmap_*`、`FindClass/GetMethodID/CallStaticObjectMethod` 和 Native 分配结果全部检查；发生 Java exception 时停止后续 JNI 调用。

内部调用点改成：

```kotlin
BitmapProcessor(bitmap).use { processor ->
    processor.rotateBitmapCw90()
    processor.bitmap
}
```

即使 `bitmap` getter 或转换抛异常，也能及时释放 Native handle。

### 7.4 生命周期测试

- 每个 wrapper：未 init 使用、重复 init、正常 close、重复 close、close 后调用。
- 两个线程对同一实例连续 decode/close 或 encode/close 10,000 次，不允许 tombstone、double free 或卡死。
- 两个独立 decoder 实例并发工作，确认同步是实例级而不是全局串行。
- `BitmapProcessor` 覆盖异常中断下的 `use {}`；补充 1x1、1xN、Nx1、非法 crop、零尺寸 scale。
- finalizer 只验证字节码签名，不以 `System.gc()` 是否及时触发作为功能正确性的必要条件。

## 8. 批次 E：热路径分配和复制优化

本批次只在 A～D 完成后实施，避免性能重构掩盖内存安全问题。

### 8.1 YUV

批次 A 已移除绝大多数 JNI 源/目标 Native 副本。`transformI420()` 仍可能为 rotate+mirror+NV12 分配一到两个 scratch buffer。第一阶段保留现有单调用 API并记录基准；若 1080p/4K 仍有明显 Native allocation churn，再新增不破坏旧 API 的目标缓冲接口：

```kotlin
fun transformI420(
    source: ByteBuffer,
    destination: ByteBuffer,
    ...
)
```

要求 DirectByteBuffer、显式 capacity 校验，并由调用方复用 destination/workspace。旧 `ByteArray` API委托新实现，保持兼容。

### 8.2 H264/HEVC 输出

在 `H264HevcDecoderContext` 缓存 `imageBuffer` 和 `imageBufferCapacity`：只有分辨率或输出格式导致容量增大时才 `av_realloc`，release 时统一释放。每帧仍创建最终 Java `ByteArray`，但不再每帧 `av_malloc/av_free` 一块 3 MB 级 Native buffer。

后续可新增 `decodeInto(encodedBytes, output: ByteBuffer)`，让高帧率调用方复用 DirectByteBuffer；旧 `decode()` 保留并复制，避免直接破坏公开 API。

### 8.3 ADPCM encoder

`AdpcmImaQtEncoder::encode()` 不再每次创建 `out0/out1`。在 `av_frame_make_writable()` 后直接把交错 PCM 拆到 `frame->data[0]`/`frame->data[1]`。有效帧大小按：

```text
frame->nb_samples * channels * av_get_bytes_per_sample(ctx->sample_fmt)
```

定义并记录非整帧输入策略：推荐本轮严格要求输入长度是 frame size 的整数倍并抛参数异常，不静默丢弃尾部。若业务需要流式任意分片，应在 Kotlin 层增加有界 remainder buffer，而不是 Native 内无界累积。

### 8.4 性能验收指标

固定同一台真机、同一 release 构建、预热后比较整改前后：

- YUV：1080p rotate+mirror+NV12，连续 1,000 帧；统计平均/95 分位耗时和每帧分配。
- JPEG：12MP quality 90；统计峰值 Native heap、总耗时和输出大小。
- H264：1080p YUV 和 RGBA 各解码 3 分钟；统计 FPS、Native heap 稳定值、GC 次数。
- ADPCM：mono/stereo 连续解码和编码 10 分钟；Native heap 不随帧数增长。

性能补丁的最低验收不是“更快”，而是：无新的线性内存增长、无输出变化、无明显 P95 回退；每项记录整改前后数据。

## 9. 批次 F：FFmpeg AAR 冲突和 JavaCPP ABI

### 9.1 三个 FFmpeg AAR 的互斥关系

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

每个模块仍需用 `readelf -d lib*.so` 检查自身 `SONAME` 和 `NEEDED`，并验证裁剪版只包含目标 codec、组合版同时包含 ADPCM、H.264 和 HEVC。

### 9.2 JavaCPP ABI

按 D3，把 `ffmpeg-javacpp` 的 `abiFilters` 明确收窄为 `arm64-v8a`，只保留 FFmpeg 和 JavaCPP 的 `android-arm64` classifier，并在 README/CHANGELOG 标注当前不支持 armeabi-v7a、x86、x86_64。

发布前解包 AAR，确认 `lib/arm64-v8a/` 同时包含 JavaCPP loader 和 FFmpeg 所需 `.so`，且不存在会让消费者误判为受支持的其它 ABI 目录；在 arm64 真机执行最小 ADPCM 解码和资源释放测试，不只检查 Gradle resolve。

## 10. FFmpeg 源码到发布二进制的闭环

修改 `ffmpeg-sdk/src/main/cpp` 不会自动改变三个当前发布模块。完成 C/D/E/F 中任何 FFmpeg JNI 改动后，必须执行完整闭环：

1. 确认 `config.sh` 使用 NDK `29.0.14206865`、minSdk 21 和 FFmpeg 8.1.1。
2. 临时把 `ffmpeg-sdk` 加入本地 Gradle 构建，但不要把该 settings 变更提交到远端。
3. 按顺序运行音频、视频、组合构建脚本；每个脚本会重建对应 codec 集并把四 ABI `.so` 复制到目标 wrapper 模块。由于 `prebuilt/` 会被下一种 profile 覆盖，每一步复制后立即校验目标文件。
4. 确认 wrapper 模块中的 Kotlin 副本和 Native 注册签名一致。
5. 对每个 ABI 检查：ELF 架构、`SONAME`、`DT_NEEDED`、导出/隐藏符号和 LOAD segment `0x4000` 对齐。
6. 对三个 AAR 分别解包，确认没有缺文件、旧文件或意外重复 runtime。
7. 执行 `git lfs status`、`git lfs ls-files`、`git lfs fsck`，确认所有新增/替换 `.so` 是有效 LFS 对象而不是损坏指针。
8. 比较修改前后 `.so` SHA-256 和大小；在提交说明中列出变更原因、FFmpeg profile 和 ABI。

建议同时给三个 shell 脚本增加 `set -euo pipefail` 和明确的源/目标存在性检查，防止某个 ABI 构建失败后仍复制上一轮旧产物。

## 11. 建议提交拆分

为便于 Claude Code 二次审查和问题回滚，避免把所有 Native 变更压成一个提交：

1. `fix(yuv): validate native buffers and strides`
2. `fix(jpeg): clean up native compression resources`
3. `fix(ffmpeg-javacpp): own decoder frame resources`
4. `fix(ffmpeg): pad decoder input packets`
5. `fix(native): serialize handle lifecycle`
6. `fix(lib-image): harden bitmap processor handles`
7. `perf(native): reduce frame buffer churn`
8. `fix(ffmpeg): enforce codec artifact exclusivity`
9. `fix(ffmpeg-javacpp): limit published ABI to arm64`
10. `test(native): add device regression coverage`
11. `docs: document native runtime compatibility`

包含 `.so` 的提交应与生成它的 C++/构建脚本提交紧邻，并在提交正文写清生成命令；不要只提交 C++ 源码，也不要只提交无法追溯来源的二进制。

## 12. 验证矩阵

### 12.1 构建与静态检查

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

新增设备测试后运行对应 `connectedDebugAndroidTest`；如果测试放入 demo，则使用 `connectedDevDebugAndroidTest`。

### 12.2 设备和 ABI

- arm64 真机：必测，覆盖所有 Native 功能、并发 close、长时间循环和内存曲线。
- armeabi-v7a：验证三个预编译 FFmpeg wrapper AAR；`ffmpeg-javacpp` 按 D3 不测试、不声明支持。
- x86、x86_64：验证三个预编译 FFmpeg wrapper AAR；`ffmpeg-javacpp` 按 D3 不测试、不声明支持。
- 16KB 页设备：验证所有发布 `.so` 可加载，不能只看 wrapper 自身；FFmpeg runtime、JavaCPP runtime、`libc++_shared.so` 全部检查。

### 12.3 稳定性工具

- debug/HWASan 构建覆盖损坏 YUV、H.264、HEVC、ADPCM 输入。
- `adb shell dumpsys meminfo <package>` 定时采样 Native Heap。
- tombstone/logcat 中不得出现 `SIGSEGV`、`SIGABRT`、double free、FORTIFY 或 Scudo 报错。
- 长测结束后主动 close 全部对象，内存应回落到稳定区间；不要把 GC 后偶然下降当作唯一证据。

## 13. 完成定义

只有同时满足以下条件，整改才可标记完成：

1. A～D 的 HIGH 问题均有源码补丁和对应回归。
2. 所有 JNI 入口在非法参数下是 Java 异常或定义明确的失败返回，不发生进程级崩溃。
3. 所有 Native handle 有唯一所有者、幂等 close、并发互斥和初始化失败回滚。
4. JPEG、JavaCPP frame、FFmpeg packet 和 Bitmap handle 的失败路径无可复现线性泄漏。
5. C++ 源码、Kotlin JNI 声明、注册表签名和提交的四 ABI `.so` 完全一致。
6. FFmpeg 二进制通过 LFS、ELF、SONAME、codec 能力和 16KB 对齐检查。
7. D1～D6 的已确认方案全部落地，CHANGELOG/README 与实际行为一致；D1 明确标注为 breaking change。
8. 构建、静态检查、单元测试、设备测试和未覆盖项分别记录；不得用“assemble 成功”替代真机结论。

## 14. 给 Claude Code 的二次审查清单

请二次审查时重点回答以下问题，而不是直接实现：

1. YUV 的每个 size/offset/stride 计算是否在取得指针前完成，并且使用 64 位防溢出？
2. `nv12ToI420()` 在 90/270 度时，目标 Y/U/V stride 是否都基于旋转后的宽度？
3. `i420ToRgb24()` 是否使用 `width * 3` 行 stride，而不是总数组长度？
4. JNI pending exception、OOM 和 libyuv 非零返回是否都有立即退出路径？
5. JPEG 的 `setjmp/longjmp` 是否会绕过任何依赖 C++ 析构的资源？所有 file/compressor/row/path/bitmap lock 是否都能清理？
6. JavaCPP 的 packet/frame/context 是否恰好释放一次？mono 是否还会访问 `extended_data(1)`？
7. H.264/HEVC 和 ADPCM packet 是否由 `av_new_packet()` 或等价方式保证 FFmpeg padding 全部为 0？
8. release 与 decode/encode 是否通过同一个实例锁互斥？是否仍存在 handle 先读出、随后被另一线程删除的窗口？
9. H264 重复 init 是否会覆盖旧 handle？初始化中任一步失败是否回滚所有已分配资源？
10. Bitmap stride、crop、scale、1 像素边界和公开 handle 兼容策略是否完整？
11. 性能优化是否引入共享 scratch buffer 的跨实例/跨线程竞态？缓存容量是否有上限和 release？
12. 三个 FFmpeg AAR 是否通过 capability 和消费端测试落实三选一关系，并移除了会用 `pickFirst` 掩盖错误组合的配置？
13. `ffmpeg-javacpp` 的 ABI 声明、依赖 classifier、AAR 内容和真机/模拟器矩阵是否一致？
14. 所有修改过的 `ffmpeg-sdk` 源码是否已经真实反映到三个发布模块的四 ABI `.so`，并通过 LFS 和 16KB 对齐校验？

二次审查若提出替代方案，应同时说明公开 API/ABI、minSdk 21、四 ABI、AAR 体积、线程安全和二进制重建成本，不能只评价单个 C++ 函数。
