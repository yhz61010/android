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
  - `cameraSensorOrientation == 90`（Nexus 6/6P）:`mirrorNv21(i420, w, h)`（水平镜像，原地）+
    `rotateYUV420Degree270(i420, w, h)`（旋转 270° 顺时针，返回新数组）。
  - 其它（270）:`rotateYUVDegree270AndMirror(i420, w, h)`（旋转 270° + 镜像，一步）。

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

**⚠️ 镜像轴不一致(本改动的头号风险)**:
- Sp 现前置用 `mirrorNv21` = **水平镜像**(左右翻转)。
- P 前置 native 用 `verticallyFlip = true` = **垂直翻转**(上下)。
- 两者轴不同。若照抄 P 的 `verticallyFlip=true`，前置画面可能上下颠倒/镜像反向。**必须真机核对方向。**

**格式一致性**:后置输出 NV12（`i420ToNv12`）。前置现分支返回 Kotlin 函数结果、**未显式 `i420ToNv12`**，
且这些函数按 NV 交织处理色度、其确切输出格式含糊。native 化后前置应**显式产出 NV12**，与后置一致。

## 4. 变更方案（仅 `EncoderStrategyYuv420Sp.kt`）

把前置 `else` 分支（`:47-58`）替换为 native 调用，**旧 Kotlin 分支整块注释保留**以便真机不符时快速回滚。

### 候选 B（主选：忠实移植现有"水平镜像 + 旋转 270"行为）

```kotlin
} else {
    // Front lens: horizontal mirror + rotate 270 CW, then pack to NV12 (270 swaps w/h).
    val mirrored = com.leovp.yuv.YuvUtil.mirrorI420(i420Bytes, width, height)
    val rotated =
        com.leovp.yuv.YuvUtil.rotateI420(mirrored, width, height, com.leovp.yuv.YuvUtil.ROTATE_270)
    com.leovp.yuv.YuvUtil.i420ToNv12(rotated, height, width)
}
```

- `mirrorI420` = 水平镜像，与原 `mirrorNv21` 同轴（左右），忠实保留当前前置观感。
- 统一 90/270 两子情形为"镜像 + 旋转 270"（原两分支实质都是镜像+270；`sensorOrientation` 差异按真机确认是否
  真的需要区分，默认合并）。
- 末尾 `i420ToNv12(rotated, height, width)` 使前置与后置一致输出 NV12（维度因 270° 旋转互换）。

### 候选 A（回退：对齐 P 范式，垂直翻转）

若真机显示候选 B 方向不对（例如实际需要垂直翻转），改用:

```kotlin
} else {
    val i420 = com.leovp.yuv.YuvUtil.convertToI420(
        i420Bytes, com.leovp.yuv.YuvUtil.I420, width, height, true, com.leovp.yuv.YuvUtil.ROTATE_270
    )!!
    com.leovp.yuv.YuvUtil.i420ToNv12(i420, height, width)
}
```

**决策规则**:先按候选 B 实现，真机对比前置预览（方向 + 镜像 + 颜色）与改前一致则采纳；不一致再试候选 A；
两者皆不符则保留旧 Kotlin 分支（回滚），记录现象另议。

### 保留与清理

- 旧前置 Kotlin 分支整块**注释保留**（回滚用）。
- 后置分支不动。
- `androidbase/YuvUtil` 的 `mirrorNv21`/`rotateYUV420Degree270`/`rotateYUVDegree270AndMirror` 不删除
  （属其它模块、`@file:Suppress("unused")`），仅停止从 camera2live 调用。
- `getYuvDataFromImage` 仍用于提取 I420（H4/M5 不动）。

## 5. 测试与验证

- **单元测试**:不适用。`doProcess` 依赖真实 `Image`/相机；native 转换由 libyuv 保证，不在 JVM 单测范围。
- **真机回归（合并前置条件，必须全过）**:
  1. **前置**预览/录制:画面方向、左右镜像、上下方向、颜色均与改动前**一致**（无镜像反向、无上下颠倒、无花屏/偏色）；
  2. 后置预览/录制:不受影响、正常；
  3. 前后置**连续快速切换**:无花屏、无 `ERROR_CAMERA_IN_USE`、方向正确；
  4. 覆盖 `sensorOrientation` 90 与 270 的机型各至少一台（如 Nexus 6/6P 属 90）;
  5. 录制文件回放:前置片段方向/镜像/颜色正确。
- **性能对照**:前置录制时对比改前/改后的每帧耗时或 CPU（可用现有 `measureTimeMillis`/profiler）。

## 6. 影响面

- **对外 API**:无签名变更。
- **行为**:目标是**保持前置观感不变**（仅把实现从 Kotlin 换成 native）；若真机发现旧 Kotlin 路径本就有
  方向/颜色瑕疵，另行记录，不在本次"性能等价替换"范围内擅自"修正"。
- **风险**:每帧 YUV 正确性 —— 由第 5 节真机回归把关；未过不合并。
- **detekt/ktlint**:native 调用用全限定名（与后置一致），不新增 import；前置注释块不引入未用符号。
