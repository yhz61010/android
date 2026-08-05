# `camerax` 与 `camera2live` 性能审查报告

> 审查日期：2026-08-05
> 审查基线：`master@152ef438a`，与 `origin/master` 同步
> 审查范围：`camerax`、`camera2live`，以及与拍照字节处理直接相关的 `lib-image` 代码
> 审查方式：静态代码审查、调用链检查、目标模块 Lint；尚未执行真机性能采样

## 1. 结论摘要

本次共识别 13 个性能或资源管理问题，其中 8 个为 P1，5 个为 P2。风险主要集中在以下路径：

- `camera2live` 录像链路逐帧执行全尺寸 YUV 转换，并把结果写入无界队列；编码速度落后时可能同时出现高 CPU、持续内存增长和丢失实时性。
- `camera2live` 拍照链路存在 `Image` 未关闭和协程永久等待的路径，可能耗尽 `ImageReader` 缓冲区。
- `camerax` 分析、能力诊断和全尺寸字节拍照存在不必要的逐帧分配、Codec 实例创建及较高峰值内存。
- 两个模块均有资源生命周期长于 View 生命周期的情况，旋转、返回和重复进入页面时可能累积线程、监听器或媒体资源。

以下内存和吞吐量数字均为按分辨率和数据格式计算的理论值，不是真机实测结果，必须通过 Profiler 或 Perfetto 复核。

## 2. P1 问题

### PERF-C2-01 录像逐帧 YUV 转换占用 Camera Handler

- 位置：`camera2live/.../Camera2ComponentHelper.kt:727-805`
- 现状：`ImageReader` 回调运行在 `cameraHandler`，每帧调用 `dataProcessContext.doProcess()` 完成格式转换、旋转或镜像，然后把新 `ByteArray` 交给编码器。
- 影响：Camera 回调线程同时承担帧读取和 CPU 密集型图像处理，处理时间超过帧间隔时会阻塞后续相机事件。1080p I420 单帧约 2.97 MiB；30 FPS 下，每增加一次全帧复制，理论内存写入量约 89 MiB/s。若处理链产生 2 至 3 份数组，理论分配或复制吞吐量约 178 至 267 MiB/s。
- 建议：优先改为 MediaCodec 输入 `Surface`，通过 Camera2 或 EGL 直接向编码器送帧。若暂时保留 ByteBuffer 输入，应使用独立帧处理线程、只保留最新帧、复用缓冲区，并尽量把转换合并为一次 native 操作。传感器方向等固定参数应在会话建立时缓存。
- 验证：1080p/30 FPS 连续录像 10 分钟，记录 Camera 回调耗时、帧处理耗时、CPU、分配速率、GC、实际编码 FPS 和丢帧数。

### PERF-C2-02 编码器输入队列无界

- 位置：`camera2live/.../codec/CameraAvcEncoder.kt:26,202-204`
- 现状：录像帧存入 `ConcurrentLinkedQueue<ByteArray>`，没有容量上限或过载丢帧策略。
- 影响：生产速度高于编码速度时，队列会持续增长。1080p I420 每帧约 2.97 MiB，仅积压 30 帧就接近 89 MiB，最终可能引发频繁 GC、OOM，并使视频延迟持续增加。
- 建议：改为容量很小的有界队列，实时录像优先采用“丢弃最旧帧、保留最新帧”的策略；同时暴露队列高水位和丢帧计数用于诊断。停止编码时必须清空队列。
- 验证：人为降低编码吞吐量，确认队列长度保持在固定上限，内存稳定，时间戳单调且延迟不会持续增加。

### PERF-C2-03 无输入帧时仍提交空 Codec Buffer

- 位置：`camera2live/.../codec/CameraAvcEncoder.kt:121-135`
- 现状：`onInputBufferAvailable()` 中即使 `queue.poll()` 返回 `null`，仍提交长度为 0 的输入 Buffer，并递增 `mFrameCount`。
- 影响：空闲或低帧率时可能形成无意义的 Codec 回调循环；时间戳按空 Buffer 次数推进，而不是按真实帧推进，导致视频时基与实际采集节奏偏离。
- 建议：分别管理可用输入 Buffer ID 和待编码帧，只有两者同时存在时才提交；不要在 Codec 回调线程阻塞等待。PTS 应来自采集时间戳或仅在真实帧提交时递增。
- 验证：相机无帧、低 FPS 和正常 30 FPS 三种场景下检查回调频率、CPU 占用、输出帧数和 PTS 间隔。

### PERF-C2-04 拍照链路泄漏 Image 并永久等待

- 位置：`camera2live/.../Camera2ComponentHelper.kt:870-1001`
- 现状：拍照前清空 `ImageReader` 时没有关闭取出的旧 `Image`；匹配时间戳时，遇到不匹配的 `Image` 直接 `continue`，同样没有关闭。成功 `cont.resume()` 后循环没有退出，监听器已移除且队列为空时会继续阻塞在 `imageQueue.take()`。
- 影响：未关闭的 `Image` 会占用 `ImageReader` 的有限缓冲区，后续拍照可能停滞；永久等待还会占用协程及其执行线程。异常、超时和取消路径也缺少统一资源清理。
- 建议：所有丢弃的 `Image` 立即 `close()`；成功恢复 continuation 后退出循环。用结构化并发和 `try/finally` 统一清理监听器、超时任务、队列及其中的图像，并保证 continuation 只恢复一次。
- 验证：连续拍照 100 次、快速连点、切换摄像头、超时和离开页面场景下，确认没有 `maxImages` 错误、阻塞协程或未关闭图像。

### PERF-C2-05 Helper 线程和 Executor 生命周期过长

- 位置：`camera2live/.../Camera2ComponentHelper.kt:93,419-447,1198-1215`；`camera2live/.../view/BaseCamera2Fragment.kt:88-89,203-220`
- 现状：Helper 持有单线程 Executor、两个 `HandlerThread` 和 View 引用。`stopCameraThread()` 只停止 HandlerThread，没有关闭 `singleExecutor`；Fragment 只在 `onDestroy()` 停线程，在 `onDestroyView()` 仅清空 Binding。
- 影响：View 被重建而 Fragment 仍存活时，旧 Helper 可能继续持有已销毁 View 和后台线程。多次旋转或进出页面后，线程和资源可能累积。
- 建议：让 Helper 与 View 生命周期对齐，在 `onDestroyView()` 完整释放并清空引用，需要恢复时创建新实例；关闭 `singleExecutor`。释放方法应幂等，并等待或确认线程退出。
- 验证：连续旋转和前后台切换 20 次，比较操作前后的线程数、Helper 实例数和 View 泄漏情况。

### PERF-CX-01 亮度分析逐帧复制并创建装箱集合

- 位置：`camerax/.../analyzer/LuminosityAnalyzer.kt:71-80`；`camerax/.../fragments/CameraFragment.kt:364-383`
- 现状：每帧先把 Y 平面复制为 `ByteArray`，再通过 `map` 创建 `List<Int>`，随后求平均值；分析使用与预览相同的 `resolutionSelector`，并逐帧输出 verbose 日志。
- 影响：装箱后的 `List<Int>` 会产生大量对象和 GC 压力，全分辨率分析还放大内存带宽与 CPU 消耗。逐帧日志会进一步影响调试构建的帧率。
- 建议：为分析单独配置较低目标分辨率；直接遍历 `ByteBuffer`，或按固定步长采样 Y 平面，使用 `Long` 累加，不创建中间数组和集合。日志改为采样输出，并用 `try/finally` 保证 `ImageProxy.close()`。
- 验证：比较修改前后 Analyzer 平均耗时、P95 耗时、分配速率、GC 次数和预览 FPS，确认亮度误差在可接受范围内。

### PERF-CX-02 能力诊断反复创建 MediaCodec 且未释放

- 位置：`camerax/.../utils/CodecExt.kt:35-58`；`camerax/.../fragments/base/BaseCameraXFragment.kt:900-981`；调用点 `CameraFragment.kt:269-272`
- 现状：四个 capability helper 通过 `MediaCodec.createEncoderByType()` 或 `createDecoderByType()` 获取 `codecInfo`，创建后没有 release。`outputCameraParameters()` 在每次 bind 时执行多项查询。
- 影响：切换摄像头或比例会重复创建 Codec 实例，可能增加绑定耗时，并在部分设备上耗尽厂商 Codec 实例。
- 建议：直接遍历 `MediaCodecList(REGULAR_CODECS).codecInfos` 并调用 `getCapabilitiesForType()`；按 MIME 和编码/解码类型缓存结果。完整能力日志只应在 Debug 或显式诊断开关下执行，并限制为每个 Camera/MIME 一次。
- 附带问题：HEVC profile 日志当前实际传入 AVC MIME（`BaseCameraXFragment.kt:954-957`），应一并修正。
- 验证：重复 bind 20 次，确认不创建活动 Codec 实例，且绑定耗时不随次数增长。

### PERF-CX-03 全尺寸字节拍照峰值内存过高

- 位置：`camerax/.../fragments/base/BaseCameraXFragment.kt:225-280`；`lib-image/.../ImageExt.kt:36-46`；`lib-image/.../BitmapProcessor.kt:22-81`
- 现状：字节模式先保留原始 JPEG，再解码源 Bitmap、复制到 native 缓冲区并创建处理后 Bitmap；`Bitmap.toBytes()` 又同时分配同尺寸 `ByteBuffer` 和 `ByteArray`。
- 影响：12 MP ARGB_8888 单份像素约 45.8 MiB，多份 Bitmap、native 副本和字节缓冲同时存在时，理论峰值可能超过 150 至 200 MiB，低内存设备容易 OOM。并行触发拍照会进一步放大风险。
- 建议：第一阶段保持 `CaptureImage.ImageBytes` 的 raw pixel 数据格式、宽高和字节顺序不变，使用 `ByteBuffer.wrap(result)` 直接写最终数组，删除额外的同尺寸 backing buffer；通过 `use/finally` 尽早释放 Bitmap/native 缓冲，并禁止并行执行多个全尺寸字节拍照任务。后续可增加显式目标分辨率或一次性 native 输出接口。
- 兼容要求：不能在 5.x 中静默把 raw ARGB payload 改为 JPEG/PNG，否则属于数据格式行为变更。
- 验证：用固定像素样本确认优化前后 payload 完全一致；前后摄分别连续拍摄 12 MP 图片 20 张，记录 Java/native heap 峰值和 GC，API 21 中低内存真机不得 OOM。

## 3. P2 问题

### PERF-CX-04 重复 bind 累积曝光监听器

- 位置：`camerax/.../fragments/CameraFragment.kt:408-432`，统一清理位于 `:202`
- 现状：每次 `bindCameraUseCases()` 都调用 `sliderExposure.addOnChangeListener()`，同一 View 生命周期内切换摄像头或比例会累积监听器。
- 影响：一次滑动会触发多次日志和 `setExposureCompensationIndex()`，重复 bind 越多，请求次数越多。
- 建议：在 `onViewCreated()` 或 UI 初始化阶段只注册一次，通过当前 `camera` 字段发送请求；仅处理 `fromUser=true` 的变化。也可保存 listener 引用，在重新注册前移除旧 listener。
- 验证：重复 bind 20 次后模拟一次滑动，确认只发送一次曝光请求，且请求发给当前 CameraControl。

### PERF-CX-05 每次 bind 重建 ExtensionsManager 查询

- 位置：`camerax/.../fragments/base/BaseCameraXFragment.kt:661-738`；调用点 `CameraFragment.kt:387-395`
- 现状：每次 bind 都调用 `ExtensionsManager.getInstanceAsync()`，并依次查询和记录多个扩展模式。
- 影响：重复异步初始化和厂商扩展查询会增加绑定开销；旧回调返回时还可能作用于已经变化的镜头或 View 状态。
- 建议：在 CameraProvider 就绪后初始化并缓存 ExtensionsManager；按镜头缓存扩展能力，仅在镜头或配置变化时刷新。回调应用结果前检查当前生命周期和选择器。
- 验证：重复切换比例和镜头，记录 ExtensionsManager 初始化次数、回调次数及 bind 耗时。

### PERF-CX-06 View 重建时重复创建 SoundPool

- 位置：`camerax/.../fragments/base/BaseCameraXFragment.kt:178-206`；`camerax/.../utils/SoundManager.kt:28-43,69-73`
- 现状：每次 `onCreateView()` 都调用 `loadSounds()` 创建新的 `SoundPool`，但只在 Fragment `onDestroy()` 时 release。
- 影响：仅重建 View 时，旧 SoundPool 未释放就被新实例覆盖，可能累积 native 音频资源，并发生声音尚未加载完成就播放的竞态。
- 建议：重复加载前先释放旧实例，或把 SoundPool 明确绑定到 Fragment 生命周期并只加载一次；记录加载完成状态后再允许播放。
- 验证：旋转 20 次并重复播放快门音，检查 SoundPool/native 资源数量、日志和播放成功率。

### PERF-CX-07 Gallery 在主线程扫描和排序文件

- 位置：`camerax/.../fragments/GalleryFragment.kt:56-95`
- 现状：`onCreate()` 在主线程执行 `listFiles`、扩展名转换、全量排序并输出完整列表；`containsItem()` 每次线性扫描媒体列表。
- 影响：媒体数量较多时会阻塞 Gallery 首帧；数据刷新时，`containsItem()` 产生重复 O(n) 查询。
- 建议：先用空列表创建 Adapter，在 IO Dispatcher 扫描和排序，回到主线程更新；删除完整列表日志。为稳定 ID 建立同步更新的 `HashSet<Long>` 或显式 ID 映射，使 `containsItem()` 为 O(1)，同时处理 hash 冲突。
- 验证：分别生成 1,000 和 10,000 个混合扩展名文件，记录 Gallery 首帧时间，确认主线程不执行全量目录扫描。

### PERF-CX-08 CameraX UseCase 生命周期长于 View

- 位置：`camerax/.../fragments/CameraFragment.kt:408-414`
- 现状：UseCase 绑定到 Fragment 的 `LifecycleOwner`（`this`），而分析器和 UI 回调直接引用当前 View Binding；View 销毁但 Fragment 未销毁时，Camera 和 Analyzer 仍可能继续运行。
- 影响：页面视图不可见期间仍消耗相机、分析线程和电量，也增加访问已销毁 View 的风险。
- 建议：先确认产品是否要求跨 View 重建保持 Camera 会话。若无该要求，绑定到 `viewLifecycleOwner`；若必须保持会话，则分析和 UI 回调不得持有旧 View，并应在 `onDestroyView()` 主动解除 Analyzer/UI 观察者。
- 验证：导航离开、返回、旋转和后台恢复时检查 Camera 状态、Analyzer 调用次数及旧 View 是否被持有。

## 4. Lint 结果

已执行：

```bash
./gradlew :camerax:lintDebug :camera2live:lintDebug --rerun-tasks
```

结果为 `BUILD SUCCESSFUL`，耗时约 1 分 51 秒，共执行 499 个任务。与性能相关或需要视觉复核的提示包括：

- `camerax` 中 4 个长 VectorDrawable path：`ic_fps_30`、`ic_fps_60`、`ic_ratio_16v9`、`ic_ratio_16v9_on`。这类资源可能增加首次解析和栅格化成本，但优先级低于运行时录像链路。
- Lint 报告 `camerax` 5 个布局、`camera2live` 1 个布局可能存在 overdraw。该检查会根据主题背景推断，存在误报可能，应结合最终 App 主题和 GPU Overdraw 实测后再决定是否删除背景。

## 5. 建议实施顺序

1. 先修复 `PERF-C2-04` 的 Image 关闭和循环退出，避免资源耗尽。
2. 联动修复 `PERF-C2-01`、`PERF-C2-02`、`PERF-C2-03`，形成有背压、时间戳正确的录像输入链路。
3. 修复 `PERF-C2-05`，明确 Helper、线程和 Executor 的生命周期。
4. 修复 `PERF-CX-02` 和 `PERF-CX-04`，降低重复 bind 的固定成本和资源累积。
5. 修复 `PERF-CX-01` 和 `PERF-CX-03`，控制逐帧分配及拍照峰值内存。
6. 最后处理 ExtensionsManager、SoundPool、Gallery 和 CameraX 生命周期问题，并通过真机数据确认收益。

## 6. 真机验证矩阵

至少覆盖一台 API 21 至 26 的中低内存设备和一台 API 33 以上设备；如果可用，应补充一台使用厂商硬件编解码器差异明显的设备。

| 场景 | 操作 | 关键指标与通过条件 |
|---|---|---|
| 录像稳定性 | 1080p/30 FPS 连续录像 10 分钟 | FPS 稳定；队列不超过上限；内存不持续增长；无 Codec 或 ImageReader 资源错误 |
| 编码过载 | 人为降低编码消费速度 | 丢帧策略生效；延迟不持续累积；PTS 单调且只对应真实帧 |
| 拍照稳定性 | 前后摄各连续拍照 20 至 100 次 | 无 OOM、无 `maxImages`、无永久等待；raw payload 兼容 |
| 重复绑定 | 切镜头和比例共 20 次 | Codec 实例不累积；一次曝光操作只产生一次请求；bind 耗时不随次数增长 |
| 生命周期 | 旋转、后台恢复、进出页面各 20 次 | 线程数和 native 资源回到基线；旧 View 不泄漏；预览可恢复 |
| Gallery | 目录放入 1,000/10,000 个媒体文件 | 首帧期间主线程无全量文件扫描；列表滚动和删除无明显卡顿 |

建议使用 Perfetto、Android Studio CPU/Memory Profiler、System Trace、LeakCanary（仅调试构建）以及 `dumpsys media.codec` 辅助验证。测试时同时记录设备型号、系统版本、分辨率、实际 FPS、编码器名称和构建类型，避免把单一设备结果当成通用结论。

## 7. 审查边界

- 本报告未修改生产代码，也未把性能结论同步到 2026-08-04 的三份模块审查和整改文档。
- 当前结论来自静态代码和 Lint。CPU 占用、分配速率、峰值内存、过绘制和帧率影响仍需按上述矩阵真机测量。
- 使用 Surface 输入替代字节输入会影响现有录像处理链设计，实施前需确认是否仍要求在编码前由 CPU 完成旋转、镜像或自定义 YUV 处理。
