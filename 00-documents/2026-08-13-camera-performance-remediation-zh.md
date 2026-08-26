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
| M7 | `camera2live/Camera2ComponentHelper.kt:926-927` | 每帧重查 `SENSOR_ORIENTATION`（旧实现） | 每帧 |

---

## 3. 拆分依据

按**位置 + 风险**拆成 3 个子项目，一个个走 brainstorm → 实现，风险从低到高：

1. **子项目 1（camerax 基础设施）** = H1、M3、M4 —— 后 M3 移交子项目 2（见下）。行为保持、无设备依赖。
2. **子项目 2（LuminosityAnalyzer）** = H2、M1、M2、M3。含"分析器去留"决策。
3. **子项目 3（camera2live + 共享 YuvUtil）** = H3、H4、M5、M6、M7。最高风险，含每帧 YUV 正确性。
   进一步再拆：
   - **3a（低风险机械）** = M7。
   - **3b（YUV 正确性，必须真机）** = H3 及同路径的前置方向/颜色修复（H4、M5 决定不做，见 §4 / §6）。
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

### 子项目 3a —— 已完成，后由 3b 取代（commit `b1ff02064`）

- **M7（缓存 sensor orientation）**：3a 曾在 `initializeParameters()` 里缓存一次，消除 image-available
  回调的每帧查询。3b 改为录像开始时锁定 `relativeOrientation` 后，内置 YUV420P/SP 策略已不再读取该值，因此
  缓存字段已删除；`IDataProcessStrategy` 参数仅为公共 API 兼容暂留，helper 传入未使用的 `-1` sentinel，计划在
  下一次破坏性版本删除。该旧参数不作为 API 32+ 折叠设备逻辑相机的方向来源。

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
| 3b | **H3 + 前置方向/颜色正确性**（H4/M5 不做） | ✅ 已推送；当前设备组合已真机验证，前置 `SENSOR_ORIENTATION`=90 组合待发布回归 | `117e26213`、`8afc8a845`(取消处理)；spec 见 `superpowers/specs/2026-08-13-cam2-front-yuv-native-design.md` |
| M6 | 编码帧池化 | ⛔ 跳过 | — |

---

## 6. 子项目 3b —— 前置方向/颜色正确性整改（当前设备组合已验证，前置 `SENSOR_ORIENTATION`=90 组合待回归）

经 Explore 深度探查与后续真机验证（native `com.leovp.yuv.YuvUtil` API、前置 native 范式、纯 Kotlin函数
方向语义、`getYuvDataFromImage` 与线程模型）后，3b 收敛为 H3 及同一路径的方向/颜色正确性修复；H4、M5
探查后决定不做。设计详见
`superpowers/specs/2026-08-13-cam2-front-yuv-native-design.md`。

- **H3 + 正确性修复（当前设备组合已通过真机门禁）**：真机已确认前置旧路径存在两个实际缺陷：横屏仍编码成竖屏，且
  YUV420SP 设备颜色错误。方向问题来自固定 270°变换和旧编码尺寸；偏色来自把 I420 数据交给 NV21 色度函数。
  新实现统一使用 native I420 旋转，并在旋转后的尺寸上做 I420 水平镜像；YUV420SP 显式输出 NV12，YUV420P
  保持 I420。`OrientationLiveData` 已包含镜头传感器方向差异，因此变换按录像开始时锁定的相对角执行，不再按
  `cameraSensorOrientation` 复制两套固定 270°分支。
- **H4（不做）**：色度 strided 提取无干净 native 路径（native `android420ToI420` 只吃 `ByteArray`、不吃
  `Image`/planes；从 Image 提取平面仍须 Kotlin）。真 native 化要新增吃 planes 的 JNI 入口（大改，另立项）；
  纯 Kotlin 微优化收益有限且仍改字节，风险/收益比差。
- **M5（不做）**：`androidbase/YuvUtil` 是**无状态 `object`**，线程安全正来自"每帧新分配局部 `rowData`"。
  复用须 `ThreadLocal`/传入 scratch；而 `rowData`（~1–2KB）相对 `data`（≈460KB/帧）是小头，不值得为共享
  util 引入可变状态。

**验证结果（2026-08-17，当前设备组合已通过）**：`SENSOR_ORIENTATION` 属于**具体 camera（cameraId /
lensFacing）**而非整机——常见手机前置约 270°、后置约 90°，API 32+ 折叠设备的逻辑相机方向还可能随设备状态
变化。维护者在其手头设备（**前置 camera `SENSOR_ORIENTATION`=270** 的常见组合）上确认本轮 Camera2Live
照片、预览和录像真机测试均通过：照片方向与水平镜像正确；后置横屏录像方向和颜色保持正确；前置横屏已按横屏
输出，水平镜像和颜色正确，无花屏。据此解除该组合的真机门禁。

**⚠️ 前置 camera `SENSOR_ORIENTATION`=90 组合（如 Nexus 6/6P）尚未验证**：维护者无此类真机，无法实测。新
实现不再按 `cameraSensorOrientation` 每帧分流，90° 情形理论上由 `relativeOrientation`（`computeRelativeRotation()`
已综合 sensor 方向与前后置符号）统一覆盖，但**仍缺真机实证**——`computeRelativeRotation` 的纯公式已抽为 `internal` 可测函数并补
2×2×4=16 组合 JVM 单测（`OrientationLiveDataTest`，佐证公式在 sensor 90/270 × 前后置 × 四方向下正确），
但公式测试**不替代**真机 YUV 验证，故 90° 组合仍保留为发布前回归项。YUV420P/YUV420SP 设备的扩大覆盖同样
保留在回归矩阵中。

**回归记录字段（建议）**：设备型号 + `cameraId` + `lensFacing` + `SENSOR_ORIENTATION` + `deviceState` +
YUV420P/SP，逐组合记录方向/镜像/颜色结果。详见 3b spec §5。

**动态逻辑相机支持边界**：当前 `OrientationLiveData` 会在每次物理方向事件中重新读取
`CameraCharacteristics.SENSOR_ORIENTATION`，但不会独立监听“仅折叠状态变化、物理方向不变”的事件。因此 API
32+ 折叠设备的动态逻辑相机不在本轮已验证/承诺范围内。若后续纳入支持，应由应用层接入折叠状态来源（例如
AndroidX Window），在状态变化时重建或刷新方向源；不能恢复为长期缓存 `SENSOR_ORIENTATION`。

### ⚠️ 剩余验证事项

- 子项目 1/3a 是**行为保持型**改动。子项目 2 则是已确认的默认行为调整：停用亮度分析日志，并从默认绑定组合中
  移除 `ImageAnalysis`；这可能改变 CameraX 的 use-case 组合与分辨率协商。维护者已完成 CameraX 真机冒烟，
  相机预览、拍照、录制及前后镜头切换均正常，结果详见下一节。
- 本仓库环境不做本地编译；detekt/ktlint/单测由维护者本地执行。

### 2026-08-17 camerax 真机验证记录

#### 已通过项目

- 拍照功能正常：前后摄像头、取景预览、左右横屏拍照均正常。
- 录像基本流程正常：前后摄像头均可录像，取景画面比例正确且没有拉伸或变形。

#### 问题现象

1. `CameraXDemoActivity` 固定为 `userPortrait`。使用后置摄像头以设备物理方向 90°/270° 横屏录像后，进入
   文件预览功能查看录制结果，视频仍按竖屏方向显示；宽高比例正确，没有变形，但不符合横屏录像的预期。
2. 期望行为是：摄像模式的取景框继续保持现有竖屏布局；录制文件保存为正确的横屏方向；在竖屏文件预览页中，
   横屏视频垂直居中，上下显示黑边。

#### 第一次修改方案及遇到的问题（已撤回）

- 第一次方案使用 `OrientationEventListener` 获取设备物理方向，通过 `UseCase.snapToSurfaceRotation()` 转成
  CameraX target rotation，同时更新 `VideoCapture.targetRotation`、`Preview.targetRotation` 和取景框比例。
- 该方案错误地把“录像输出方向”与“摄像界面布局”绑定在一起。真机反馈显示，设备横屏后摄像模式的取景框也
  变成横屏，不符合“取景框保持竖屏”的交互要求。
- 第一次方案只调整了摄像界面的 `PreviewView`，没有处理文件预览页的 `VideoView` 布局。因此横屏文件虽然能
  按横屏内容显示，但在文件预览页中仍然居上，而不是垂直居中。

#### 最终修改方案（已真机验证）

- **录像方向与摄像取景框解耦**：`VideoFragment` 保留 `OrientationEventListener`，但物理方向只用于更新
  `VideoCapture.targetRotation`。`Preview` 继续使用 `viewFinder.display.rotation`，取景框比例继续按
  `resources.configuration.orientation` 计算，因此摄像界面维持原有竖屏布局。
- **生命周期处理**：方向监听器在 `onStart()` 启用、`onStop()` 停用。CameraX 1.4.1 的动态 target rotation
  只影响设置后新开始的录像，不改变正在录制的文件；当前录像保持开始时方向，下一段录像使用最新设备方向。
- **文件预览垂直居中**：`PhotoFragment` 不再直接返回裸 `VideoView`，而是使用黑色 `FrameLayout` 作为
  全屏容器，并通过 `Gravity.CENTER` 放置 `VideoView`。横屏视频保持原比例并在竖屏页面中垂直居中，剩余区域
  显示为上下黑边。
- **资源清理**：`PhotoFragment.onDestroyView()` 停止视频播放并释放对 `VideoView`、`MediaController` 的引用，
  避免 ViewPager 页面销毁后继续持有旧 View。

#### 复测要求

1. 后置摄像头分别以物理方向 90°、270° 录像，确认摄像模式取景框始终保持原有竖屏布局。
2. 回放上述文件，确认内容方向为横屏、比例无变形、画面垂直居中且上下黑边宽度基本一致。
3. 回归前置摄像头横屏录像，以及前后摄像头竖屏录像与回放。
4. 录像过程中旋转设备应保持稳定，当前文件保持开始录像时的方向；停止后再次录像时，新文件采用最新方向。
5. 快速进入/退出文件预览并切换多个图片/视频页面，确认无崩溃、黑屏或播放资源残留。

#### 前置横屏照片缩略图方向修复（已真机验证）

- **问题现象**：拍照页面左下角的相册按钮默认显示最后一张照片的缩略图。前置摄像头横屏拍照后，相册中的
  最终照片方向正确，但按钮缩略图仍显示为错误方向。
- **原因**：`captureForOutputFile()` 收到 CameraX 文件保存回调后，通过 IO 协程旋转、镜像并重写 JPEG，旧实现
  没有等待该处理完成就调用成功回调。`CameraFragment` 随即将 URI 交给 Coil 加载缩略图，Coil 可能读取并缓存
  尚未旋转/镜像的原始文件；后台随后覆盖同一路径，也不会自动刷新已经缓存的缩略图。
- **修改方案**：成功回调移入同一个 IO 处理块，只有在 Bitmap 解码、方向/镜像调整和 JPEG 写入全部成功后才
  通知 `CameraFragment` 更新缩略图。解码或写入失败则回调异常，不再把未完成的文件报告为成功。
- **复测要求**：前置摄像头分别以竖屏、90°、270° 拍照，确认左下角缩略图与相册最终照片方向一致；连续快速
  拍摄多张照片，确认缩略图始终对应最新一张且不会短暂显示错误方向。后置摄像头同样回归三个方向。

### 2026-08-17 Camera2Live 真机测试问题闭环

本轮 Camera2Live 真机测试不是一次完成。照片和裸 H.264 录像分别经历了“发现方向问题、修正方向、暴露镜像或
颜色问题、统一最终语义”的过程。以下按实际测试顺序记录问题、原因和最终解决方案；照片与录像的实现细节及
回归矩阵见后续两节。

#### 1. 后置横屏照片仍按竖屏显示

- **现象**：设备处于物理方向 90°/270° 时拍照，最终 JPEG 仍按竖屏显示。
- **原因**：拍照请求只根据 `Display.rotation` 设置 `JPEG_ORIENTATION`。宿主界面没有随物理持机方向旋转时，
  `Display.rotation` 不能代表真实设备方向；已有的 `OrientationLiveData` 结果只用于日志，没有进入拍照请求。
- **解决方案**：在按下快门时锁定 `relativeOrientation.value` 并传给 `takePhoto()`；请求和
  `CombinedCaptureResult` 使用同一方向值。方向不可用时才回退原有 `Display.rotation` 计算，以保留旧 API 的
  兼容行为。

#### 2. 第一轮照片方向修复后，90°/270° 左右镜像仍不正确

- **现象**：横屏方向已经正确，但照片左右关系仍与取景预期不一致，前置镜头尤为明显。
- **原因**：第一轮实现虽然计算了包含水平镜像的 `CombinedCaptureResult.orientation`，`saveResult()` 却仍把
  相机返回的 JPEG 字节直接写入文件。方向和镜像值没有应用到最终像素，结果依赖相册是否解释相关元数据。
- **解决方案**：普通 JPEG 在 IO 线程解码为 Bitmap，通过 `decodeExifOrientation()` 应用旋转和水平镜像后
  重写文件，使最终像素完成方向归一化；`DEPTH_JPEG` 继续原样保存，避免破坏深度元数据。同时修正
  `ORIENTATION_UNKNOWN` 和 316°–359° 的方向监听边界，并在切换镜头后重建方向源。

#### 3. 后置横屏录像在 `ffplay` 中仍按竖屏显示

- **现象**：后置摄像头以物理方向 90°/270° 录像后，裸 `camera.h264` 仍显示为竖屏。问题文件的 `ffprobe`
  结果为 `width=1080`、`height=1920`，且没有 SAR/DAR 信息。
- **原因**：保存的是裸 H.264 elementary stream，没有 MP4/MOV 容器的旋转矩阵可供播放器修正。旧编码路径固定
  旋转 90°，并使用由显示方向推导的 `previewSize` 初始化编码器，导致 SPS 本身就是 `1080x1920`。
- **解决方案**：录像开始时锁定相机相对旋转角；0°/180° 保持输入宽高，90°/270° 交换宽高，并使用同一角度
  旋转每帧 I420。编码器尺寸直接由相机输入尺寸和锁定角度计算，使横屏裸码流直接编码为横屏像素及横屏 SPS。

#### 4. 横屏录像黑边需求经确认不属于裸码流输出

- **现象与澄清**：测试过程中曾期望横屏录像在竖屏播放区域内上下显示黑边，随后确认当前产物是裸 H.264。
- **原因**：黑边属于播放器布局或主动填充后的编码像素；裸 H.264 不包含容器显示矩阵，也不会自动表达
  letterbox 布局。
- **处理结论**：本次不向码流填充黑边，只保证 SPS 宽高、像素方向和比例正确。使用 `ffplay` 直接播放时按码流
  原始横屏尺寸显示；若以后封装为 MP4/MOV，再由容器元数据或播放器布局处理黑边。

#### 5. 后置录像修复通过后，前置横屏仍为竖屏且颜色错误

- **现象**：第一轮动态方向只修复了后置镜头；后置 90°/270° 方向和颜色正确，但前置横屏录像仍按竖屏显示。
  前置画面没有花屏，但颜色明显不正确。
- **原因**：第一轮前置路径仍固定旋转 270°并沿用竖屏编码尺寸；同时 YUV420SP 路径取得的是 I420，却调用
  `mirrorNv21()`、`rotateYUV420Degree270()` 等 NV21 函数解释 U/V 平面，因此长度合法但色度错误。
- **解决方案**：前后镜头统一使用录像开始时锁定的相对角。共享变换先旋转合法 I420，再按旋转后的宽高对
  前置画面做水平镜像；YUV420P 输出 I420，YUV420SP 最后显式转换为 NV12，不再让 I420 数据进入 NV21 函数。

#### 最终验证结果

- Camera2Live 前后摄像头的竖屏、90°和 270°照片方向及水平镜像正确，比例无变形。
- 后置横屏裸 H.264 的方向、宽高和颜色正确；前置横屏录像方向、水平镜像和颜色正确，未出现花屏或拉伸。
- 镜头切换、预览、拍照和录像回归均通过。不同 `cameraId` / `lensFacing` /
  `SENSOR_ORIENTATION` / `deviceState` 组合与 YUV420P/YUV420SP 设备的扩大覆盖继续保留在发布回归矩阵中。

### 2026-08-17 Camera2Live 横屏照片方向修复（已真机验证）

#### 问题现象

- 使用 Camera2Live 后置摄像头在设备物理方向 90°/270° 拍照时，最终 JPEG 仍按竖屏方向显示，没有按拍摄时的
  横屏方向显示。
- 第一阶段方向修复后，真机复测发现 90°/270° 照片的左右镜像仍不正确，与 CameraX 之前仅依赖方向元数据时的
  表现类似。

#### 原因

- `Camera2ComponentHelper.getJpegOrientation()` 只读取 `Display.rotation`。当 Activity 方向未及时跟随设备物理
  方向，或宿主锁定界面方向时，该值不能代表用户实际持机方向。
- `BaseCamera2Fragment` 已通过 `OrientationLiveData` 监听传感器物理方向，但旧实现只记录日志，没有把计算后的
  相对旋转角传给 `CaptureRequest.JPEG_ORIENTATION`。
- `OrientationLiveData` 还会把 `ORIENTATION_UNKNOWN (-1)` 误判为正常竖屏，并遗漏 316°–359° 的正常竖屏区间，
  可能使首次方向值为空或错误。
- 第一阶段虽然通过 `CombinedCaptureResult.orientation` 计算了包含前置镜头水平镜像的 EXIF 方向，但
  `saveResult()` 仍直接写入相机返回的 JPEG 字节。该方向值没有参与最终文件生成，因此镜像信息实际被丢弃。

#### 修改方案

- 保留原有无参数 `takePhoto()` API，并新增接收 JPEG 旋转角的重载；基类拍照时传入
  `relativeOrientation.value`。方向值不可用时仍回退现有 `Display.rotation` 计算，避免影响直接使用 helper 的
  调用方。
- `CaptureRequest.JPEG_ORIENTATION` 使用拍照瞬间的物理方向结果，`CombinedCaptureResult.orientation` 同步使用
  同一请求中的方向计算，避免请求方向与返回元数据不一致。
- 切换前后摄像头成功后重新创建 `OrientationLiveData`，确保方向计算使用新镜头的传感器方向和镜像属性；删除
  demo 中会覆盖基类镜头切换监听器的重复设置。
- 忽略 `ORIENTATION_UNKNOWN`，并把 316°–359° 正确归入 `Surface.ROTATION_0`。
- 参考 CameraX 已通过真机验证的处理方式，普通 JPEG 保存时先解码为 Bitmap，再通过
  `decodeExifOrientation(result.orientation)` 应用“水平镜像 + 对应角度旋转”的矩阵，最后以 JPEG 质量 100
  重写文件。这样最终像素本身已完成方向和镜像归一化，不依赖相册是否正确解释 EXIF 镜像标记。
- Bitmap 转换在 `Dispatchers.IO` 中执行；无论成功或失败都会回收源 Bitmap 和转换结果，写入失败时删除不完整
  文件。`DEPTH_JPEG` 继续原样保存，避免重新编码破坏深度元数据。
- 照片修复本身不修改预览布局；Camera2Live 的 YUV/H.264 录像方向问题在下一节单独处理。

#### 复测要求

1. 后置摄像头分别以竖屏、物理方向 90°、270° 拍照，确认相册中的 JPEG 方向、画面左右关系正确且比例无变形。
2. 前置摄像头按相同三个方向拍照，确认最终照片与取景预期一致，方向及水平镜像均正确。
3. 前后摄像头连续切换后立即拍照，确认方向计算使用当前镜头且不崩溃。
4. 分别在系统自动旋转开启和关闭时重复横屏拍照，确认结果不依赖 Activity 是否旋转。
5. 回归 Camera2Live 预览和录像，确认本次照片修复没有改变取景框布局或 H.264 输出行为。

#### 真机验证结果（2026-08-17）

- 维护者确认 Camera2Live 照片真机测试通过。
- 前后摄像头在竖屏及 90°/270° 横屏方向下生成的照片方向与水平镜像正确，比例无变形。
- 镜头切换后的拍照、预览和录像回归正常；本节真机门禁已解除。

### 2026-08-17 Camera2Live 裸 H.264 横屏录像方向修复（已真机验证）

#### 问题现象与实测证据

- 使用 Camera2Live 后置摄像头在设备物理方向 90°/270° 录像时，最终 `camera.h264` 仍按竖屏显示，没有生成
  横屏画面。
- 对问题文件执行 `ffprobe`，结果为 `width=1080`、`height=1920`，且
  `sample_aspect_ratio=N/A`、`display_aspect_ratio=N/A`。这证明码流本身已编码为 9:16 竖屏，并非 `ffplay`
  的显示错误。
- 当前保存的是裸 H.264 elementary stream，不处理黑边，也不依赖 MP4/MOV 旋转矩阵。修复目标是横屏录像直接
  编码为横屏像素和 `1920×1080` SPS；播放器布局不属于本次范围。
- 第一轮后置动态方向修复经真机验证通过：后置横屏 90°/270°的视频方向及颜色均正确。继续测试发现前置横屏
  仍为竖屏，并且前置生成的视频无花屏但颜色错误。

#### 原因

- `EncoderStrategyYuv420P` 与 `EncoderStrategyYuv420Sp` 的后置分支都固定使用 `ROTATE_90`。相机输入通常为
  `1920×1080` 传感器方向帧，固定旋转后变成 `1080×1920`。
- `CameraAvcEncoder` 使用 `previewSize` 初始化宽高，而该尺寸由显示方向推导，没有使用录像开始时
  `OrientationLiveData` 提供的设备物理相对方向。
- 录像热路径调用 `doProcess(image, lensFacing, cameraSensorOrientation)`，没有携带本段录像锁定的旋转角。因此
  横屏 90°/270° 无法分别选择 0°/180° YUV 旋转。
- 第一轮为控制风险只对后置启用了动态旋转，前置仍固定按 270°处理并沿用旧编码尺寸，所以前置横屏继续输出
  竖屏码流。
- YUV420SP 前置旧分支从 `getYuvDataFromImage(..., COLOR_FORMAT_I420)` 取得 I420，却调用 `mirrorNv21()`、
  `rotateYUV420Degree270()` 或 `rotateYUVDegree270AndMirror()`。这些函数按交织 NV21 色度解释平面 I420 的 U/V
  数据，因而会偏色；画面长度仍符合 YUV420，所以可能不花屏。

#### 修改方案

- 点击录像按钮时读取一次 `relativeOrientation.value`，在创建编码器前传给
  `extraInitializeCameraForRecording()`。该角度在当前录像期间保持不变；录像中旋转设备只影响下一段录像。
- 保留原有 `extraInitializeCameraForRecording(bitrate)` 入口，新增带录像方向的重载，避免破坏已发布调用方。
  未提供角度时保留镜头相关竖屏默认值（后置 90°、前置 270°），非法角度立即拒绝。
- 动态方向应用到前后镜头：横屏两侧使用相机相对旋转 0°/180°并保持输入宽高，竖屏使用 90°/270°并交换
  宽高。该角度已由 `OrientationLiveData` 按当前镜头的 `SENSOR_ORIENTATION` 和前后置符号计算。
- 编码器尺寸直接根据 `selectedSizeFromCamera` 和锁定旋转角计算，不再把显示方向推导出的 `previewSize` 当作
  最终编码方向依据。0°/180°输出 `width×height`，90°/270°输出 `height×width`。
- 不修改公开的 `IDataProcessStrategy.doProcess()` 签名。录像旋转角通过策略构造参数传入，工厂保留旧入口并新增
  带角度的内部创建路径，降低 API/ABI 风险。
- `EncoderStrategyYuv420P` 与 `EncoderStrategyYuv420Sp` 通过融合的 `YuvUtil.transformI420()` 在一次 JNI 调用内
  完成锁定角度旋转、前置水平镜像以及 I420/NV12 输出；不再产生多次 JNI 往返和中间 Java `ByteArray`。0°、
  非镜像 I420 仍直接复用原数组。
- YUV420P 返回合法 I420，YUV420SP 直接返回 NV12，彻底移除 I420 数据进入 NV21 函数的颜色错误路径。融合
  JNI 通过 critical array access 避免显式 native 输入/输出副本，仅在 libyuv 必须多步变换时分配 native
  scratch buffer。
- 新增纯 Kotlin 单元测试覆盖 0°/180°保持宽高、90°/270°交换宽高、非法旋转角及非正尺寸拒绝；native YUV
  像素方向与颜色仍按下述真机矩阵验证。
- `CameraSurfaceView` 预览布局保持不变；`outputYuvForDebug=true` 仍表示输出转换前的原始 I420，不用于判断最终
  H.264 方向。

2026-08-18 的线程、MediaCodec、内存和 JPEG 策略整改详见
[`2026-08-18-camera-performance-follow-up-zh.md`](2026-08-18-camera-performance-follow-up-zh.md)。

#### 复测要求

1. 后置物理方向 90°、270°分别录像，`ffprobe` 均应得到横屏宽高（目标尺寸下为 `1920×1080`），且
   `ffplay` 画面方向正确、不倒置、不镜像、不拉伸。
2. 后置竖屏录像仍应得到 `1080×1920`，方向与现有正确行为一致。
3. 开始录像后旋转设备，当前码流的 SPS 宽高和方向保持不变；停止并重新录像后采用新的开始方向。
4. 前置竖屏、90°横屏、270°横屏分别验证：SPS 宽高、画面方向、水平镜像及颜色均正确，无花屏或拉伸。
5. 分别覆盖 YUV420P 与 YUV420SP 编码设备；确认颜色正常、无花屏，SPS 宽高与每帧数据布局一致。

#### 真机验证结果（2026-08-17）

- 维护者确认本轮 Camera2Live 录像真机测试通过。
- 后置横屏录像方向及颜色保持正确；前置横屏录像已按横屏显示，水平镜像及颜色正确，未出现花屏。
- 本次裸 H.264 方向与前置颜色修复已通过当前设备验证；不同传感器方向及 YUV 输出格式的跨设备覆盖继续纳入
  发布回归矩阵。

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
     某类传感器方向。当时先要求保留 `cameraSensorOrientation` 分支、两方向各自真机验证；后续真机确认旧路径
     本身方向和颜色错误后，该临时候选已由“录像相对角旋转 + 最终水平镜像”方案取代，详见第 6 节与更新后 spec。
  2. 原文声称"逐字节等价"——旧函数是 **NV21 色度函数处理 I420 数据**，不能宣称字节级等价，只能以真机
     画面为基线。
- **3a/M7、CHANGELOG、进度表**：注释重措辞、ktlint 换行、过期信息订正（3a→`b1ff02064`），并注明
  3b 已取代内置策略对缓存值的依赖；缓存字段已删除，公共接口参数留待下一次破坏性版本删除。

### 3b 旧实现保留策略（已明确）

SP2 的亮度分析代码在开关启用时仍是有效的 opt-in 路径，不属于失效代码；3b 完成 native 替换后，旧前置
Kotlin 分支则不再参与任何执行路径，两者性质不同。3b 按 spec 现有方案处理：旧实现通过 Git 历史保留，不在
源码中留下整块失效注释，并同步清理不再使用的 import，以满足 detekt 零容忍要求。

### `32d3af9a2` —— 相机性能整改（7 项 finding，已复核通过）

对 Camera2Live/CameraX 的 7 项性能 finding 整改提交，经 cpp-reviewer（native `transformI420`）与 kotlin-reviewer
（全部 Kotlin 改动）并行专家审查后**通过**，无 CRITICAL/HIGH。逐条结论：

1. 录像帧改用 `imageReaderHandler`（与 `cameraHandler` 确认为**不同** HandlerThread，非别名），`image.close()`
   顺序不变。
2. `CameraAvcEncoder` 空队列时先 `poll()?:return`，input buffer id 留在池，不再提交空帧、PTS 只随真实帧推进。
3. 旋转+镜像+I420/NV12 合并为单次 `YuvUtil.transformI420()`；back-lens/P 走 identity 快路径**字节不变**；参数序
   与 native 签名 `([BIIIZI)[B` 一致。
4. `Bitmap.toBytes()` 单次分配 + `ByteBuffer.wrap()`；拍照路径提前回收中间 Bitmap，`recycledSafety()` 防重复回收，
   异常路径无泄漏/无 double-free。
5. 复制后立即 `releaseOutputBuffer`（finally 内），编码字节回调改由专用串行线程投递。
6. CameraX 诊断默认关闭，codec 枚举移到 `Dispatchers.Default` 并按 `cameraId:rotation` 缓存（缓存加
   `synchronized`）；顺带修好 HEVC 误查 AVC 的旧 bug。
7. 新增 `JpegOutputStrategy` 枚举，两端默认 `PIXEL_NORMALIZED`（**不改现有设备验证行为**），`EXIF_ONLY` 只写方向
   元数据，为 opt-in。

- **native 侧**：融合 JNI 与旧三步链逐字节等价，内存安全（临界区正确嵌套/释放、RAII scratch、长度校验、64 位溢出
  防护）。遗留 1 处 **LOW**（嵌套临界区加不变量注释），留待真机回归时一并观察。
- **Kotlin 侧**：审查发现 1 处 **MEDIUM**——`drainInputBuffers` 异常路径（`getInputBuffer` 返回 null/抛异常，或
  帧尺寸超过 `inputBuffer.remaining()`）会孤立已弹出的 input buffer id，systematic 命中会逐步耗尽输入池、静默卡死
  编码器。该项已在 `af35be538` 修复（见下）。

### `af35be538` —— input buffer 泄漏修复（MEDIUM，已复核通过）

针对上条 MEDIUM 的修复提交，人工复核**通过**，含单测，并附带解决一处 LOW（`offerDataIntoQueue` 与已 release
encoder 的竞态）：

- **核心修复**：新抽出的 `PendingInputBuffers.drain()` 改为 **peek-then-remove**——`bufferIds.first()` 取而不弹，
  `submit` 成功后才 `removeFirst()`；失败则 `onFailure` 后 `return`，buffer id 保留在池供下一帧复用。从根上消除
  异常路径的 id 孤立。
- **失败分级**：超大帧（`IllegalArgumentException`）只丢该帧、保留 id、不停编码器；真编码器故障
  （`CodecException`/`IllegalStateException`）及 `onError` 触发 `stopAcceptingFrames()`（清队列+清池+永久停收）。
- **生命周期硬化**：`acceptingFrames`/`stopped`/`released` 三个 `AtomicBoolean`；`offerDataIntoQueue`/
  `onInputBufferAvailable` 锁内双检，`stop()`/`release()` 用 `compareAndSet` 幂等，teardown 后迟到帧被干净拒绝。
- **并发**：`drain` 全程在 `inputBufferLock` 内，`onFailure` 的 `stopAcceptingFrames()` 走可重入 `synchronized`
  无死锁；catch 后立即 `return`，`clear()` 不与迭代冲突。旧字段 `availableInputBufferIds` 已全仓无残留引用。
- **单测**：新增 `PendingInputBuffersTest` 覆盖「超大帧保留 id 且后续合法帧复用消费」「正常提交消费 id」。
- **文档**：英文后续文档 `2026-08-18-camera-performance-follow-up.md` 经维护者确认删除（该文档不需要英文版），
  保留 ZH 版并同步更新 change #2 措辞与验证项。

> 真机回归仍待项（无对应硬件/设备）：前置 `SENSOR_ORIENTATION`=90 组合、录像方向/颜色、不同厂商 MediaCodec 行为、
> 目标相册对 `EXIF_ONLY` 的渲染。
