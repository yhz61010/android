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

### 子项目 2 —— 已完成（commit `ab4d0cdbc`）

- **方向决策：采纳 C（默认停用 + 内部优化）**。
  - 关键事实：`LuminosityAnalyzer` 为 `internal`、全仓唯一使用点是 `CameraFragment:380`，其每帧算出的 luma
    **只喂一条 verbose 日志**，无 UI / 无逻辑消费。
  - 备选 A（就地优化、继续每帧跑）治标不治本；B（默认停用）拿到主要收益但 H2/M1/M2 变"没人跑的代码"。
  - **C = A+B**：默认零每帧开销，opt-in 时也高效，且把 H2/M1/M2 一并了结。
- **M3（每帧日志）**：原计划在子项目 1 处理，后**移交子项目 2**。**理由**：camerax 未启用 BuildConfig、log 无
  延迟消息重载，单独守卫要 reach 进 log 内部；且它与分析器强耦合，应随分析器去留一起决定（C 下自然消失）。
- **停用方式**：按维护者要求，`CameraFragment` 里 `setAnalyzer(LuminosityAnalyzer{…})` 块与其 import
  **注释保留、不删除**，消费方取消注释即可启用。
- **H2+M2 合并**：`averageLuma` 改为**直接遍历 `ByteBuffer`**（不再每帧 `toByteArray()`）并按
  `LUMA_SAMPLE_STRIDE` 采样。签名默认 `stride=1` 保持精确（单测覆盖）。
- **M1**：FPS 用原始 `LongArray` 环形缓冲替代 `ArrayDeque<Long>`，消除装箱。

### 子项目 3a —— 已完成（待提交）

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
| 2 | LuminosityAnalyzer H2/M1/M2/M3（方向 C） | ✅ 已推送 | `ab4d0cdbc` |
| 3a | camera2live M7 | ✅ 已实现，待提交 | — |
| 3b | **仅 H3**（前置 YUV native 化；H4/M5 不做） | 📄 spec 已定，代码待真机 | `superpowers/specs/2026-08-13-cam2-front-yuv-native-design.md` |
| M6 | 编码帧池化 | ⛔ 跳过 | — |

---

## 6. 子项目 3b —— 已出 spec，代码待真机

经 Explore 深度探查（native `com.leovp.yuv.YuvUtil` API、前置 native 范式、纯 Kotlin 函数方向语义、
`getYuvDataFromImage` 与线程模型）后，3b **收敛为只做 H3**；H4、M5 探查后决定不做。设计详见
`superpowers/specs/2026-08-13-cam2-front-yuv-native-design.md`。

- **H3（做，device-gated）**：`EncoderStrategyYuv420Sp` 前置分支从 `androidbase` 纯 Kotlin 旋转/镜像改走
  native `com.leovp.yuv.YuvUtil`（对齐后置与 `EncoderStrategyYuv420P` 前置范式），显式输出 NV12，旧 Kotlin
  分支注释保留以便回滚。
  - **头号风险**：镜像轴不一致 —— Sp 现用**水平镜像**（`mirrorNv21`），而 P 前置 native 用 `verticallyFlip`
    （**垂直**翻转）。spec 给出候选 B（`mirrorI420 + rotateI420(270)`，忠实移植水平镜像，主选）与候选 A
    （`convertToI420(verticallyFlip=true, 270)`，回退），**由真机方向核对择一**。
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

- 子项目 1/2/3a 均**行为保持型**改动，理论不需真机；但建议随 3b 的真机回归**顺带冒烟**
  （相机预览/录制/切镜头/切比例正常）。
- 本仓库环境不做本地编译；detekt/ktlint/单测由维护者本地执行。
