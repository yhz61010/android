# 对 Codex Native 整改方案的复核（2026-08-27，_cc）

> 本文复核 Codex 的 `2026-08-27-native-modules-remediation-plan-zh.md`，逐条对照真实代码与我的独立盲审
> （`2026-08-27-native-modules-review_cc.md`）。仅记录**发现的问题与差异**，并给出整体判断。

## 1. 复核方法与范围

- 我的独立审查范围：`lib-image`、`yuv`、`jpeg`（三个 CMake 直接构建的 native 模块）。
- Codex 方案范围**更广**：除上述三个外，还覆盖 `ffmpeg-javacpp`、`ffmpeg-sdk`（h264/hevc、adpcm native 源码）、三个 FFmpeg AAR 打包互斥、JavaCPP ABI。
- 重叠模块（lib-image/yuv/jpeg）：直接对照我的独立发现。
- 非重叠模块（ffmpeg-javacpp/ffmpeg-sdk/打包）：不在我独立盲审内，仅对最具体的断言做代码 spot-check，不做完整独立复审。

## 2. 总体判断

**Codex 方案技术上正确，且覆盖面比我的独立审查更广。** 我能逐行验证的 Codex 断言**全部属实**，未发现虚报（false positive）。其中两处还是**我独立审查漏掉**的真 bug（见 §3）。

但存在一个**主要问题**和若干次要问题，集中在**严重度分级与优先级**，而非技术事实：

- **主要问题**：方案全表**最高只标到 HIGH，无一条 CRITICAL**，但实际存在多处**确认的 CRITICAL 级堆溢出 / 越界**，且可从公开 Kotlin API 用普通参数平凡触发。
- **次要问题**：`lib-image` 内存安全被归入 MEDIUM（NAT-BMP-01）且排在**批次 D**，优先级低于 yuv/jpeg，但其 crop/scale 堆溢出与 yuv/jpeg 同为 CRITICAL 且同样易触发。

## 3. 正确性核实（Codex 断言属实，含我漏掉的两处）

| Codex ID | 我方对应 | 代码核实 | 结论 |
|---|---|---|---|
| NAT-YUV-01（i420ToRgb24 总长当行 stride）| Y-C2 | `YuvConvert.cpp` 传 `dst_len` 作 `dst_stride_rgb24` | ✅ 属实 |
| **NAT-YUV-02**（nv12ToI420 旋转后仍用原宽度作 dst stride）| **（我漏）** | `YuvConvert.cpp:244` `NV12ToI420Rotate` 带 `degree`，但 `dst_y_stride=width`；90/270 输出宽应为 `height`，portrait 帧越界写 | ✅ 属实，**我独立审查漏报** |
| NAT-YUV-03（多数 JNI API 缺尺寸/长度/枚举/溢出检查）| Y-C1+Y-C5+Y-H3+Y-H4 | 逐入口确认 | ✅ 属实 |
| NAT-JPEG-01（libjpeg 错误路径泄漏 compressor/文件/字符串/RGB 缓冲）| J-H5+J-H6 | `JPEGNative.cpp:52/128/186-188` | ✅ 属实 |
| **NAT-JPEG-02（optimize 被 defaults 覆盖）**| **（我漏）** | `JPEGNative.cpp:89-95` 设 `optimize_coding`/`arith_code`，`:97` `jpeg_set_defaults` 覆盖回默认 → `optimize` 参数**完全失效** | ✅ 属实，**我独立审查漏报** |
| NAT-BMP-01（无效 finalize、公开句柄、stride、参数校验）| L-C1~C4+L-H7+L-H9+L-M10 | `BitmapProcessor.kt` / `BitmapRotateNative.cpp` | ✅ 属实 |
| NAT-JCPP-01（成功解码泄漏 AVFrame、mono 访问右声道）| （spot-check）| `AdpcmImaQTDecoder.kt:80` return 前不 `av_frame_free`，`finally` 只放 pkt；`:75` mono 无条件 `extended_data(1)`；错误路径 pkt 多次 free | ✅ 属实 |

> 说明：NAT-YUV-02 与 NAT-JPEG-02 是 Codex 独立发现、而我的三代理盲审未覆盖的真 bug，应计入 Codex 方案的**正向价值**，同时说明我的独立审查在这两点上有盲区（我的代理核了 `rotateI420`/`TransformI420` 与 jpeg 内存安全，但未细查 `nv12ToI420` 旋转路径与压缩参数顺序）。

## 4. 发现的问题

### 问题 1（主要）— 严重度低估：确认的 CRITICAL 未标 CRITICAL

Codex 全表最高 HIGH。但下列问题均为**确认的 CRITICAL 内存安全 bug**，可从公开 API 用普通参数触发，应升级：

- **yuv 缺"源数组长度 vs width×height"校验**（NAT-YUV-03 内含）→ `mirrorI420(10字节,1920,1080)` 按 ~3.1MB 读 10 字节缓冲，**堆越界读**。→ 应 CRITICAL。
- **yuv `i420ToRgb24` stride 越界写**（NAT-YUV-01）→ portrait/常规尺寸每行越界写。→ 实为 OOB **写**，建议 CRITICAL。
- **lib-image `CropBitmap` / `ScaleNN/BI` 未校验负/超大维度**（NAT-BMP-01 内含）→ `scaleBitmap(-1,-1)` 触发海量堆溢出写，**纯 Kotlin API + 普通负值即可触发**。→ 应 CRITICAL。
- **jpeg 未校验位图格式 / getInfo 返回码 / w×h×3 溢出 / malloc 失败**（NAT-JPEG-01/02 的 remediation 已覆盖，但严重度偏低）→ RGB565 位图 → 堆越界读；38000² → 32 位溢出堆溢出写。→ 应 CRITICAL。

**影响**：严重度直接决定修复顺序与门禁。把可平凡触发的堆溢出标为 HIGH/MEDIUM，可能让它们在排期上被推后或被"HIGH 批次可延后"的判断放行。**建议**：新增 CRITICAL 档并据实归类；`完成定义`（方案 §13）应显式要求"所有 CRITICAL 内存安全 bug 修复并有 ASan/HWASan 回归"作为发布硬门禁。

> 注：Codex 方案的**修复内容**（§4.2、§5.3、§7.3）实际已覆盖上述各点（补校验、`int64_t` 尺寸、格式检查、alloc 检查）。因此这是**分级/优先级问题，不是修复缺失**。

### 问题 2（次要）— lib-image 内存安全优先级过低

`lib-image` 的 crop/scale 堆溢出被归入 **NAT-BMP-01（MEDIUM）+ 批次 D**，排在 yuv（批次 A）、jpeg（批次 B）之后。但这些 crop/scale 溢出与 yuv/jpeg 的越界**同为 CRITICAL、同样从公开 API 平凡触发**。**建议**：把 lib-image 的纯内存安全项（crop/scale 维度校验、`size_t` 运算、bilinear 1 像素负索引）**从批次 D 提前到与 A/B 同级的 P0**；批次 D 只保留生命周期/句柄/finalize 等真正需要设计的部分。

### 问题 3（次要 / 小 gap）— 两处未在方案中显式点名

- **`transformI420` 的 `GetPrimitiveArrayCritical` 失败返回 `nullptr`，但 Kotlin 声明非空 `ByteArray`**（我方 Y-H6）。方案二次审查清单 Q4 泛泛覆盖"OOM 立即退出"，但未点名此契约违背。建议：失败前显式 `ThrowOutOfMemoryError`，或把 Kotlin 签名改 `ByteArray?`。
- **lib-image 的 `new uint32_t[...]` 未用 `nothrow`，`std::bad_alloc` 可穿越 `extern "C"` 触发 `std::terminate()`**（我方 L-H5）。方案 §7.3.8 "Native 分配结果全部检查"隐含此意，但未显式要求 `new(std::nothrow)` + 校验，且 CMake 已 `-fno-exceptions`（抛异常即 abort）。建议显式写明。

## 5. 范围限制声明

以下 Codex 断言**未经我独立复核**，仅作为方案条目记录，需要单独验证：

- NAT-FFMPEG-01（h264/hevc、adpcm native 输入缺 64 字节 padding）— 未独立复核。
- NAT-LIFE-01（init/decode/release 并发 → 泄漏/UAF/double free）— 未独立复核。
- NAT-PKG-01 / NAT-ABI-01（三个 FFmpeg AAR 同名 `.so` 冲突、JavaCPP ABI 声明与 classifier 不一致）— 未独立复核。

我仅对 `ffmpeg-javacpp` 的 NAT-JCPP-01 做了代码 spot-check 并证实属实，这为 Codex 在 FFmpeg 系模块的判断可信度提供了旁证，但**不等于**已复核上述三项。

## 6. 复核结论

1. **Codex 方案的技术事实与修复方向正确**，未见虚报；覆盖面比我的独立审查更广（多覆盖 ffmpeg-javacpp/ffmpeg-sdk/打包）。
2. **Codex 独立发现了两处我漏掉的真 bug**（NAT-YUV-02、NAT-JPEG-02）。
3. **主要待改进：严重度分级**——多处确认的 CRITICAL 堆溢出/越界被标为 HIGH/MEDIUM，且 `lib-image` 内存安全被排到批次 D，优先级偏低。建议引入 CRITICAL 档、把 lib-image 纯内存安全项提前到 P0，并把"CRITICAL 全修 + ASan/HWASan 回归"写入发布硬门禁。
4. 两处小 gap（transformI420 空返回契约、bad_alloc 穿 JNI）建议在方案中显式点名。
5. FFmpeg padding / 并发 / 打包三项需单独独立复核，本轮未覆盖。

> 交叉参考：我的独立审查结论见 `2026-08-27-native-modules-review_cc.md`；Codex 原方案见 `2026-08-27-native-modules-remediation-plan-zh.md`。
