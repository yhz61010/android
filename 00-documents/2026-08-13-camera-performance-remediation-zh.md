# camera2live / camerax 性能整改 —— 决策与进度记录（2026-08-13）

> 本文记录对 `camera2live` 与 `camerax` 两个模块（并延伸到共享的
> `androidbase/.../media/YuvUtil.kt`）的运行时性能审查、整改方案、**每项选择的理由**与落地进度。
> 关联分支：`fix/eight-module-remediation`。
> 关联文档：`2026-08-11-remediation-progress-and-review-zh.md`（八模块整改）、
> `superpowers/specs/`（CX-5 / LB-3 设计）。

---

## 1. 审查方法

两个模块各派一个只读审查代理并行审查（`ecc:kotlin-reviewer`），只找**运行时性能问题**
（每帧分配、主线程阻塞、重复计算、无界缓冲、缺复用等），产出带 file:line 与严重度的清单。
随后对 4 条 HIGH 逐行人工复核确认。共 **11 项：4 HIGH + 7 MEDIUM**。

> 说明：最热的两条（H4、M5）落在共享工具 `androidbase/.../media/YuvUtil.kt`，严格说不在两个相机模块
> 目录内，但被其每帧调用。经确认，**修复范围延伸到该共享 `YuvUtil`**（维护者决定）。

---

## 2. 全部发现（按严重度）

| 编号 | 位置 | 问题 | 频率 |
|------|------|------|------|
| H1 | `camerax/utils/CodecExt.kt:35-53` + `BaseCameraXFragment.outputCameraParameters` | `createEncoderByType()` 只读能力、从不 `release()`（泄漏原生编码器）；每次 bind 在主线程建 4 个 MediaCodec 仅为拼日志 | 每次 bind |
| H2 | `camerax/analyzer/LuminosityAnalyzer.kt:72` | 每帧 `planes[0].buffer.toByteArray()` 整帧 Y 平面新分配（≈9MB/s） | 每帧 |
| H3 | `camera2live/.../EncoderStrategyYuv420Sp.kt:48-57` | 前置摄像头走纯 Kotlin 逐像素镜像+旋转，后置走 native libyuv；前置每帧多花数倍 CPU | 每帧（前置） |
| H4 | `androidbase/.../media/YuvUtil.kt:155-172` | 色度平面 strided 时逐字节 Kotlin 循环提取 | 每帧 |
| M1 | `LuminosityAnalyzer.kt:22,56` | `ArrayDeque<Long>` 每帧 `push` 装箱 Long | 每帧 |
| M2 | `LuminosityAnalyzer.kt:80-85` | 每帧全分辨率逐字节求和 luma，无降采样 | 每帧 |
| M3 | `CameraFragment.kt:384` | 每帧 `log.v("Average luminosity: $luma")` 无条件拼串 | 每帧 |
| M4 | `BaseCameraXFragment.kt:806-843` / `CameraFragment.kt:280-303` | 同一次 bind 内重复取 `CameraCharacteristics` + 两次重算 supported-size | 每次 bind |
| M5 | `androidbase/.../media/YuvUtil.kt:118` | 每帧新分配 `rowData` 刮擦缓冲（rowStride 会话内不变） | 每帧 |
| M6 | `camera2live/codec/CameraAvcEncoder.kt:157-158` | 每编码帧 `ByteArray(info.size)` 新分配 | 每编码帧 |
| M7 | `camera2live/Camera2ComponentHelper.kt:926-927` | 每帧重查 `SENSOR_ORIENTATION`（相机静态） | 每帧 |

---

## 3. 拆分依据

按**位置 + 风险**拆成 3 个子项目，一个个走 brainstorm → 实现，风险从低到高：

1. **子项目 1（camerax 基础设施）** = H1、M3、M4 —— 后 M3 移交子项目 2（见下）。行为保持、无设备依赖。
2. **子项目 2（LuminosityAnalyzer）** = H2、M1、M2、M3。含"分析器去留"决策。
3. **子项目 3（camera2live + 共享 YuvUtil）** = H3、H4、M5、M6、M7。最高风险，含每帧 YUV 正确性。
   进一步再拆：
   - **3a（低风险机械）** = M7。
   - **3b（YUV 正确性，必须真机）** = 探查后收敛为**仅 H3**（H4、M5 决定不做，见 §4 / §6）。
   - **M6** 单独决策 → 跳过（见下）。

---

## 4. 每项决策与理由

### 子项目 1 —— 已完成（commit `78e4553a6`，ktlint 收尾 `0deaafbf0`）

- **H1a（CodecExt release）**：4 个能力查询函数创建 codec 后 `try/finally release()`。能力数组是 Java 侧
  拷贝，release 后仍有效，故安全。
- **H1b（选择“只缓存编解码能力”而非“缓存整段诊断串”）**：
  - 备选 (a) 按 cameraId 缓存整段诊断串——最省，但该串含**动态字段** `deviceRotation`，整段缓存会让日志里
    该字段停在首次 bind 的旧值。
  - **采纳 (b)**：编码器能力是**设备全局、运行时不变**，按 mime 全局缓存；诊断串每次 bind 重建，动态字段
    保持实时。**理由**：正确性优先，且直接消除真正的开销（4 次 codec 创建），字符串重建成本很低。
- **M4（按 cameraId 记忆化 characteristics + supported-size）**：二者对给定相机不可变，切镜头用不同 key。
  `showAvailableRatio`/`getMaxPreviewSize`/`outputCameraParameters`/`CameraFragment.bindCameraUseCases`
  共用，消除同一次 bind 内的重复 binder 查询与尺寸重算。

### 子项目 2 —— 已完成（初版 commit `ab4d0cdbc`）

- **方向决策：采纳 C（默认停用 + 内部优化）**。
  - 关键事实：`LuminosityAnalyzer` 为 `internal`、全仓唯一使用点是 `CameraFragment:380`，其每帧算出的 luma
    **只喂一条 verbose 日志**，无 UI / 无逻辑消费。
  - 备选 A（就地优化、继续每帧跑）治标不治本；B（默认停用）拿到主要收益但 H2/M1/M2 变"没人跑的代码"。
  - **C = A+B**：默认不创建或绑定 `ImageAnalysis`，因此没有分析流的每帧开销；opt-in 时也高效，且把
    H2/M1/M2 一并了结。
- **M3（每帧日志）**：原计划在子项目 1 处理，后**移交子项目 2**。**理由**：camerax 未启用 BuildConfig、log 无
  延迟消息重载，单独守卫要 reach 进 log 内部；且它与分析器强耦合，应随分析器去留一起决定（C 下自然消失）。
- **停用方式**：`CameraFragment` 的 `ENABLE_LUMINOSITY_ANALYSIS` 默认是 `false`；关闭时 `imageAnalyzer`
  为 `null`，绑定列表不包含 `ImageAnalysis`。维护者把开关设为 `true` 后才创建、配置并绑定分析器。
- **H2+M2 合并**：`averageLuma` 改为**直接遍历 `ByteBuffer`**（不再每帧 `toByteArray()`）并按
  `LUMA_SAMPLE_STRIDE` 采样。签名默认 `stride=1` 保持精确，并拒绝非正 stride（单测覆盖正常采样、零值和
  负值）。
- **M1**：FPS 用原始 `LongArray` 环形缓冲替代 `ArrayDeque<Long>`，消除装箱。

### 子项目 3a —— 已完成（commit `b1ff02064`）

- **M7（缓存 sensor orientation）**：`Camera2ComponentHelper` 加字段，在 `initializeParameters()` 里
  `characteristics` 赋值处同步缓存一次；每帧 image-available 回调改用字段。`SENSOR_ORIENTATION` 相机静态、
  仅切镜头随 `characteristics` 重赋值变化 → 缓存点与 characteristics 绑定，始终一致；沿用 `?: -1` 默认值。
  行为不变，非每帧的其它三处读取（:316/:997/:1124）不动。

### 单独决策

- **M6（编码帧 `ByteArray(info.size)` 池化）—— 决定跳过**。**理由**：`encodedBytes` 交给对外接口
  `dataUpdateCallback.onCallback(...)`，池化复用只有消费方**同步读取/拷贝**才安全；消费方若异步保留数组会
  被后续帧覆写损坏。这是改变对外**字节数组所有权契约**的破坏性变更，且编码帧仅几 KB、收益有限，
  **不值得**。保持每帧分配。
- **M5（`rowData` 复用）—— 探查后决定不做**。原计划并入 3b；探查确认 `androidbase/YuvUtil` 是**无状态
  `object`**、线程安全正来自"每帧新分配局部 `rowData`"，复用须 `ThreadLocal`/传入 scratch。而 `rowData`
  （~1–2KB）相对不可避免的 `data`（≈460KB/帧）是小头 → 不值得为共享 util 引入可变状态。详见 3b spec §2。
- **H4（色度 strided 提取）—— 探查后决定不做**。native 侧无吃 `Image`/planes 的入口，从 Image 提取平面仍须
  Kotlin；真 native 化要新增 JNI 入口（大改，另立项），纯 Kotlin 微优化收益有限且仍改字节。详见 3b spec §2。

---

## 5. 进度与提交

| 子项目 | 内容 | 状态 | 提交 |
|--------|------|------|------|
| 1 | camerax H1 / M4 | ✅ 已推送 | `78e4553a6`、`0deaafbf0`(ktlint) |
| 2 | LuminosityAnalyzer H2/M1/M2/M3（方向 C） | ✅ 已推送 | `ab4d0cdbc`（初版）、`5a23d6c93`（定稿：开关停用、不绑定分析流） |
| 3a | camera2live M7 | ✅ 已推送 | `b1ff02064`、`5a23d6c93`(注释/格式) |
| 3b | **仅 H3**（前置 YUV native 化；H4/M5 不做） | 📄 spec 已定，代码待真机 | `superpowers/specs/2026-08-13-cam2-front-yuv-native-design.md` |
| M6 | 编码帧池化 | ⛔ 跳过 | — |

---

## 6. 子项目 3b —— 已出 spec，代码待真机

经 Explore 深度探查（native `com.leovp.yuv.YuvUtil` API、前置 native 范式、纯 Kotlin 函数方向语义、
`getYuvDataFromImage` 与线程模型）后，3b **收敛为只做 H3**；H4、M5 探查后决定不做。设计详见
`superpowers/specs/2026-08-13-cam2-front-yuv-native-design.md`。

- **H3（做，device-gated）**：`EncoderStrategyYuv420Sp` 前置分支从 `androidbase` 纯 Kotlin 旋转/镜像改走
  native `com.leovp.yuv.YuvUtil`，显式输出 NV12；旧实现由 Git 历史保留，不复制为失效注释。
  - **头号风险**：90° 与 270° 旧分支的镜像轴和处理顺序不同，而且旧代码用 NV21 函数处理 I420 色度。
    spec 因此保留 `cameraSensorOrientation` 分支：90° 候选使用 `mirrorI420 + rotateI420(270)`，270° 候选使用
    `convertToI420(verticallyFlip=true, 270)`。两个方向必须分别真机验证，不能由其中一个方向的结果决定全局实现。
- **H4（不做）**：色度 strided 提取无干净 native 路径（native `android420ToI420` 只吃 `ByteArray`、不吃
  `Image`/planes；从 Image 提取平面仍须 Kotlin）。真 native 化要新增吃 planes 的 JNI 入口（大改，另立项）；
  纯 Kotlin 微优化收益有限且仍改字节，风险/收益比差。
- **M5（不做）**：`androidbase/YuvUtil` 是**无状态 `object`**，线程安全正来自"每帧新分配局部 `rowData`"。
  复用须 `ThreadLocal`/传入 scratch；而 `rowData`（~1–2KB）相对 `data`（≈460KB/帧）是小头，不值得为共享
  util 引入可变状态。

**验证（合并前置条件）**：必须真机回归 —— 前置方向/镜像/颜色与改前一致、后置不受影响、前后置连续快速切换无
花屏/`ERROR_CAMERA_IN_USE`、覆盖 sensorOrientation 90 与 270 机型；像 R-5/CX-5 一样**代码可先备好但 gated，
真机通过后才合并**。

### ⚠️ 未验证事项

- 子项目 1/3a 是**行为保持型**改动。子项目 2 则是已确认的默认行为调整：停用亮度分析日志，并从默认绑定组合中
  移除 `ImageAnalysis`；这可能改变 CameraX 的 use-case 组合与分辨率协商。因此仍建议随 3b 的真机回归
  **顺带冒烟**（相机预览/拍照/录制/切镜头/切比例正常）。
- 本仓库环境不做本地编译；detekt/ktlint/单测由维护者本地执行。

---

## 7. 审查记录

### `5a23d6c93` —— Codex 复审整改（已复核通过）

对 SP2/3a 及 3b spec 的复审提交，逐文件人工复核后**通过**，其中两处为实质性纠正：

- **SP2 停用方式（代码更优）**：原实现里 `imageAnalyzer` 仍被**创建并绑定**，仅注释掉 `setAnalyzer`。
  CameraX 1.4.1 只有在设置 analyzer 后才激活数据发送，因此不能断言该旧实现仍在逐帧取帧；但绑定
  `ImageAnalysis` 仍会创建和协商分析管线及输出 surface，并参与 use-case 组合与分辨率选择。改为编译期开关
  `ENABLE_LUMINOSITY_ANALYSIS = false`：关闭时 `imageAnalyzer = null`，`bindToLifecycle` 动态构建 use-case
  列表（`preview + imageCapture`，`imageAnalyzer?.let { add(it) }`），默认不再绑定分析 use case，从而完整移除
  分析路径的资源占用；开关启用时仍保留优化后的亮度分析能力。
- **`averageLuma` 加 `require(stride > 0)`** + 单测（覆盖 0/-1）：防止非正步长导致的循环异常。
- **3b spec 纠正两个设计缺陷**：
  1. 原方案拟**合并 90°/270° 分支**为单一"镜像+旋转270"——有风险：两分支镜像轴/顺序本不同
     （90°=`mirrorNv21`+`rotateYUV420Degree270`；270°=`rotateYUVDegree270AndMirror` 一步），合并可能翻转
     某类传感器方向。改为**保留 `cameraSensorOrientation` 分支、两方向各自真机验证**。
  2. 原文声称"逐字节等价"——旧函数是 **NV21 色度函数处理 I420 数据**，不能宣称字节级等价，只能以真机
     画面为基线。
- **3a/M7、CHANGELOG、进度表**：注释重措辞、ktlint 换行、过期信息订正（3a→`b1ff02064`），无行为变化。

### 3b 旧实现保留策略（已明确）

SP2 的亮度分析代码在开关启用时仍是有效的 opt-in 路径，不属于失效代码；3b 完成 native 替换后，旧前置
Kotlin 分支则不再参与任何执行路径，两者性质不同。3b 按 spec 现有方案处理：旧实现通过 Git 历史保留，不在
源码中留下整块失效注释，并同步清理不再使用的 import，以满足 detekt 零容忍要求。
