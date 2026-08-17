# 3b 设计 —— camera2live 前置摄像头 YUV 旋转/镜像 native 化（H3，2026-08-13）

> 性能整改子项目 3b。**代码待真机**:本 spec 先定设计,实现与合并须在真机回归通过后进行。
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

## 2. 决策（已与维护者确认）

- **H3:做**。前置分支改走 `com.leovp.yuv.YuvUtil` native，与后置/`EncoderStrategyYuv420P` 前置范式对齐。
- **H4:不做**。`getYuvDataFromImage` 的色度 strided 提取无干净 native 路径:native 侧 `android420ToI420`
  只吃 `ByteArray`、不吃 `Image`/planes；从 `Image` 提取平面仍须 Kotlin。真 native 化需新增吃 Image planes
  的 JNI 入口（大改，另立项）。纯 Kotlin 微优化收益有限且仍改字节，风险/收益比差 → 不做。
- **M5:跳过**。`androidbase/YuvUtil` 是**无状态 `object`**，线程安全正来自"每帧新分配局部 `rowData`"。
  复用刮擦缓冲须 `ThreadLocal`/传入 scratch，否则并发帧数据竞争。`rowData`（~rowStride，1–2KB）相对不可避免的
  `data`（≈460KB/帧）是小头 → 不值得为共享 util 引入可变状态。

## 3. 现成范式与关键风险

**范式**:`EncoderStrategyYuv420P` 前置分支已用一次 native 调用完成"翻转 + 旋转":
`convertToI420(yuvData, I420, w, h, verticallyFlip = true, ROTATE_270)`（内部先翻转再旋转）。

**⚠️ 镜像轴和处理顺序不一致(本改动的头号风险)**:
- Sp 现前置用 `mirrorNv21` = **水平镜像**(左右翻转)。
- P 前置 native 用 `verticallyFlip = true` = **垂直翻转**(上下)。
- Sp 的 90° 和 270° 分支也不是同一处理顺序。若统一照抄任意一种 native 组合，某类传感器方向可能上下
  颠倒或镜像反向。**必须保留方向分支并分别真机核对。**

**格式一致性**:后置输出 NV12（`i420ToNv12`）。前置现分支返回 Kotlin 函数结果、**未显式 `i420ToNv12`**，
且这些函数按 NV 交织处理色度、其确切输出格式含糊。native 化后前置应**显式产出 NV12**，与后置一致。

## 4. 变更方案（仅 `EncoderStrategyYuv420Sp.kt`）

把前置 `else` 分支（`:47-58`）替换为 native 调用，但继续按 `cameraSensorOrientation` 分流。旧实现由 Git
历史保留，不在源码中留下整块失效注释。

### 90° 方向候选（水平镜像后旋转）

```kotlin
cameraSensorOrientation == 90 -> {
    val mirrored = com.leovp.yuv.YuvUtil.mirrorI420(i420Bytes, width, height)
    val rotated =
        com.leovp.yuv.YuvUtil.rotateI420(mirrored, width, height, com.leovp.yuv.YuvUtil.ROTATE_270)
    com.leovp.yuv.YuvUtil.i420ToNv12(rotated, height, width)
}
```

- `mirrorI420` = 水平镜像，与 90° 旧分支的亮度平面处理方向一致；由于旧代码用 NV21 函数处理 I420 色度，
  不能宣称逐字节等价，仍以真机画面为准。
- 末尾 `i420ToNv12(rotated, height, width)` 使前置与后置一致输出 NV12（维度因 270° 旋转互换）。

### 270° 方向候选（垂直翻转后旋转）

270° 旧分支的变换顺序不同，先使用与 `EncoderStrategyYuv420P` 相同的 native 组合进行独立验证:

```kotlin
else -> {
    val i420 = com.leovp.yuv.YuvUtil.convertToI420(
        i420Bytes, com.leovp.yuv.YuvUtil.I420, width, height, true, com.leovp.yuv.YuvUtil.ROTATE_270
    )!!
    com.leovp.yuv.YuvUtil.i420ToNv12(i420, height, width)
}
```

**决策规则**:两个方向分别实现、分别验证，不因其中一个方向通过就删除分支。若某一方向的候选实现与旧版
前置画面不一致，只调整或回滚该方向；在 90° 与 270° 机型都验证通过之前，不合并 native 实现。

### 保留与清理

- 旧前置 Kotlin 实现通过 Git 历史保留，不复制为失效注释。
- 后置分支不动。
- `androidbase/YuvUtil` 的 `mirrorNv21`/`rotateYUV420Degree270`/`rotateYUVDegree270AndMirror` 不删除
  （属其它模块、`@file:Suppress("unused")`），仅停止从 camera2live 调用。
- `getYuvDataFromImage` 仍用于提取 I420（H4/M5 不动）。

## 5. 测试与验证

- **JVM 单元测试**:不能直接加载 Android native 库，也不能构造真实 `Image`，不作为本项的主要验证手段。
- **Android 仪器测试**:建议把提取 I420 之后的变换拆成 internal helper，用合成 I420 数据分别覆盖 90°/270°，
  校验输出长度、NV12 排布和 Y 平面方向；这不能替代真机，但可防止后续误合并两个方向分支。
- **真机回归（合并前置条件，必须全过）**:
  1. **前置**预览/录制:画面方向、左右镜像、上下方向、颜色均与改动前**一致**（无镜像反向、无上下颠倒、无花屏/偏色）；
  2. 后置预览/录制:不受影响、正常；
  3. 前后置**连续快速切换**:无花屏、无 `ERROR_CAMERA_IN_USE`、方向正确；
  4. 覆盖 `sensorOrientation` 90 与 270 的机型各至少一台（如 Nexus 6/6P 属 90）;
  5. 录制文件回放:前置片段方向/镜像/颜色正确。
- **性能对照**:前置录制时对比改前/改后的每帧耗时或 CPU（可用现有 `measureTimeMillis`/profiler）。

## 6. 影响面

- **对外 API**:无签名变更。
- **行为**:目标是**保持前置观感不变**并显式输出合法 NV12；由于旧路径用 NV21 函数处理 I420 色度，不承诺
  字节级等价。若真机发现旧路径本就有方向/颜色瑕疵，另行记录，不在本次性能整改中擅自修正。
- **风险**:每帧 YUV 正确性 —— 由第 5 节真机回归把关；未过不合并。
- **detekt/ktlint**:native 调用用全限定名（与后置一致），不新增 import；删除旧调用后清理失效 import。
