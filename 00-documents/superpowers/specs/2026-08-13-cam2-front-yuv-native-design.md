# 3b 设计 —— camera2live 前置摄像头 YUV 旋转/镜像 native 化（H3，2026-08-13）

> 性能与正确性整改子项目 3b。真机已确认旧前置路径存在横屏方向和颜色缺陷；代码按本设计整改后已于
> 2026-08-17 通过真机回归。
> 关联:`00-documents/2026-08-13-camera-performance-remediation-zh.md`(总决策记录，含 H3/H4/M5 取舍）。
> 分支:`fix/eight-module-remediation`。

## 1. 背景与问题（H3）

`EncoderStrategyYuv420Sp.doProcess`（`camera2live/.../base/encodestrategies/EncoderStrategyYuv420Sp.kt`）
每帧把相机 `Image` 转成送编码器的 YUV。两条镜头分支处理方式不一致:

- **后置**（`:39-45`）:走 native libyuv —— `com.leovp.yuv.YuvUtil.rotateI420(i420, w, h, ROTATE_90)` 再
  `i420ToNv12(rotated, h, w)`，输出 **NV12**。
- **前置**（`:48-58`）:走 `androidbase` 的**纯 Kotlin** 函数逐像素处理:
  - `cameraSensorOrientation == 90`（Nexus 6/6P）:先调用 `mirrorNv21(i420, w, h)` 原地处理，再调用
    `rotateYUV420Degree270(i420, w, h)`。
  - 其它（270）:调用 `rotateYUVDegree270AndMirror(i420, w, h)` 一步处理。

这里不能把两条分支简单视为同一变换：镜像轴/顺序不同，而且输入实际是 I420，旧函数却按 NV21 交织色度
解释数据。旧路径只能作为真机画面基线，不能作为逐字节等价的格式规范。

前置分支每帧在 CPU 上跑纯 Kotlin 逐像素循环，比后置的 native 路径慢数倍（15–30 fps × 全帧）。

## 2. 决策（已与维护者确认并经真机反馈修订）

- **H3 与同路径正确性修复:做**。前置分支改走 `com.leovp.yuv.YuvUtil` native；同时修复已确认的横屏方向和
  I420/NV21 格式错用导致的偏色，YUV420P/SP 使用同一旋转与最终水平镜像语义。
- **H4:不做**。`getYuvDataFromImage` 的色度 strided 提取无干净 native 路径:native 侧 `android420ToI420`
  只吃 `ByteArray`、不吃 `Image`/planes；从 `Image` 提取平面仍须 Kotlin。真 native 化需新增吃 Image planes
  的 JNI 入口（大改，另立项）。纯 Kotlin 微优化收益有限且仍改字节，风险/收益比差 → 不做。
- **M5:跳过**。`androidbase/YuvUtil` 是**无状态 `object`**，线程安全正来自"每帧新分配局部 `rowData`"。
  复用刮擦缓冲须 `ThreadLocal`/传入 scratch，否则并发帧数据竞争。`rowData`（~rowStride，1–2KB）相对不可避免的
  `data`（≈460KB/帧）是小头 → 不值得为共享 util 引入可变状态。

## 3. 关键风险与最终语义

- **方向来源**：不能继续用固定 270°或只看 `cameraSensorOrientation`。录像开始时的 `relativeOrientation` 已综合
  传感器方向、设备物理方向和镜头符号，是当前码流唯一方向来源。
- **镜像顺序**：先把 I420 旋转为最终观看方向，再在旋转后的宽高上水平镜像。这样四个角度的“最终画面左右
  镜像”语义一致，不需要复制旧实现互相矛盾的水平/垂直翻转分支。
- **格式一致性**：YUV420P 必须输出 I420；YUV420SP 必须显式输出 NV12。任何 I420 数据都不得进入 NV21 专用
  函数。
- **尺寸一致性**：编码器 SPS、旋转后的 I420 和 NV12 转换必须使用同一输出宽高，否则会花屏或颜色错位。

## 4. 变更方案

真机反馈推翻了“只做性能替换且保持旧观感”的前提：旧前置路径本身横屏方向错误且偏色。因此实现扩展到
`Camera2ComponentHelper`、YUV420P/SP 两种策略和共享录像变换，但仍不修改 `IDataProcessStrategy` 公开签名。

### 4.1 录像开始时锁定相机相对方向

`BaseCamera2Fragment` 在点击录像时读取一次 `relativeOrientation.value`，创建编码器时传入 helper。该值已根据
当前镜头的 `SENSOR_ORIENTATION` 和前后置符号计算，可能为 0°/90°/180°/270°，无需在每帧路径再次按
`cameraSensorOrientation` 分流。缓存字段已经删除；旧参数仅为 `IDataProcessStrategy` 公共 API 兼容暂留，helper
传入未使用的 `-1` sentinel，内置策略不再读取。录像期间不更新角度，避免同一裸 H.264 流中途交换 SPS 宽高。

### 4.2 编码尺寸与像素变换统一

- 0°/180°：编码宽高保持相机输入 `width×height`。
- 90°/270°：编码宽高交换为 `height×width`。
- 前后置通过融合的 `YuvUtil.transformI420()` 对合法 I420 执行旋转和可选水平镜像；语义仍是“旋转后对最终
  画面水平镜像”，但不再产生多次 JNI 往返和中间 Java `ByteArray`。
- YUV420P 由同一次 JNI 直接输出 I420；YUV420SP 由同一次 JNI 直接输出 NV12。native 只在 libyuv 必须多步
  处理时分配 scratch buffer，并通过 critical array access 避免显式 native 输入/输出副本。

该方案保证 U/V 平面始终按 I420 解释，删除 `mirrorNv21()`、`rotateYUV420Degree270()` 和
`rotateYUVDegree270AndMirror()` 对 I420 输入的错误调用。旧实现通过 Git 历史保留，不复制为失效注释；这些
androidbase 公共函数本身不删除。

### 4.3 API/ABI 边界

- 保留 `extraInitializeCameraForRecording(bitrate)`，新增带可空录像角度的重载。
- 保留 `IDataProcessStrategy.doProcess(image, lensFacing, cameraSensorOrientation)` 签名。
- 旋转角通过策略构造参数注入；现有策略无参构造和 `DataProcessFactory.getConcreteObject(type)` 保留镜头相关旧
  默认值（后置 90°、前置 270°）。
- H4/M5 决策不变：`getYuvDataFromImage` 仍负责从 `Image` planes 提取 I420，不引入共享 scratch。

## 5. 测试与验证

- **JVM 单元测试**：覆盖四个角度对应的编码尺寸和非法输入；JVM 不能加载 Android native 库或构造真实
  `Image`，不能验证实际 I420/NV12 像素。
- **Android 仪器测试（后续可补）**：用合成 I420 覆盖四个角度，校验输出长度、NV12 排布、Y 平面方向和最终
  水平镜像；不能替代真机。
- **当前设备真机回归已通过；完整发布回归矩阵如下**:
  1. **前置**竖屏及两个横屏方向：SPS 宽高、画面方向、左右镜像和颜色正确，无上下颠倒、花屏、偏色或拉伸；
  2. 后置竖屏及两个横屏方向：保持已验证通过的方向和颜色；
  3. 前后置**连续快速切换**:无花屏、无 `ERROR_CAMERA_IN_USE`、方向正确；
  4. 至少覆盖前置 camera `SENSOR_ORIENTATION`=90 与 270 两组具体组合，并记录设备型号、`cameraId`、
     `lensFacing` 和 `deviceState`（如 Nexus 6/6P 的前置 camera 属 90 组合）；
  5. 录制文件回放:前置片段方向/镜像/颜色正确。
- **性能对照**:前置录制时对比改前/改后的每帧耗时或 CPU（可用现有 `measureTimeMillis`/profiler）。

**2026-08-18 线程与分配整改**：录像 `ImageReader` listener 已切到专用 `imageReaderHandler`；融合 JNI 已替代
`rotateI420` → `mirrorI420` → `i420ToNv12` 的多次调用链。当前已通过四 ABI native 构建和 JVM 公式测试，仍需
真机复核不同 YUV420P/SP 编码器的方向、镜像、颜色和持续录制稳定性。

> **术语**：`SENSOR_ORIENTATION` 属于**具体 camera（cameraId / lensFacing）**而非整机——常见手机前置约
> 270°、后置约 90°，API 32+ 折叠设备的逻辑相机方向还可能随设备状态变化。因此下文以"**前置 camera
> `SENSOR_ORIENTATION`=X**"限定覆盖范围，而非"X 类机型"。

**2026-08-17 验证结果**：维护者在其手头设备（**前置 camera `SENSOR_ORIENTATION`=270** 的常见组合）上真机
测试通过——后置横屏方向及颜色未回归；前置竖屏/横屏的方向、水平镜像和颜色均正确，无花屏、偏色、上下颠倒或
拉伸。据此解除该组合的真机合并门禁。

**⚠️ 尚未验证:前置 camera `SENSOR_ORIENTATION`=90（如 Nexus 6/6P）**。维护者手头无此类真机，无法实测。
新实现**不再按 `cameraSensorOrientation` 每帧分流**，而是依赖录像起始的 `relativeOrientation`
（`computeRelativeRotation(characteristics, rotation)` 已综合 sensor 方向与前后置符号）——因此 90° 情形
**理论上被统一路径正确覆盖**，公式部分已补 JVM 单测（见下），但**仍缺真机实证**（公式测试不替代真机 YUV
验证）。上表第 4 项（前置 camera `SENSOR_ORIENTATION` 90 与 270
各一组）仅完成 270 侧；90 侧保留为**发布前回归项**，待有 Nexus/90° camera 组合时补测，若届时发现方向/
镜像异常再针对性调整。YUV420P/YUV420SP 设备的扩大覆盖同样继续作为发布回归项。

**已补齐（Finding 3）**：`computeRelativeRotation` 的纯公式部分已抽为 `internal` 可测函数
（入参 `sensorOrientationDegrees` / `lensFacingFront` / `surfaceRotation`，与 `CameraCharacteristics` 解耦；
原 `private` 重载改为读取 characteristics 后委托该纯函数，并把 `SENSOR_ORIENTATION` 的 `!!` 改为 `?: 0`），
并在 `OrientationLiveDataTest` 补 2（sensor 90/270）× 2（前/后置）× 4（设备旋转）= 16 组合 JVM 单测。它
**不替代**真机 YUV 验证，仅为"90° 由统一路径理论覆盖"提供公式级实证，且无需 Nexus 硬件。**回归记录字段**：
设备型号 + `cameraId` + `lensFacing` + `SENSOR_ORIENTATION` + `deviceState` + YUV420P/SP。

**动态逻辑相机支持边界**：当前 `OrientationLiveData` 只在物理方向事件中重新读取
`SENSOR_ORIENTATION`，没有独立监听“仅折叠状态变化、物理方向不变”的事件。API 32+ 折叠设备的动态逻辑相机
不在本轮已验证/承诺范围内；后续支持时应由应用层提供折叠状态事件，在状态变化时重建或刷新方向源，不得依赖长期
缓存的 sensor orientation。

## 6. 影响面

- **对外 API**:无签名变更。
- **行为**：有意修复前置旧路径的竖屏输出和偏色，并显式输出合法 I420/NV12；不承诺与错误旧路径字节等价。
- **风险**:每帧 YUV 正确性已通过当前设备真机回归；不同传感器方向与 YUV 输出格式的跨设备组合仍需在发布
  回归中持续覆盖。
- **detekt/ktlint**：删除旧调用后清理失效 import，并保持共享变换函数只接收合法旋转角和正尺寸。
