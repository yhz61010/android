# Native 模块独立代码审查（2026-08-27，_cc）

> 本文是对 `lib-image`、`yuv`、`jpeg` 三个激活 native 模块（C/C++ + JNI 边界）的**独立盲审**结论。
> 审查全程未参考 Codex 的 `2026-08-27-native-modules-remediation-plan-zh.md`，仅基于源码逐行核对。
> 审查方式：三个独立 `cpp-reviewer` 代理分别盲审一个模块，均被禁止读取 `00-documents/`；本文为综合结论。

## 范围

独立枚举得出的 native 源码范围（与 `CMakeLists.txt` 集合一致；`x264` 未激活、ffmpeg 组只打包预编译 `.so` 无可编译源码，均排除）：

| 模块 | 审查文件 |
|------|---------|
| lib-image | `BitmapRotateNative.cpp/.h`、`CMakeLists.txt`、`BitmapProcessor.kt`（JNI 边界） |
| yuv | `YuvUtilNative.cpp`、`YuvConvert.cpp/.h`、`CMakeLists.txt`、`YuvUtil.kt`（`libyuv.h` 视为可信第三方） |
| jpeg | `JPEGNative.cpp`、`CMakeLists.txt`、`JPEGUtil.kt`（libjpeg-turbo 头视为可信第三方） |

## 总体结论

**三个模块均为 BLOCK**（各含 CRITICAL 内存安全问题）。共性根因高度一致：

1. **JNI 边界缺输入校验**（尺寸、缓冲区长度、位图格式）——最主要的 CRITICAL 来源。
2. **尺寸运算 32 位整型溢出**（`w*h*channels` 未用 `int64_t`/`size_t`）→ 堆溢出。
3. **JNI 调用后缺 null 检查 / 异常检查**（`NewByteArray`、`FindClass`、`Call*Method`）。
4. **缺 RAII**（裸 `new[]/malloc`）→ 错误/longjmp 路径泄漏。
5. **Kotlin 非空返回契约被 native 的 `NULL` 返回破坏** → 静默失败。
6. **忽略行 stride**（假设紧凑排布 `width*4`）。

正面共性：三个 `CMakeLists.txt` **均正确设置 16KB 页对齐**；所有 JNI 描述符与 Kotlin `external fun` 一一匹配；`yuv/TransformI420`（本会话早前引入）是范式正确、内存安全的样板。

---

## 一、lib-image（BLOCK）

### CRITICAL

- **L-C1 `CropBitmap` 零输入校验 → 无符号下溢 → 堆溢出**（`BitmapRotateNative.cpp:19-43`）。`left/top/right/bottom` 为 `uint32_t`，Kotlin 侧 `Int` 按位透传；`newWidth = right-left` 在 `right<left` 或负值时下溢为巨大值，`new uint32_t[newWidth*newHeight]` 又可能乘法回绕成小分配，随后 `memcpy` 越界读写。复现：100×100 位图上 `cropBitmap(0,0,999999,999999)`。修：校验 `right>=left && bottom>=top && right<=oldWidth && bottom<=oldHeight` 并用 `size_t` 运算。
- **L-C2 `ScaleNNBitmap`/`ScaleBIBitmap` 接受未校验的负 `newWidth/newHeight` → 整型溢出 → 堆溢出**（`:240-276`、`:282-380`）。`scaleBitmap(-1,-1)` → native 收到 `0xFFFFFFFF`，`newWidth*newHeight` 回绕为 1，`new uint32_t[1]`，但循环按 `0xFFFFFFFF` 次写入 → 海量堆溢出，**纯 Kotlin API + 普通负值 bug 即可触发**。修：校验 `>0` 且设上限，`size_t` 运算。
- **L-C3 `ScaleBIBitmap` 源宽或高为 1 时负索引**（`:308-311,321-324,344-346`）。`oldWidth==1` 时 `xTopLeft` 被减到 `-1`，`previousData[y*oldWidth+xTopLeft]` 负偏移越界读。复现：任意 1×N / N×1 位图走双线性缩放。修：要求 `oldWidth>=2 && oldHeight>=2` 或钳制 `>=0`。
- **L-C4 原生指针句柄暴露为 `public` 可变字段**（`BitmapProcessor.kt:33` `var bitmapByteBuffer: ByteBuffer? = null`）。无可见性修饰符即 public；外部可将其替换为任意 `ByteBuffer`，下一次 native 调用把任意内存当 `JniBitmap*` 解释 → 类型混淆/内存破坏，并可读出原生堆地址。修：改 `private`/`internal`。

### HIGH

- **L-H5 未捕获的 `std::bad_alloc` 穿越 JNI 边界**（所有 `new uint32_t[...]`：`:29,56,85,229,250,292`）。无 `nothrow`/`try-catch`，分配失败抛 C++ 异常穿出 `extern "C"` → `std::terminate()` 硬崩溃。修：`nothrow` + 校验 + `ThrowNew` 上报。
- **L-H6 `JniBitmap`/像素缓冲缺 RAII**（`.h:61-71` 及各函数手动 `delete[]`）。当前靠每个函数小心配对，新增早退/异常路径即泄漏/双管理。修：`std::unique_ptr<uint32_t[]>`。
- **L-H7 `GetBitmapFromSavedBitmapData` 缺 `FindClass`/`Call*Method` 的 null/异常检查**（`:169-190`）。`Bitmap.createBitmap` 抛异常时返回 NULL 且异常挂起，却继续 `AndroidBitmap_lockPixels(NULL)` → 挂起异常下调 JNI 属 UB。修：逐一 null 检查 + `ExceptionCheck()`。
- **L-H8 Kotlin 非空返回与 native `NULL` 不一致 → 静默失败**（`BitmapProcessor.kt:40-41`；native `SetBitmapData` `:210,216,226`、`GetBitmap...` `:164,190` 返回 NULL）。构造非 RGBA_8888 位图 → `bitmapByteBuffer` 静默为 null，后续操作全 no-op，无异常无日志，违反"绝不静默吞错"。修：声明返回 `?` 并抛错/日志。
- **L-H9 `internal fun finalize()` 永不被 JVM 调用**（`BitmapProcessor.kt:132-140`）。Kotlin 对 `internal` 成员做名称改写，签名不匹配 `Object.finalize()`，兜底释放形同虚设 → 忘记 `free()` 即整生命周期泄漏。修：改 `protected fun finalize()`，更佳用 `Cleaner`/显式生命周期。

### MEDIUM

- **L-M10** 忽略 `AndroidBitmapInfo.stride`，假设 `stride==width*4`（`:231,194`），有行填充时画面错位。
- **L-M11** `pixelsCount` 由无符号乘积窄化为 `int`（`:193,230`），配合 L-C1/C2 corrupted dims 可为负 → `memcpy` 巨长。
- **L-M12** 零维度边界未处理（`:257-267`）。
- **L-M13** 无线程安全约定；`FreeBitmapData` 不失效 `ByteBuffer` 内地址，并发 `free` 与 `rotate` 竞态 → use-after-free。修：文档化"非线程安全"或加锁。

### LOW

- **L-L14** `ARGB` 结构字段名与 RGBA_8888 实际位序不符，但 `convertIntToArgb`/`convertArgbToInt` 用同一套错位、缩放为逐通道对称加权 → **经逐位核验非颜色 bug**，仅命名误导。
- **L-L15** 日志格式串当前均为字面量，安全；仅提示后续保持。

### 已核验正确

16KB 页对齐（`CMakeLists.txt:22-24`）；11 个 JNI 描述符全匹配；`lockPixels/unlockPixels` 各路径配对；`SetBitmapData` 入口校验 RGBA_8888；空句柄 guard 全函数一致；`RotateBitmapCw90`/`Rotate180`（含奇数高）旋转数学逐格核验正确；`JNI_OnLoad` 检查完整。

---

## 二、yuv（BLOCK）

### CRITICAL

- **Y-C1 所有 old-style 入口缺"源数组长度 vs width*height"校验 → 堆越界读**（`YuvUtilNative.cpp` 除 `TransformI420` 外全部入口；`YuvConvert.cpp` 按 `width*height` 算平面偏移）。复现：`mirrorI420(10字节, 1920, 1080)` → 按 ~3.1MB 读 10 字节缓冲。修：每入口校验 `GetArrayLength(src) >= (int64_t)width*height*3/2`（按格式）且 `width,height>0`，对齐 `TransformI420`。
- **Y-C2 `i420ToRgb24`/`I420ToRGB24` 把目标总长当作 `dst_stride_rgb24`**（`YuvConvert.cpp:341-345`、`YuvUtilNative.cpp:422-438`）。传 `width*height*3` 而非 `width*3` 作行 stride → 第 1 行后每行越界写。缓解：该 API 在 `YuvUtil.kt:203-207` 标注 "NOT work now"，但源码 bug 属实，重启用前必修为 `width*3`。
- **Y-C5 `jint` 维度未校验正性/溢出**（除 `TransformI420` 外所有入口的 `width*height` 运算）。负维度经隐式 `size_t` 转换成巨值；`50000×50000` 在 `YuvConvert.cpp` 的纯 `jint` 运算里溢出回绕 → 指针偏移 OOB。修：各入口校验 `>0` 并全程 `int64_t`/`size_t` + 上限，如 `TransformI420`。

### HIGH

- **Y-H3 `ScaleNV12` `ThrowNew` 后无 `return`**（`YuvUtilNative.cpp:381-384`）。异常挂起后继续 `GetArrayLength`/分配/`scaleNV12`/`NewByteArray`/`SetByteArrayRegion`，CheckJNI 下进程 abort；且 `FindClass` 未 null 检查。修：改用 `ThrowIllegalArgumentException(env,...); return nullptr;`。
- **Y-H4 `NewByteArray` 返回值未 null 检查即 `SetByteArrayRegion`**（所有 old-style 入口）。OOM 时返回 NULL 且异常挂起，`SetByteArrayRegion(nullptr,...)` 非法。修：`if(dst==nullptr) return nullptr;`，对齐 `TransformI420:160`。
- **Y-H6 `transformI420` 可 `return nullptr` 但 Kotlin 声明非空 `ByteArray`**（`YuvUtil.kt:103-110` vs `YuvUtilNative.cpp:183,187`）。`GetPrimitiveArrayCritical` 失败返回 NULL 且无保证异常 → 违反 Kotlin 空安全。修：失败前 `ThrowOutOfMemoryError` 或声明 `ByteArray?`。

### MEDIUM / LOW

- **Y-M7** old-style 全用裸 `new[]/delete[]`（应 `unique_ptr`，如 `TransformI420`）；当前直线控制流无实际泄漏但脆弱。
- **Y-M8** old-style 入口 `GetArrayLength` 前未 null 检查输入 `jbyteArray`。
- **Y-L9** `CropI420` 校验裁剪边界但未校验维度正性/源长度（同 Y-C1 根因）。
- **Y-L10** `nv12ToI420` 参数误命名 `nv21ByteArray`（仅命名）。
- **Y-L11** `YuvConvert.h` 用 `extern "C"` 包裹带 `namespace` 的 `libyuv.h`，无实际效果，易误导。

### 已核验正确

`TransformI420`：非空/正偶维度/degree/format 全校验，`int64_t`+`INT32_MAX` 上限并对齐源长度，critical section 顺序正确（输出 `NewByteArray` 在进入 critical 前、source-then-output 获取、output-then-source 释放、source 用 `JNI_ABORT`/output 用 0、期间无 JNI 调用），`unique_ptr`+`nothrow`，rotate 后正确重算尺寸再 mirror/NV12。`rotateI420` 90/270 正确交换 stride；16 个 JNI 描述符全匹配；NV21/NV12/I420 色度平面顺序全程一致无混淆；`JNI_OnLoad` 检查完整；16KB 对齐（`CMakeLists.txt:12`）。

---

## 三、jpeg（BLOCK）

### CRITICAL

- **J-C1 未校验位图格式即当作 4 字节 RGBA 处理**（`JPEGNative.cpp:143-178`）。`android_bitmap_info.format` 从不检查，像素循环按 `w*h*4` 走。传 `RGB_565`(2B)/`ALPHA_8`(1B) → 越界读相邻堆（信息泄漏）或崩溃。修：校验 `==ANDROID_BITMAP_FORMAT_RGBA_8888`。
- **J-C2 `AndroidBitmap_getInfo`/`lockPixels` 返回码被忽略，`AndroidBitmapInfo` 未初始化**（`:140-141,150`）。null/recycled/HARDWARE 位图时 `w/h` 为栈垃圾、`pixelsColor` 为未初始化指针 → `malloc(巨值)` 或解引用任意指针。修：检查返回值 `==SUCCESS` 再用，失败先 unlock 再返回错误码。
- **J-C3 缓冲尺寸 32 位整型溢出**（`:112,155` `w*h*3`、`image_width*3`）。约 38000² 时 `w*h*3` 回绕成小值，`malloc` 过小，后续像素循环 + `jpeg_write_scanlines` 越界写 → 堆溢出。修：`static_cast<size_t>(w)*h*3` 并拒绝溢出维度。
- **J-C4 `malloc` 返回值未检查**（`:155,172-175`）。大图/低内存返回 nullptr，`*data=r` 解引用 null 崩溃。修：null 检查 + unlock + 返回错误码。

### HIGH

- **J-H5 `write_JPEG_file` 失败路径泄漏**（`:186-188`）。`resultCode==-1` 时在 `ReleaseStringUTFChars` 与 `free(tempData)` 之前 return → 每次失败（不可写路径等）泄漏 JNI 字符串缓冲 + `w*h*3` 像素缓冲，批量调用无界泄漏 → native OOM。修：单一 cleanup 路径。
- **J-H6 `write_JPEG_file` 在 setjmp 错误路径泄漏 compress 对象/文件句柄**（`:52-57,58,62-66,120-128`）。`jpeg_start_compress`/`write_scanlines` 内部 fatal → longjmp 回 `:52` 直接 `return -1`，`jpeg_destroy_compress`/`fclose` 不可达；`fopen` 失败也漏 `jpeg_destroy_compress`。修：统一 `goto cleanup`/RAII 始终销毁 + 关文件。
- **J-H7** 裸 `malloc/free` 无 RAII（是 H5 泄漏的结构根因），应 `std::vector<BYTE>`/`unique_ptr`。

### MEDIUM / LOW

- **J-M8** 假设行 stride==`width*4`，忽略 `android_bitmap_info.stride`，非 2 幂宽度设备画面错位（`:159-178`）。
- **J-M9** `outFilPath` 直接 `fopen` 无校验（路径穿越；取决于上游调用点是否来自不可信输入，本文件内未见不可信调用点）。
- **J-M10** `GetStringUTFChars` 返回值未 null/异常检查（`:181`）。
- **J-L11-16** `AndroidBitmapInfo` 未值初始化（与同文件 `cinfo{}`/`jem{}` 不一致，是 J-C2 诱因）；`quality` 未在边界校验（libjpeg 内部会 clamp，非确认 bug）；`(char*)` 弃 const；`write_JPEG_file` 应 `static`；`JNI_OnLoad` 的 `clz` 未 `DeleteLocalRef`；`#include <string>` 未用。

### 已核验正确

compress 路径**已正确安装** libjpeg 自定义 `error_exit` + `setjmp/longjmp`（不会 `exit()` 进程，fatal 时返回 -1，受 J-H6 清理缺口影响）；`cinfo{}`/`jem{}` 已值初始化；成功路径 `jpeg_destroy_compress`+`fclose` 均可达；JNI 描述符 `"(Landroid/graphics/Bitmap;ILjava/lang/String;Z)I"` 与 `compressBitmap` 完全匹配；本模块**无** `GetByteArrayElements`/`GetPrimitiveArrayCritical`，无 pin/release 风险；`turbojpeg.h` 已包含但**全文件未调用任何 `tj*`**——本模块仅做压缩（Bitmap→file），无解码路径，故解码相关风险不适用；16KB 对齐（`CMakeLists.txt:12`）；无全局可变状态，多线程各自栈缓冲安全；正确链接 `jnigraphics`/`log`。

---

## 汇总（按严重度）

| 模块 | CRITICAL | HIGH | MEDIUM | LOW | 结论 |
|------|:---:|:---:|:---:|:---:|:---:|
| lib-image | 4 | 5 | 4 | 2 | BLOCK |
| yuv | 3 | 3 | 2 | 3 | BLOCK |
| jpeg | 4 | 3 | 3 | 6 | BLOCK |

## 优先级建议

1. **P0（先修，纯 native 内存安全）**：各模块 CRITICAL —— 所有 JNI 入口补维度/长度/格式校验 + `size_t`/`int64_t` 尺寸运算 + `malloc/new` 失败处理。yuv 的 `i420ToRgb24` stride 必修。
2. **P1**：HIGH —— RAII 化（`unique_ptr`/`vector`）、`ThrowNew` 后 `return`、`NewByteArray`/`Call*Method` null/异常检查、jpeg 错误路径统一 cleanup、Kotlin 返回类型改 `?` 或 native 抛异常、lib-image `finalize()` 修为 `protected`/`Cleaner`。
3. **P2/P3**：stride 行拷贝、命名/一致性、`static` 链接、未用 include 等。

> 注：`yuv/TransformI420` 已是范式正确实现，建议以它为模板改造其余 old-style 入口。真机验证仍是 native 图像/YUV 改动的必需环节。
