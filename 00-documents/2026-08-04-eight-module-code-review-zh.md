# 八模块代码审查复核报告（2026-08-04）

> **状态更新（2026-08-06）**：本报告记录的是审查基线问题，不是当前分支的开放问题列表。
> `fix/eight-module-remediation` 已完成整改计划中的全部 26 项 P2；实现状态与验证结果见
> [整改实现计划](./2026-08-04-remediation-impl-plan-zh.md) 和
> [整改实现细节](./2026-08-04-remediation-impl-details-zh.md)。

## 1. 审查范围与基线

本报告复核以下 8 个模块：

`android-restricted`、`audio`、`androidbase`、`camera2live`、`camerax`、`circle-progressbar`、`http`、`lib-bytes`

复核基线：

- 分支：`master`
- Commit：`d24931c9828f54803becf78f2aa9cb5b6d5bf733`
- 远端主线：`origin/master`
- 工作树说明：本报告在上述 commit 的生产代码基础上复核；审查文档本身为待提交变更。

本次复核以当前代码、调用点、Android Framework 源码和实际 Gradle 执行结果为依据。此前各 agent 未保存的原始输出不再作为可独立验证的证据，因此本文不沿用原报告的 `5 CRITICAL / 34 HIGH / 39 MEDIUM / 21 LOW` 汇总数字，只保留能够从当前仓库重新定位的问题。配套整改计划现拆分为 72 条已确认整改项和 2 条实施前决策项（CX-5、LB-3）。

严重级别定义：

- **CRITICAL**：可直接导致任意文件写入、稳定复现的核心功能崩溃/永久阻塞，或高影响安全事件。
- **HIGH**：主要路径存在资源泄漏、竞态、生命周期错误、敏感信息暴露或明显功能失效。
- **MEDIUM**：需要特定输入或时序才触发，影响可恢复，或属于应修复的 API/性能/健壮性问题。
- **LOW**：维护性、日志规范、死代码、测试缺口或低概率风险。

---

## 2. 原报告纠正项

### 2.1 删除：`CameraAvcEncoder.setCallback()` 因无 Looper 导致录制必崩

原结论不成立。`CameraAvcEncoder` 虽然在 `Dispatchers.IO` 中创建，但 `MediaCodec` 创建线程没有 Looper 时会回退到主 Looper；`setCallback(callback)` 等价于传入空 Handler，并不会仅因调用线程无 Looper 就抛出“Callback set without a valid looper”。

当前代码仍有一个 **MEDIUM** 级 API 使用问题：`setCallback()` 位于 `configure()` 之后，而 Android API 文档建议在 `configure()` 前设置异步 callback。建议调整调用顺序；若希望回调线程固定且不占用主线程，可为编码器显式传入专用 Handler。

### 2.2 删除：`LeoTextureView.onOutputFormatChanged()` 直接修改布局导致线程异常

原结论不成立。`onOutputFormatChanged()` 只更新 `outputFormat` 并调用可选的 `videoOutputFormatChangeEvent`；真正修改 `layoutParams` 的 `updateDimension()` 当前没有调用点。因此不能把现有实现描述为必然发生的 `CalledFromWrongThreadException`。

`LeoTextureView` 与 `CameraAvcEncoder` 一样，应把 `MediaCodec.setCallback()` 移到 `configure()` 前；显式指定 Handler 可进一步明确线程契约。

### 2.3 降级：`CameraDevice.onDisconnected()`

问题成立，但不是所有断连都会使打开相机的协程永久挂起。只有在 `onOpened()` 前发生断连且 continuation 仍 active 时才会挂起；在 `onOpened()` 后断连，主要风险是设备未关闭和相机状态失效。

该问题调整为 **HIGH**：始终调用 `device.close()`，仅在 `cont.isActive` 时 `resumeWithException()`，并为 continuation 取消注册资源清理逻辑。

### 2.4 修正：HTTP 日志“宿主无法关闭”

HTTP 拦截器确实硬编码为 `BODY`，且会输出完整 Header、请求体和响应体；但宿主仍可通过全局 `LogContext` 使用 `enableLog=false` 的日志实现关闭所有日志。准确表述应为：**宿主无法单独配置或关闭 `BaseHttpRequest` 内部的 HTTP 日志级别**。

修复应包括：默认 `NONE`、公开日志级别配置、按名称脱敏 `Authorization`/`Cookie`/`Set-Cookie` 等敏感 Header，并限制可记录的 Body 大小。无需把所有 Header 值一律删除。

### 2.5 待契约确认：`ByteArray.toAsciiString()`

`Byte.toInt().toChar()` 对负数字节会发生符号扩展，但 `0x80..0xFF` 本身不属于 ASCII。简单增加 `and 0xFF` 得到的是类似 ISO-8859-1 的单字节字符映射，而不是严格 ASCII。

该项不再列为 HIGH。应先确定 API 需要“严格 ASCII”“ISO-8859-1”还是“原始无符号字节到 Unicode code point”的哪种语义，再据此实现并补充测试。

### 2.6 降级：`BluetoothUtil.setPin()` 的非局部 return

成功和失败路径都能返回正确结果；末尾 `return true` 实际不可达，属于 **LOW** 级可读性和死代码问题，不是功能缺陷。可直接改写为 `runCatching { ... }.getOrElse { ... }`。

---

## 3. 确认的 CRITICAL 问题

### C1. `ZipUtil.unzip()` Zip Slip 路径穿越

位置：`androidbase/src/main/kotlin/com/leovp/androidbase/utils/cipher/ZipUtil.kt:181`

ZIP 条目名未经目标目录边界校验就传给 `File(destFile, entry.name)`。攻击者可通过 `../` 或绝对路径尝试写出目标目录，覆盖应用可写范围内的文件。

修复要求：

- 先规范化目标目录和条目路径，再确认条目位于目标目录内。
- 在创建目录或文件前完成校验。
- 增加 `../`、绝对路径和既有符号链接父目录的回归测试。
- 同时增加可配置的单文件和总解压大小上限，超限或写入失败时不得遗留当前部分文件；保留原有两参数 JVM API。

### C2. `takePhoto()` 泄漏 `Image` 且恢复 continuation 后不退出循环

位置：`camera2live/src/main/kotlin/com/leovp/camera2live/Camera2ComponentHelper.kt:874-1001`

已确认的问题包括：

- flush 循环取得的 `Image` 没有关闭。
- 时间戳不匹配的 `continue` 分支没有关闭当前 `Image`。
- `cont.resume(...)` 后 `while (true)` 没有退出；监听器已移除后，协程会继续阻塞在 `imageQueue.take()`。
- `imageQueue.add(image)` 在队列已满时会抛异常，且当前 image 不会关闭。
- 超时、相机回调和协程取消之间缺少单次完成保护。

这些问题会耗尽 `ImageReader(maxImages=3)`、泄漏相机缓冲区，并导致拍照路径卡死或回调线程异常。

修复要求：使用 `use/finally` 保证关闭；使用 `Channel.receive()`/`withTimeout()` 等可取消等待，不能在协程中继续调用 `BlockingQueue.take()`；成功后立即退出；丢帧和取消时关闭未消费的 image；统一管理 listener、capture callback、timeout 和取消清理。

---

## 4. 确认的 HIGH 问题

### 4.1 `android-restricted`

- `DisplayCutoutManager` 使用进程级 `SingletonHolder` 持有首个 Activity，导致 Activity 泄漏，并可能在后续页面操作已销毁的 Window。应改为每个 Activity 独立实例。
- `DisplayCutoutManager.kt:78` 将 vivo 设备错误路由到 `HuaweiDisplayCutout`，导致 vivo 实现不可达。
- `ApplicationManager.application` 在 object 初始化时立即反射，调用 `init(context)` 前已经执行；显式初始化路径失去意义。应改为延迟读取，并让 `init()` 优先使用传入的 application context。
- `ApplicationManager.getApplicationByReflect()` 位于静态初始化链且没有失败隔离，反射失败会导致 object 初始化失败。
- `VivoDisplayCutout` 使用 `27 * densityDpi`、`100 * densityDpi` 计算 dp，结果远大于真实像素。应使用 `density` 或标准 dp 转 px API。

### 4.2 `audio`

- `BaseMediaCodec.release()` 先释放 codec、后取消 scope；后台协程可能继续访问已释放的 codec。整改时不能反向引入 UI 线程无界 `runBlocking` 或 job self-join，应提供明确的 suspend 释放路径及兼容入口。
- `AacDecoder.onInputData()` 使用阻塞式 `queue.take()`，协程取消不能可靠唤醒该调用，释放流程无法等待任务退出。
- `MicRecorder.stopRecord()` 取消后没有等待录音任务结束，`AudioRecord.read()` 与 `stop()/release()` 存在竞态。
- `AacStreamPlayer`、`OpusStreamPlayer` 只在初始化阶段同步，decode、flush 和 stop 可并发读写 `audioDecoder`/CSD 字段，存在 TOCTOU 空指针和已释放对象访问。
- 解码器初始化失败时 CSD 字段不会回滚，后续数据会进入错误状态。
- `MicRecorder` 每次短读后把工作缓冲区永久替换为更短数组，后续吞吐持续下降。
- `AudioRecord.read()` 的负错误码直接传给 `copyOfRange()`，异常被吞后录音协程静默结束，调用方收不到失败通知。
- `BaseMediaCodecSynchronous.process()` 捕获异常后继续返回 `true`，codec 持续抛错时会形成高 CPU 忙循环。

### 4.3 `camera2live`

- `CameraDevice.onDisconnected()` 不关闭设备，也不在打开阶段恢复 continuation 异常。
- `switchCamera()` 关闭旧 camera 后立即打开新 camera，没有等待 `onClosed()`，可能触发 `ERROR_CAMERA_IN_USE`。
- `openCamera()` 的同步 `SecurityException`/`CameraAccessException` 未纳入协程异常处理；运行时撤权或 camera service 异常会使主线程 scope 失败。
- `getJpegOrientation()` 在 `display == null` 时得到 `-1`，随后 `ORIENTATIONS.getValue(-1)` 抛异常。
- Fragment 每次重建 View 都创建新的 helper 和 HandlerThread，但只在 `onDestroy()` 释放最后一个 helper；`singleExecutor` 即使在最终释放时也没有 shutdown。
- `onStop()` 可能在主线程调用 `stopRecording()`，其中包含同步 `sleep(100)` 和资源关闭，存在卡顿及 ANR 风险。
- `takePhoto()`/`createCaptureSession()` 使用不可取消的 `suspendCoroutine`；拍照成功恢复后不退出循环，见 C2。

### 4.4 `camerax`

- `SoundManager` 进程级单例持有 Fragment 的 Context。应保存 `applicationContext`，并明确 release 后是否允许重新加载。
- `captureForBytes()` 没有在 `finally` 中关闭 `ImageProxy`；在读取 plane 或播放快门声时抛异常会泄漏 image。其 callback 运行在 cameraExecutor，修复时 View 动画和结果通知还必须切回主线程。
- 多个依赖 ViewBinding 的任务使用 Fragment `lifecycleScope`，在 `onDestroyView()` 后仍可能访问旧 View。
- `BaseCameraXFragment.binding` 为永久 `lateinit` 引用，`onDestroyView()` 没有清理；View 重建时存在旧 View 泄漏窗口。该属性当前为公开 API，整改不能直接降级成 `protected val`，需先做 API/ABI 兼容设计。
- `CameraFragment.bindToLifecycle(this, ...)` 使 camera use cases 生命周期长于 Fragment View；应根据组件设计评估改为 `viewLifecycleOwner`。

### 4.5 `circle-progressbar`

- 无限 `ValueAnimator` 没有在 `onDetachedFromWindow()` 取消；从 indeterminate 切换到 idle/finish/error 时也没有结束，可能持续持有 View 并刷新。
- `wrap_content` 没有稳定的建议尺寸或 `onMeasure()` 实现，在无背景最小尺寸时可能测量为 `0 x 0`，控件不可见。

### 4.6 `http`

- `BaseHttpRequest` 默认以 `BODY` 记录完整请求/响应和敏感 Header；风险和修复边界见 2.4。
- `HttpRequest` 单例共享并修改同一个 `Retrofit.Builder`；并发调用不同 `baseUrl` 时可能构建出错误 Host。
- `HttpLoggingInterceptor` 的 `source.request(Long.MAX_VALUE)` 会把完整响应读入内存；请求侧也会通过 `requestBody.writeTo(Buffer)` 完整缓冲。BODY 日志必须双向限制，且不能在请求限制字节后又读取整个已缓冲 Buffer。
- `BaseProgressObserver` 保存 `Disposable` 但不暴露取消入口，调用方无法通过 observer 在页面销毁时终止订阅。

### 4.7 `androidbase`

- `KeepAliveReceiver.onReceive()` 使用 `GlobalScope.launch`，没有 `goAsync()` 或可追踪的生命周期；进程回收时事件可能丢失。

---

## 5. 确认的 MEDIUM / LOW 问题

### 5.1 跨模块

- `WifiUtil`、`DeviceUtil` 等 Context 单例应保存 `applicationContext`。
- `audio`、`androidbase`、`android-restricted`、`camera2live` 中仍存在 `printStackTrace()`；这些模块已有 `log` 依赖，应统一使用 `LogContext` 并传入 throwable。
- 多处使用 `!!`，违反 `.claude/rules/kotlin/coding-style.md` 的仓库补充规则。替换时应优先消除非法状态，而不是只把 `!!` 机械改成同样会抛异常的 `checkNotNull()`。
- 宽泛 `runCatching`/`catch (Exception)` 需要检查是否吞掉 `CancellationException`；协程边界应继续抛出取消异常。

### 5.2 `android-restricted`

- `DeviceProp.getSystemPropertyByStream()` 的 `FileInputStream` 未关闭，应使用 `use`。
- `DeviceProp.getSystemPropertyByShell()` 把公开参数直接拼入真实 shell 命令；应对白名单格式进行验证，避免命令注入。

### 5.3 `androidbase`

- `Watermark` 在空文本、零字号或异常间距下可能计算出 `step=0` 并抛异常；配置值需要约束。
- AES/PBKDF2 路径会把 key 转成不可清零的 hex `String`，且 `PBEKeySpec` 未调用 `clearPassword()`；应尽量缩短不可清零副本的生命周期。
- ZIP/GZIP 解压没有输出大小上限，存在压缩炸弹导致内存或磁盘耗尽的风险。
- `GZipUtil.isGzip()` 对少于 2 字节的输入没有长度检查。
- `BluetoothUtil.setPin()` 存在不可达的末尾 return，见 2.6。

### 5.4 `camera2live`

- `CameraAvcEncoder.queue` 是无界队列，生产速度超过编码速度时内存会持续增长。应使用有界队列并定义丢帧策略。
- 两处 MediaCodec callback 应调整到 `configure()` 前，并明确使用主线程、camera Handler 或独立 codec Handler 的线程策略。

### 5.5 `circle-progressbar`

- `maxProgress` 允许设置为 0 或负数，`getDegrees()` 会产生 `NaN`/异常比例；setter 和 XML 属性都应限制为正数。
- indeterminate 状态每帧重复调用 `Drawable.setTint()`，可能产生不必要的 ColorFilter 更新；应在颜色变化时设置。
- 监听器列表在回调期间被修改会触发并发修改异常，可使用快照遍历。

### 5.6 `lib-bytes`

- `ByteBuffer.copy()`/`copyAll()` 使用默认大端 `ByteBuffer.allocate()`，没有继承源 buffer 的 `order()`；复制小端 buffer 后，多字节读取语义发生变化。应设置 `dst.order(order())` 并补充大小端测试。
- `toShortArray()`/`toShortArrayLE()` 对奇数字节长度静默丢弃最后一个字节；应明确抛错、补齐还是允许截断，并用文档和测试固定契约。
- `toAsciiString()` 需要先确认编码契约，见 2.5。

---

## 6. 测试与验证结果

已执行：

```bash
./gradlew :android-restricted:testDebugUnitTest \
  :audio:testDebugUnitTest \
  :androidbase:testDebugUnitTest \
  :camera2live:testDebugUnitTest \
  :camerax:testDebugUnitTest \
  :circle-progressbar:testDebugUnitTest \
  :http:testDebugUnitTest \
  :lib-bytes:testDebugUnitTest \
  --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 56s
425 actionable tasks: 425 executed
```

实际执行测试的模块：`androidbase`、`http`、`lib-bytes`。

以下模块的 `testDebugUnitTest` 为 `NO-SOURCE`：

- `android-restricted`
- `audio`
- `camera2live`
- `camerax`
- `circle-progressbar`

测试通过只能证明现有测试未失败，不能否定上述 Camera、Audio、生命周期和并发问题。当前还缺少：

- Zip Slip、ZIP bomb 和 GZIP 边界测试。
- Camera2 Image 关闭、超时/取消单次恢复和切换镜头状态测试。
- Audio codec/recorder 停止与并发释放测试。
- CameraX View 重建和 ImageProxy 异常关闭测试。
- CircleProgressbar detach、状态切换和非法进度测试。
- HTTP 日志脱敏、日志级别和大响应上限测试。
- ByteBuffer 大小端复制、奇数字节和 ASCII 契约测试。

### 6.1 实施约束与决策门槛

- **CX-5**：`CameraFragment.bindToLifecycle(this, ...)` 是否是为了让 use case 跨 View 重建存活，当前代码无法证明产品意图。确认前保持现状，只补生命周期验证。
- **LB-3**：`toAsciiString()` 的严格 ASCII、ISO-8859-1 或其它逐字节映射契约必须由调用方确认，确认前不修改生产实现。
- Camera2 Image 生命周期、超时/取消、单次完成和线程切换不能只靠抽纯函数测试；至少需要可控 fake 并发测试，并补 Robolectric、instrumented 或真机验证。
- Audio 停止流程必须覆盖主线程调用、后台调用、callback 内请求停止、重复停止和释放超时。
- 本仓库为已发布 Android library；公开属性可见性、函数签名、JVM overload 和同步/异步语义变化都要执行 API/ABI 对比并提供迁移路径。

---

## 7. 建议整改顺序

1. **P0**：Zip Slip；Camera2 `Image`/continuation 生命周期；HTTP 默认 BODY、敏感字段和无限响应读取。
2. **P1**：Camera2 断连/切换/线程释放；Audio 阻塞取消和 codec/recorder 释放竞态；CameraX View 生命周期；Activity/Context 单例。
3. **P2**：ApplicationManager、Vivo 刘海、Retrofit Builder 并发、CircleProgressbar animator、ByteBuffer 字节序、命令注入输入校验。
4. **P3**：日志规范、`!!`、死代码、低风险性能优化和剩余测试补齐。解压上限、密钥副本清理等安全/功能项应在 P0-P2 完成。

安全项修复后，应再次进行 security review；Camera 和 Audio 修复后，应在 API 21、API 26、API 29、API 33+ 的代表设备或模拟器上执行生命周期、权限撤销、后台恢复和并发操作验证。
