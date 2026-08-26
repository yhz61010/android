# 相机性能问题后续整改（2026-08-18）

## 范围

本轮关闭 Camera2Live 与 CameraX 的 7 项性能问题，并默认保持现有公开输出行为。

## 修改

1. Camera2Live 录像帧改由 `imageReaderHandler` 处理；Camera2 控制回调继续使用 `cameraHandler`。
2. `CameraAvcEncoder` 保存可用 input buffer ID，等真实帧到达后才提交；不再提交空 buffer，PTS 只随真实提交帧
   推进。ID 仅在 `queueInputBuffer()` 成功后从本地池移除；帧被拒绝时保留 ID 供下一帧复用，stop/release 后也会
   拒绝迟到帧。
3. 编码输出复制后立即释放 MediaCodec output buffer；独立的编码字节通过专用串行回调线程交给外部调用方。
4. 新增 `YuvUtil.transformI420()`，在一次 JNI 调用内完成旋转、可选水平镜像以及 I420/NV12 输出。JNI 通过
   critical array access 避免显式 native 输入/输出副本，仅在 libyuv 必须多步处理时使用 native scratch buffer。
5. `Bitmap.toBytes()` 改成单个 `ByteArray` 配合 `ByteBuffer.wrap()`，不再额外分配一份完整像素 backing buffer；
   拍照路径在分配 raw 输出字节或编码变换后文件前，也会提前回收已不再使用的独立源 Bitmap。
6. CameraX 能力诊断默认关闭。宿主可通过 `CameraXActivity.isCameraDiagnosticsEnabled()` 显式开启；codec 枚举在
   `Dispatchers.Default` 执行，完整格式化结果按 camera 与显示方向缓存。
7. 新增明确的 `JpegOutputStrategy`：
   - `PIXEL_NORMALIZED` 为兼容现有调用方继续保持默认；
   - `EXIF_ONLY` 保留压缩 JPEG 像素，只写入或保留方向元数据，从而避免全分辨率 Bitmap 解码、变换和重编码。

CameraX 宿主可覆写 `CameraXActivity.getJpegOutputStrategy()`；Camera2Live 调用方可设置
`Camera2ComponentHelper.jpegOutputStrategy`。

## 验证

- `:yuv:assembleDebug` 已对全部配置 ABI 构建通过。
- `:camera2live:testDebugUnitTest`、`:camerax:testDebugUnitTest`、`:lib-image:testDebugUnitTest` 均通过
  `--rerun-tasks` 强制执行。
- Camera2Live 输入调度测试覆盖“超大帧保留 input buffer ID，后续合法帧复用并消费同一槽位”。
- 目标 Kotlin 模块已通过 `--rerun-tasks` 编译。

当前没有连接设备。录像方向/颜色、不同厂商 MediaCodec 行为，以及目标相册对 `EXIF_ONLY` 的渲染仍需真机回归。
