# 八模块整改实现细节（2026-08-04）

本文件是《[整改实现计划](./2026-08-04-remediation-impl-plan-zh.md)》的配套细节：每条给出 `文件:行`、根因、现状→目标代码、兼容/边界、测试、回归风险。基线 `master@d24931c9`。ID 与计划文档一致，其中 72 条为已确认整改项，CX-5 与 LB-3 为实施前必须确认的决策项。

> 通用说明：无 `src/test` 的模块（camera2live、camerax、circle-progressbar、android-restricted、audio）需先新建测试源集；框架/硬件强耦合逻辑建议抽纯函数后单测。含 `log` 依赖的模块用 `LogContext`，`lib-bytes` 用 `require`/`android.util.Log`（不得引入 log）。跨模块统一模式见计划文档 §3（T1–T8）。

> **目标代码为实现骨架，不能脱离条目约束直接粘贴**：其中 `getRetrofitBuilder` 是当前源码已有函数；`buildCombinedCaptureResult`、`initializeCameraAndAwait`、`releaseCodecOnce`、`finishRecorderRelease`、`decodeRotateAndBuild`、`notifyCodecFailure` 是文档为拆分复杂流程而提出的新 helper。它们不是留给实现者自由发挥的占位符：返回类型、线程、资源所有权、异常和幂等语义必须分别遵循 CAM2-1/3、AUD-1/3/8、CX-2 的条目要求，并保留现有 EXIF、旋转和时间戳计算。若落地时不抽 helper，也必须在原函数中实现完全相同的约束。

| Helper | 最小契约 |
|---|---|
| `buildCombinedCaptureResult(Image, TotalCaptureResult): CombinedCaptureResult` | 在调用方的 `Image.use` 结束前复制完整字节，保留现有 EXIF/镜像/尺寸计算；不持有或关闭外部 Image |
| `initializeCameraAndAwait(width, height)` | suspend；仅在 camera、session 和 repeating request 都就绪后返回，失败/取消时抛出原始异常并清理本次部分状态 |
| `releaseCodecOnce()` | 幂等；只负责 stop/release codec 和更新释放状态，不启动 fire-and-forget 任务 |
| `finishRecorderRelease(stopSucceeded)` | 幂等；释放 AudioRecord/encoder，并且只调用一次 `callback.onStop(finalResult)` |
| `decodeRotateAndBuild(bytes): CaptureImage.ImageBytes` | 在 `Dispatchers.Default` 执行，保留现有镜像/旋转/回收逻辑；失败向上传播，不访问 View |
| `notifyCodecFailure(CodecException)` | 向 owner 报告异常和 recoverable/transient 信息；不在原 codec 上直接忙重试 |

---

## 1. camera2live

### CAM2-1 [CRITICAL/P0] takePhoto() Image 泄漏 + continuation 恢复后不退出循环
- 位置：`camera2live/src/main/kotlin/com/leovp/camera2live/Camera2ComponentHelper.kt:870-1007`
- 根因：(1) flush 循环 `while(acquireNextImage()!=null){}` 取到的 Image 不 close；(2) 时间戳不匹配 `continue` 分支不 close；(3) `cont.resume()` 后 `while(true)` 不退出，监听器已移除后永久阻塞在 `imageQueue.take()`（线程泄漏）；(4) `imageQueue.add(image)` 队列满抛异常且不 close；(5) 不可取消 `suspendCoroutine`，超时/回调/取消三者可能重复 resume。
- 现状代码：
```kotlin
suspend fun takePhoto(): CombinedCaptureResult = suspendCoroutine { cont ->
    if (!::imageReader.isInitialized) error("initializeCamera must be called first")
    @Suppress("ControlFlowWithEmptyBody")
    while (imageReader.acquireNextImage() != null) {}
    val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
    imageReader.setOnImageAvailableListener({ reader ->
        val image = reader.acquireNextImage()
        imageQueue.add(image)
    }, imageReaderHandler)
    // ...
    context.lifecycleScope.launch(cont.context) {
        while (true) {
            val image = imageQueue.take()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                image.format != ImageFormat.DEPTH_JPEG && image.timestamp != resultTimestamp) {
                continue
            }
            cont.resume(CombinedCaptureResult(...))
            // There is no need to break out of the loop, this coroutine will suspend
        }
    }
}
```
- 目标代码：不要在协程中使用 `BlockingQueue.take()`；改用可取消的 `Channel.receive()`，由 `withTimeout` 统一约束 capture result 与匹配 image 的等待时间。以下代码展示资源所有权和取消模型，实际落地时保留现有 EXIF 计算：
```kotlin
suspend fun takePhoto(): CombinedCaptureResult = coroutineScope {
    if (!::imageReader.isInitialized) error("initializeCamera must be called first")
    while (true) {
        imageReader.acquireNextImage()?.close() ?: break
    }
    val images = Channel<Image>(
        capacity = IMAGE_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = Image::close,
    )
    imageReader.setOnImageAvailableListener({ reader ->
        val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
        if (images.trySend(image).isFailure) image.close()
    }, imageReaderHandler)

    try {
        withTimeout(IMAGE_CAPTURE_TIMEOUT_MILLIS) {
            val result = suspendCancellableCoroutine<TotalCaptureResult> { cont ->
                val callback = object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure,
                    ) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("Still capture failed: $failure")
                            )
                        }
                    }
                }
                try {
                    session.capture(captureRequest, callback, cameraHandler)
                } catch (error: Exception) {
                    if (cont.isActive) cont.resumeWithException(error)
                }
            }

            val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
            while (true) {
                val combined = images.receive().use { image ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        image.format != ImageFormat.DEPTH_JPEG &&
                        image.timestamp != resultTimestamp
                    ) {
                        return@use null
                    }
                    buildCombinedCaptureResult(image, result)
                }
                if (combined != null) return@withTimeout combined
            }
        }
    } finally {
        imageReader.setOnImageAvailableListener(null, null)
        images.cancel() // onUndeliveredElement closes every buffered Image.
    }
}
```
- 兼容/边界：minSdk 21；`Channel.receive()` 与 `withTimeout()` 都可协作取消，`onUndeliveredElement` 负责关闭未消费的 Image。不能再引入 `take()`、裸线程或依赖调用方 Dispatcher 的阻塞等待。
- 测试：纯函数单测覆盖时间戳匹配规则；使用 fake closeable 验证丢帧和取消清理；ImageReader/CameraCaptureSession 的 close、超时、取消和单次完成语义必须补 Robolectric 或 instrumented 测试，不能只靠抽纯函数证明。
- 回归风险：中。改 cancellable 后离开页面会触发取消清理；回归“连拍/快速切相机时拍照”。

### CAM2-2 [HIGH/P1] onDisconnected 不 close 设备且打开阶段不 resume
- 位置：`Camera2ComponentHelper.kt:584-626`（595-599）
- 现状：`onDisconnected` 仅打日志（TODO）。目标：
```kotlin
override fun onOpened(device: CameraDevice) { if (cont.isActive) cont.resume(device) }
override fun onDisconnected(device: CameraDevice) {
    device.close()
    if (cont.isActive) cont.resumeWithException(IllegalStateException("Camera $cameraId disconnected during open"))
}
```
- 兼容/边界：运行后断连时 `cont` 已 resume，`isActive=false` 不二次 resume；`device.close()` 幂等。测试：抽 StateCallback 策略单测状态迁移。回归：断连由静默变抛异常，需 CAM2-4 的 launch 异常处理兜底。

### CAM2-3 [HIGH/P1] switchCamera 不等 onClosed 即开新相机
- 位置：`:1099-1110`
- 目标：关闭信号必须和具体 `CameraDevice` 绑定，并通过 `Mutex` 串行化切换。连续点击时取消尚未开始的旧请求；关闭超时必须报告失败并终止本次切换，不能继续打开新设备：
```kotlin
private data class OpenedCamera(
    val device: CameraDevice,
    val closed: CompletableDeferred<Unit>,
)

private val switchMutex = Mutex()
private var openedCamera: OpenedCamera? = null
private var switchJob: Job? = null

// openCamera() creates a close signal owned by this exact device.

fun switchCamera(lensFacing: Int) {
    if (!::camera.isInitialized) throw IllegalAccessError("You must initialize camera first.")
    switchJob?.cancel()
    switchJob = context.lifecycleScope.launch(Dispatchers.Main.immediate) {
        switchMutex.withLock {
            val oldCamera = checkNotNull(openedCamera)
            oldCamera.device.close()
            val closed = withTimeoutOrNull(CAMERA_CLOSE_TIMEOUT_MILLIS) {
                oldCamera.closed.await()
                true
            } ?: false
            if (!closed) {
                cameraErrorListener?.onError(TimeoutException("Camera close timed out"))
                return@withLock
            }
            this@Camera2ComponentHelper.lensFacing = lensFacing
            initializeCameraAndAwait(previewWidth, previewHeight)
            lensSwitchListener?.onSwitch(lensFacing) // Notify only after success.
        }
    }
}
```
- 兼容/边界：不能用一个全局 Deferred 接收所有设备的 `onClosed()`，否则旧设备回调会完成新请求。回归：switchCamera 由同步变异步，覆盖按钮防抖、连续三次切换、关闭超时和初始化失败。

### CAM2-4 [HIGH/P2] openCamera/switchCamera 未捕获 Security/CameraAccessException
- 位置：`:554-626,1099`
- 目标：`manager.openCamera` 包 try/catch 转 continuation 异常；`initializeCamera` 加 `CoroutineExceptionHandler` + `catch(CancellationException){throw it}` 后转错误回调：
```kotlin
try { manager.openCamera(cameraId, stateCallback, handler) }
catch (e: SecurityException) { if (cont.isActive) cont.resumeWithException(e) }
catch (e: CameraAccessException) { if (cont.isActive) cont.resumeWithException(e) }
// initializeCamera = launch(Dispatchers.Main + cameraExceptionHandler) {
//   try {...} catch (e: CancellationException) { throw e } catch (e: Exception) { cameraErrorListener?.onError(e) } }
```
- 兼容/边界：先 rethrow CancellationException（T8）；建议新增 `cameraErrorListener` 上抛 UI。回归：低。

### CAM2-5 [HIGH/P2] getJpegOrientation display=-1 → getValue(-1) 抛异常
- 位置：`:850-863`
- 目标：
```kotlin
val deviceRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    context.display?.rotation ?: Surface.ROTATION_0
    else @Suppress("DEPRECATION") context.windowManager.defaultDisplay?.rotation ?: Surface.ROTATION_0
val cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
val deviceOrientationDegrees = ORIENTATIONS[deviceRotation] ?: 90
val jpegOrientation = (deviceOrientationDegrees + cameraSensorOrientation + 270) % 360
```
- 兼容/边界：`ORIENTATIONS[key] ?: 90`、`SENSOR_ORIENTATION ?: 0` 消除 `!!`。测试：抽 `computeJpegOrientation(rotation, sensor?)` 纯函数。回归：极低。

### CAM2-6 [HIGH/P1] Fragment 视图重建致 HandlerThread/singleExecutor 泄漏
- 位置：`view/BaseCamera2Fragment.kt`（onViewCreated 建 helper、release 仅在 onDestroy）+ helper `:93,419-447,1197-1215`
- 目标：`stopCameraThread()` 内补 `singleExecutor.shutdown()`；helper 生命周期对齐视图：
```kotlin
override fun onDestroyView() {
    super.onDestroyView()
    if (::camera2Helper.isInitialized) camera2Helper.release()   // closeCamera()+stopCameraThread()
    _binding = null
}
// stopCameraThread(): append singleExecutor.shutdown().
```
- 兼容/边界：helper 绑到视图生命周期。回归：中，改变复用语义，回归“录制停止后重新预览 / 返回栈 / 旋屏”。

### CAM2-7 [HIGH/P1] onStop 主线程 sleep(100)+关相机/编码器 → ANR
- 位置：`view/BaseCamera2Fragment.kt:203-211` → `stopRecording/stopRepeating(含 sleep(100))/closeCamera`
- 目标：首先移除固定 `sleep(100)`，用 Camera API 的回调和状态串行化关闭流程。不要把 Camera/MediaCodec 释放简单搬到通用 `Dispatchers.IO` 后与 `onDestroyView()` 并发执行：
```kotlin
private fun stopRepeating() {
    if (!::session.isInitialized) return
    runCatching {
        session.stopRepeating()
        session.abortCaptures()
    }.onFailure { LogContext.log.w(TAG, "stopRepeating failed", it) }
}
```
- 兼容/边界：若 codec stop 仍需异步执行，应使用 helper 自有的串行 teardown scope/dispatcher，并提供完成信号；`onStop()` 与 `onDestroyView()` 共用同一幂等状态机。回归：中，覆盖前后台快速切换、录制中切后台和销毁期间重复释放。

### CAM2-8 [HIGH/P1] takePhoto/createCaptureSession 用不可取消 suspendCoroutine
- 位置：`:632-658`（createCaptureSession）、`:870`（takePhoto）
- 目标：改 `suspendCancellableCoroutine`，所有 resume 加 `if (cont.isActive)`，`invokeOnCancellation` 清理；takePhoto 见 CAM2-1。回归：中，回归“建会话/拍照进行中退出 Fragment”。
- 说明：本条仅要求把 `createCaptureSession`（`:632-658`）与 `takePhoto`（`:870`）从不可取消挂起改为可取消实现。`openCamera`（`:585-589`）已经使用 `suspendCancellableCoroutine`，CAM2-8 无需替换它的挂起原语；但仍必须按 CAM2-2/CAM2-4 修复断连关闭、同步异常和取消清理，不能理解成 openCamera 整体无需修改。

### CAM2-9 [MEDIUM/P2] CameraAvcEncoder.setCallback 应在 configure 前 + 专用 Handler
- 位置：`codec/CameraAvcEncoder.kt:193-199`
- 目标：
```kotlin
private var codecThread: HandlerThread? = null
private var codecHandler: Handler? = null
// Create a new thread for each init; clear it on release so reinitialization remains valid.
h264Encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        it.setCallback(mediaCodecCallback, checkNotNull(codecHandler))
    }
    else it.setCallback(mediaCodecCallback)
    it.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    it.start()
}
// Assign outputFormat only from onOutputFormatChanged().
// release(): quitSafely(), join, and clear the thread/handler references.
```
- 兼容/边界：带 Handler 重载需 API 23+；21/22 回退到无 Handler 重载及 MediaCodec 的默认 Looper。线程不能作为释放后不可重建的 eager `val`。回归：低-中，覆盖重复 start/release、H.264 输出连续性与回调时序。

### CAM2-10 [MEDIUM/P2] CameraAvcEncoder.queue 无界 → 背压/OOM
- 位置：`codec/CameraAvcEncoder.kt:26,202-204,127`
- 目标：
```kotlin
private val queue = ArrayBlockingQueue<ByteArray>(MAX_QUEUE_SIZE)
fun offerDataIntoQueue(data: ByteArray) {
    if (!queue.offer(data)) { queue.poll(); if (!queue.offer(data)) LogContext.log.w(TAG, "Encoder queue full, dropping frame") }
}
// private const val MAX_QUEUE_SIZE = 5
```
- 兼容/边界：非阻塞不阻塞相机回调线程；`queue` 由 public 改 private 需确认无外部读。回归：中，拥塞时主动丢帧，回归长时间录制画面连续性与内存曲线。

---

## 2. androidbase（cipher）

### CIP-1 [CRITICAL/P0] ZipUtil.unzip Zip Slip 路径穿越 + 无解压上限
- 位置：`androidbase/src/main/kotlin/com/leovp/androidbase/utils/cipher/ZipUtil.kt:172-198`（关键 181）
- 目标：
```kotlin
data class UnzipLimits(val maxEntryBytes: Long, val maxTotalBytes: Long)

@JvmOverloads
fun unzip(
    zipFilePath: String,
    destDir: String,
    limits: UnzipLimits = DEFAULT_UNZIP_LIMITS,
) {
    val destFile = File(destDir).canonicalFile
    require(destFile.isDirectory || destFile.mkdirs()) { "Cannot create $destFile" }
    val destRoot = destFile.path + File.separator
    ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath))).use { zipIn ->
        val buffer = ByteArray(BUFFER_SIZE)
        var totalWritten = 0L
        while (true) {
            val entry = zipIn.nextEntry ?: break
            val entryFile = File(destFile, entry.name)
            val canonicalPath = entryFile.canonicalPath
            require(canonicalPath == destFile.path || canonicalPath.startsWith(destRoot)) {
                "Blocked Zip Slip path traversal for entry: ${entry.name}"
            }
            if (entry.isDirectory) {
                require(entryFile.isDirectory || entryFile.mkdirs()) { "Cannot create ${entry.name}" }
            }
            else {
                val parent = requireNotNull(entryFile.parentFile)
                require(parent.isDirectory || parent.mkdirs()) { "Cannot create $parent" }
                require(parent.canonicalPath == destFile.path || parent.canonicalPath.startsWith(destRoot))
                val tempFile = File.createTempFile(".unzip-", ".tmp", parent)
                try {
                    BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                        var entryWritten = 0L
                        while (true) {
                            val length = zipIn.read(buffer)
                            if (length == -1) break
                            entryWritten += length
                            totalWritten += length
                            require(entryWritten <= limits.maxEntryBytes) { "Entry exceeds size limit: ${entry.name}" }
                            require(totalWritten <= limits.maxTotalBytes) { "Archive exceeds total size limit" }
                            output.write(buffer, 0, length)
                        }
                    }
                    if (entryFile.exists()) require(entryFile.delete()) { "Cannot replace ${entry.name}" }
                    require(tempFile.renameTo(entryFile)) { "Cannot move extracted entry: ${entry.name}" }
                } finally {
                    tempFile.delete() // Removes a partial file on failure.
                }
            }
            zipIn.closeEntry()
        }
    }
}
```
- 兼容/边界：minSdk 21；`@JvmOverloads` 保留原有两参数 JVM 入口，默认限制必须在 CHANGELOG 说明，并允许调用方显式收紧。`canonicalPath` 可阻止既有软链父目录逃逸，但不能完全消除目录被其它线程并发替换的 TOCTOU；目标目录应由应用私有、可信代码控制。测试：追加 `../evil.txt`、绝对路径、既有软链父目录、超限后无部分文件及正常嵌套解压。回归：中。

### CIP-2 [MEDIUM/P2] AES/PBKDF2 密钥材料未彻底清零
- 位置：`AESUtil.kt:304,450`（`secKey.toHexString`）、`PBKDF2Util.kt:422-450`（provider 路径 PBEKeySpec）
- 目标：PBKDF2 provider 路径 finally `keySpec.clearPassword()`；AESUtil 必须直接从原始字节生成 hex `CharArray`，不能先调用 `toHexString()`：
```kotlin
// PBKDF2Util
val keySpec = PBEKeySpec(plainPassphrase, salt, iterations, outputKeyLengthInBits)
try { secretKeyFactory.generateSecret(keySpec) } finally { keySpec.clearPassword() }
// AESUtil
private fun ByteArray.toHexChars(): CharArray = CharArray(size * 2).also { output ->
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        output[index * 2] = HEX_CHARS[value ushr 4]
        output[index * 2 + 1] = HEX_CHARS[value and 0x0F]
    }
}
val passphrase = secKey.toHexChars()
try { val rawKey = deriveKey(passphrase, salt, useSha256); /* cipher */ } finally { passphrase.fill('\u0000') }
```
- 兼容/边界：hex 大小写和顺序必须与原 `toHexString(true, "")` 完全一致，保证派生结果和密文兼容；`deriveKey` 改接收 CharArray 走 PBKDF2 的 CharArray 重载。CharArray 清零只能缩短副本生命周期，不能保证 provider 内部副本可清除。测试：固定向量派生字节与修复前一致、新旧密文互解及加解密往返。回归：低。

### CIP-3 [MEDIUM/P1] GZipUtil.decompress 无输出上限（GZIP bomb）
- 位置：`GZipUtil.kt:22-27`
- 目标：
```kotlin
private const val MAX_DECOMPRESSED_BYTES = 64L * 1024 * 1024
fun decompress(data: ByteArray, charset: Charset = StandardCharsets.UTF_8): String? = runCatching {
    GZIPInputStream(data.inputStream()).use { gis ->
        val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0L; var read: Int
        while (gis.read(buffer).also { read = it } != -1) {
            total += read; require(total <= MAX_DECOMPRESSED_BYTES) { "Decompressed data exceeds limit." }
            out.write(buffer, 0, read)
        }
        String(out.toByteArray(), charset)
    }
}.getOrNull()
```
- 兼容/边界：超限 `require` → 被 `runCatching.getOrNull` 收为 null（契约不变）；整体解码避免多字节跨 buffer 截断。测试：正常往返 + 超限返回 null + 多字节 UTF-8。回归：低。

### CIP-4 [LOW/P3] GZipUtil.isGzip <2 字节无长度检查
- 位置：`GZipUtil.kt:34-42`
- 目标：`fun isGzip(data: ByteArray): Boolean { if (data.size < 2) return false; val magic = (data[0].toInt() and 0xFF) or (data[1].toInt() shl 8); return magic and 0xFFFF == GZIPInputStream.GZIP_MAGIC }`。回归：极低。

---

## 3. androidbase（非加密）

### ABN-1 [HIGH/P1] KeepAliveReceiver.onReceive 用 GlobalScope
- 位置：`utils/system/KeepAlive.kt:108-116`
- 目标：
```kotlin
override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try { KeepAliveBus.sendAliveEvent() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { LogContext.log.e(TAG, "sendAliveEvent failed: ${e.message}", e) }
        finally { pendingResult.finish() }
    }
}
```
移除 `@OptIn(DelicateCoroutinesApi)` 与 `GlobalScope` import。兼容：`goAsync()` API 11+，`finish()` 置 finally（约 10s 窗口）。回归：低。

### ABN-2 [MEDIUM/P2] LeoTextureView setCallback 应在 configure 前 + 显式 Handler
- 位置：`ui/LeoTextureView.kt:167-172`
- 目标：新增 `callbackThread/callbackHandler`，`setCallback(cb, handler)`（API 23+，21/22 回退）在 `configure()` 前；`releaseDecoder()` 追加 `callbackThread?.quitSafely()`。注意回调迁后台线程后 `onOutputFormatChanged` 内 `context.toast(...)` 须确认已切主线程。回归：中（触及 initDecoder 线程模型，真机回归解码渲染）。

### ABN-3 [MEDIUM/P2] Watermark 空文本/零间距 step=0 抛异常
- 位置：`utils/Watermark.kt:148-159`
- 目标：`draw()` 开头 `if (text.isEmpty() || textSize <= 0f) return`；两处 step `coerceAtLeast(1)`；`WatermarkConfig` 的 `lineSpacerMultiple/wordSpacerMultiple` 消费处 `coerceAtLeast(0.1f)`。回归：低。

### ABN-4 [MEDIUM/P2] WifiUtil 单例持 Activity Context（T1）
- 位置：`utils/device/WifiUtil.kt:25-26`
- 目标：`class WifiUtil private constructor(context: Context){ private val ctx = context.applicationContext; companion object: SingletonHolder<WifiUtil,Context>(::WifiUtil) }`。回归：低。

### ABN-5 [LOW/P3] KeepAlive.start 裸 CoroutineScope 无异常处理
- 位置：`KeepAlive.kt:93-98`
- 目标：`CoroutineScope(Dispatchers.Main + SupervisorJob())`，collect 内 `runCatching{ callback() }.onFailure{ if(it is CancellationException) throw it; LogContext.log.e(...) }`。回归：低。

### ABN-6 [LOW/P3] KeepAlive.release 未置空 mediaPlayer
- 位置：`KeepAlive.kt:101-106`。目标：`release()` 内 `mediaPlayer?.release()` 后加 `mediaPlayer = null`。回归：极低。

### ABN-7 [LOW/P3] AppExt.installApk/exitApp 吞异常未记录 throwable
- 位置：`exts/android/AppExt.kt:37-39,48-50`。目标：`LogContext.log.e(TAG, "installApk exception: ${e.message}", e)`（传入 e）。回归：极低。

---

## 4. android-restricted

### AR-1 [HIGH/P1] DisplayCutoutManager 单例持首个 Activity（T1 例外）
- 位置：`notch/DisplayCutoutManager.kt:34-35`
- 目标：去掉 `SingletonHolder`，改每 Activity 独立实例：
```kotlin
class DisplayCutoutManager private constructor(private val activity: Activity) {
    companion object { @JvmStatic fun getInstance(activity: Activity) = DisplayCutoutManager(activity) }
}
```
- 兼容/边界：公开 API `getInstance(activity)` 不变。测试：两 Activity 返回不同实例。回归：中，语义由全局单例变每次新建（本类无可变共享状态，安全）。

### AR-2 [HIGH/P2] vivo 误路由到 HuaweiDisplayCutout
- 位置：`notch/DisplayCutoutManager.kt:78`。目标：`activity.isVivo -> displayCutout = VivoDisplayCutout()` 并补 import。须与 AR-5 一并落地。回归：低。

### AR-3 [HIGH/P2] ApplicationManager.application 急切求值使 init(context) 失效
- 位置：`utils/ApplicationManager.kt:14`。目标：`val application: Application by lazy { app ?: getApplicationByReflect() }`。兼容：调用方须在首次读 `application` 前 `init(appContext)`。回归：中。

### AR-4 [HIGH/P2] getApplicationByReflect 静态初始化链无失败隔离
- 位置：`:31-39`。目标：
```kotlin
@SuppressLint("PrivateApi")
private fun getApplicationByReflect(): Application = runCatching {
    val activityThread = Class.forName("android.app.ActivityThread")
    val at = activityThread.getMethod("currentActivityThread").invoke(null)
    requireNotNull(activityThread.getMethod("getApplication").invoke(at) as? Application) { "getApplication() null" }
}.onFailure { LogContext.log.e(TAG, "getApplicationByReflect failed; call init(context) first", it) }
 .getOrElse { error("ApplicationManager not initialized: call init(context) before accessing application") }
```
移除对 `init(app)` 的递归回填（由 lazy 缓存承担）。回归：中。

### AR-5 [HIGH/P2] VivoDisplayCutout densityDpi 直乘算 dp（放大 ~160×）
- 位置：`notch/impl/VivoDisplayCutout.kt:41,44`。目标：
```kotlin
private companion object { const val NOTCH_HEIGHT_DP = 27; const val NOTCH_WIDTH_DP = 100 }
private fun getNotchHeight(ctx: Context) = (NOTCH_HEIGHT_DP * ctx.density).toInt()
private fun getNotchWidth(ctx: Context) = (NOTCH_WIDTH_DP * ctx.density).toInt()
```
import 由 `densityDpi` 改 `density`。回归：低（此前值本就不可用）。

### AR-6 [MEDIUM/P2] DeviceProp.getSystemPropertyByStream FileInputStream 未关闭
- 位置：`utils/DeviceProp.kt:37-44`。目标：
```kotlin
private fun getSystemPropertyByStream(key: String): String = runCatching {
    val prop = Properties()
    FileInputStream(File(Environment.getRootDirectory(), "build.prop")).use { prop.load(it) }
    prop.getProperty(key, "")
}.getOrDefault("")
```
回归：极低。

### AR-7 [MEDIUM/P1] getSystemPropertyByShell 参数直拼命令（注入）
- 位置：`utils/DeviceProp.kt:46-49`。目标：
```kotlin
private val PROP_NAME_REGEX = Regex("^[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*$")
private fun getSystemPropertyByShell(propName: String): String {
    if (!PROP_NAME_REGEX.matches(propName)) { LogContext.log.w(TAG, "Rejected illegal system property name"); return "" }
    return ShellUtil.execCmd("getprop $propName", false).successMsg
}
```
兼容：真实属性名（`ro.*`/`ril.*`/`gsm.*`）全部匹配。回归：低。

### AR-8 [MEDIUM/P2] DeviceUtil 单例持 Context（T1）
- 位置：`utils/DeviceUtil.kt:16-17,24-32`。目标：`private constructor(ctx: Context){ private val appCtx = ctx.applicationContext ?: ctx }`，`PowerProfile` 构造改用 `appCtx`。回归：低。

### AR-9 [MEDIUM/P3] Huawei/Oppo/XiaoMi DisplayCutout 用 printStackTrace（T6）
- 位置：`notch/impl/HuaweiDisplayCutout.kt:28,69`、`OppoDisplayCutout.kt:49`、`XiaoMiDisplayCutout.kt:21`。目标：`.onFailure { LogContext.log.e(TAG, "xxx failed", it) }`，各文件补 import/TAG。注意 Huawei `cutoutAreaRect` 的 `onFailure`（回调降级）不改。回归：极低。

### AR-10 [LOW/P3] ActivityExt.startActivity(clsStr) Class.forName 无异常处理
- 位置：`ActivityExt.kt:23,44`。目标：
```kotlin
val cls = runCatching { Class.forName(clsStr) }.getOrElse { LogContext.log.e(TAG, "class not found: $clsStr", it); return }
```
Fragment 重载同理。回归：低-中（由抛异常变记录并静默返回，须确认调用方不依赖异常）。

---

## 5. audio

### AUD-1 [HIGH/P1] BaseMediaCodec.release 先释放 codec 后取消 scope（T4）
- 位置：`mediacodec/BaseMediaCodec.kt:51-59`（配合 `BaseMediaCodecSynchronous.kt:29-35`）
- 目标：保存 job，并把“等待后台退出”设计成明确的 suspend 生命周期 API。不能在公开 `release()`/UI 线程中无界 `runBlocking`，也不能从 codec job 自身等待自己结束：
```kotlin
protected var codecJob: Job? = null
suspend fun releaseAndJoin() {
    val job = codecJob
    require(job !== currentCoroutineContext()[Job]) {
        "releaseAndJoin() must be called by an external owner, not the codec worker"
    }
    job?.cancelAndJoin()
    codecJob = null
    ioScope.cancel()
    releaseCodecOnce()
}
// Synchronous.start(): codecJob = ioScope.launch { do { ensureActive() } while (process()); onEndOfStream() }
```
- 兼容/边界：`releaseAndJoin()` 必须维持“返回时后台任务已退出且 codec 已释放”的确定语义，因此禁止 codec worker 自身调用；worker 遇到错误时只上报终止原因并退出，由外部 owner 调用释放。若业务确实需要从 worker 发起请求，应另设名称明确的 `requestRelease(): Job/Deferred<Unit>`，调用方通过返回对象观察完成，不能让 `releaseAndJoin()` fire-and-forget。现有非 suspend `release()` 是公开接口，不能直接删除或改成异步返回；落地前需提供弃用和迁移入口。`releaseCodecOnce()` 使用原子状态保证只执行一次。回归：中高。

### AUD-2 [HIGH/P1] AacDecoder.onInputData 阻塞 take() 取消唤不醒（T4）
- 位置：`aac/AacDecoder.kt:83-86`。目标：
```kotlin
override fun onInputData(inBuf: ByteBuffer): Int =
    queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)?.let { inBuf.put(it); it.size } ?: 0
// private const val POLL_TIMEOUT_MS = 50L
```
- 兼容/边界：返回 0 需 `process()` 对 size<=0 跳过 `queueInputBuffer`（与 AUD-8 一并）。回归：中。

### AUD-3 [HIGH/P1] MicRecorder.stopRecord 取消后不等录音任务结束（T4）
- 位置：`MicRecorder.kt:94-175`。目标：保存 recordJob，先 `stop()` 让 native read 返回，再通过 suspend 入口等待并释放；防止从 `onRecording()` 所在 job 调用时 self-join，并统一保证 `onStop()` 只通知一次：
```kotlin
private var recordJob: Job? = null
private val stopped = AtomicBoolean(false)
fun startRecord() { audioRecord.startRecording(); recordJob = ioScope.launch { /* loop */ } }
suspend fun stopRecordAndJoin() {
    if (!stopped.compareAndSet(false, true)) return
    var ok = true
    runCatching { if (audioRecord.state == AudioRecord.STATE_INITIALIZED) audioRecord.stop() }.onFailure { ok=false; LogContext.log.e(TAG,"stop error",it) }
    val job = recordJob
    if (job === currentCoroutineContext()[Job]) {
        job.cancel()
        teardownScope.launch { job.join(); finishRecorderRelease(ok) }
        return
    }
    job?.cancelAndJoin()
    recordJob = null
    ioScope.cancel()
    finishRecorderRelease(ok)
}
```
`teardownScope` 必须独立于被取消的工作 scope，`releaseCodecOnce()`/`finishRecorderRelease()` 使用原子状态保证只执行一次。现有非 suspend `stopRecord()` 的兼容策略与 AUD-1 同时确定，不能简单塞入 `runBlocking`。回归：中高。

### AUD-4 [HIGH/P1] Stream Player audioDecoder/csd 并发无锁 TOCTOU
- 位置：`aac/AacStreamPlayer.kt:71-161`、`opus/OpusStreamPlayer.kt:78-170`。目标：用私有状态锁或单线程 command queue 串行化 init/decode/flush/stop；外部 `dropFrameCallback` 必须移出锁，释放流程与 AUD-1 的 suspend 入口统一：
```kotlin
private val lock = Any()
fun startPlayingStream(audioData: ByteArray, dropFrameCallback: () -> Unit) = synchronized(lock) {
    // Initialization branch; see AUD-5 for rollback.
    val decoder = audioDecoder
    if (csd0 == null || decoder == null) { LogContext.log.e(TAG,"not ready"); return }
    if (drop) runCatching { decoder.flush() } else decoder.decode(audioData)
}
// stopPlayingAndJoin(): detach decoder/CSD and advance generation under the lock,
// then await the old decoder outside the lock to avoid suspension or callback reentry.
```
- 兼容/边界：锁对象用私有 `lock` 非 `this`；不得持 JVM monitor 调用 suspend 函数或外部 callback。通过 generation 丢弃释放后到达的旧任务。回归：中高（覆盖 decode/flush/stop 并发和 callback 重入）。

### AUD-5 [HIGH/P1] 解码器初始化失败 csd 不回滚 → 下一帧 `!!` 崩溃
- 位置：`AacStreamPlayer.kt:71-94`、`OpusStreamPlayer.kt:78-103`。目标：init 成功才提交 csd0；失败回滚：
```kotlin
val newCsd0 = byteArrayOf(audioData[audioData.size-2], audioData[audioData.size-1])
runCatching { initAudioDecoder(newCsd0); audioTrackPlayer.play() }
  .onSuccess { csd0 = newCsd0; /* start playback timing */ }
  .onFailure { runCatching { audioDecoder?.release() }; audioDecoder=null; csd0=null; LogContext.log.e(TAG,"init failed, rolled back",it) }
```
OPUS 版一并回滚 csd1/csd2。维持不变量“csd0!=null ⇔ decoder 就绪”。回归：低中。

### AUD-6 [HIGH/P1] MicRecorder 录音缓冲区逐帧永久收缩
- 位置：`MicRecorder.kt:96-105`。目标：固定容量读缓冲，切片仅供本帧：
```kotlin
val pcmBuffer = ShortArray(bufferSizeInBytes / 2) // Keep a fixed reusable capacity.
while (true) {
    ensureActive()
    val recordSize = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
    if (recordSize <= 0) { handleReadError(recordSize); continue }
    val frame = pcmBuffer.copyOfRange(0, recordSize).toByteArrayLE()
    encodeWrapper?.encode(frame) ?: callback.onRecording(frame, isConfig=false, isKeyFrame=false)
}
```
回归：低（语义等价，修复缓冲复用）。

### AUD-7 [HIGH/P1] AudioRecord.read 负错误码 → copyOfRange 崩溃被吞、静默停止（T8）
- 位置：`MicRecorder.kt:94-127`。目标：
```kotlin
try {
    val pcmBuffer = ShortArray(bufferSizeInBytes / 2)
    while (true) {
        ensureActive()
        val recordSize = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
        when {
            recordSize > 0 -> { val frame = pcmBuffer.copyOfRange(0, recordSize).toByteArrayLE(); encodeWrapper?.encode(frame) ?: callback.onRecording(frame,false,false) }
            recordSize == 0 -> continue
            else -> { LogContext.log.e(TAG,"read error=$recordSize"); callback.onStop(false); break }
        }
    }
} catch (e: CancellationException) { throw e }
catch (e: Exception) { LogContext.log.e(TAG,"Recording loop failed",e); callback.onStop(false) }
```
- 兼容/边界：rethrow CancellationException（T8）；`onStop` 与 stopRecord 可能双触发 → 加 `isStopped` 幂等。回归：中。

### AUD-8 [HIGH/P1] process() 捕获异常后仍 return true → CPU 忙循环（T8）
- 位置：`mediacodec/BaseMediaCodecSynchronous.kt:37-99`（catch 95-97）。目标：
```kotlin
} catch (e: CancellationException) { throw e }
catch (e: MediaCodec.CodecException) {
    LogContext.log.e(TAG, "CodecException", e)
    notifyCodecFailure(e)
    return false
}
catch (e: IllegalStateException) { LogContext.log.e(TAG,"Codec illegal state, stopping",e); return false }
catch (e: Exception) { LogContext.log.e(TAG,"Unexpected decode error, stopping",e); return false }
return !isFinish
```
- 兼容/边界：`MediaCodec.CodecException` 继承 `IllegalStateException`，必须先捕获。recoverable 要求重建 codec，不能继续使用原实例；若实现 transient 重试，必须限制次数并增加退避。异常终止不能伪装成正常 EOS，应走独立错误回调。回归：中。

### AUD-9 [MEDIUM/P2] AudioTrackPlayer 奇数长度丢字节 + write 负码未校验
- 位置：`AudioTrackPlayer.kt:103-122`。目标：不能记录告警后静默丢掉最后一个字节。若输入允许任意网络分片，跨调用保存 pending byte；若 API 契约要求完整 PCM16 frame，则入口拒绝奇数长度。确认契约后再实现。`audioTrack.write` 负码透传且不能乘 2：
```kotlin
require(pcmBytes.size % 2 == 0) { "PCM16 byte count must be even" } // Complete-frame contract.
if (PLAYSTATE_PLAYING != audioTrack.playState) return 0
val playData = pcmBytes.toShortArrayLE()
val wroteSize = audioTrack.write(playData, 0, playData.size)
if (wroteSize < 0) { LogContext.log.e(TAG,"write error=$wroteSize"); return wroteSize }
return wroteSize * 2
```
回归：中；必须覆盖连续两个奇数分片不丢字节，或覆盖完整帧契约的异常行为。

### AUD-10 [MEDIUM/P2] getMinBufferSize 返回值未校验即构造
- 位置：`MicRecorder.kt:53-58`、`AudioTrackPlayer.kt:44-53`。目标：
```kotlin
val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
require(minBuf > 0) { "Invalid AudioRecord params ... (code=$minBuf)" }
bufferSizeInBytes = minBuf * recordMinBufferRatio
```
AudioTrack 同理。回归：低。

### AUD-11 [MEDIUM/P2] MicRecorder 无 RECORD_AUDIO 处理、未查 state 即录
- 位置：`MicRecorder.kt:34,80-93`。目标：
```kotlin
fun startRecord() {
    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) { LogContext.log.e(TAG,"AudioRecord not initialized (permission/config)"); callback.onStop(false); return }
    audioRecord.startRecording()
    if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) { LogContext.log.e(TAG,"startRecording failed"); callback.onStop(false); return }
    recordJob = ioScope.launch { /* AUD-7 loop */ }
}
```
回归：低。

### AUD-12 [MEDIUM/P2] AacEncoder csd0==null 仍下发全零脏 ADTS
- 位置：`aac/AacEncoder.kt:61-86,136-185`。目标：`onOutputData` 中 config 帧赋 csd0 后，若 `csd0 == null` 则 `return`（丢帧不发脏数据）；`addAdtsToDataWithoutCRC` 内 null 分支保留为二次防御。回归：低。

### AUD-13 [MEDIUM/P3] 全模块 printStackTrace 违反日志约定（T6）
- 位置：`BaseMediaCodec.kt:45,57,63`、`MicRecorder.kt:125,161,171`、`AudioTrackPlayer.kt:140,164,190,203`、`AacStreamPlayer.kt:153`、`OpusStreamPlayer.kt:161` 等。目标：统一 `LogContext.log.e(TAG, "<context>", it)`；含挂起点/取消处先 rethrow CancellationException。回归：低。

### AUD-14 [LOW/P3] runCatching 吞 CancellationException + AacEncoder FIXME 死代码
- 位置：吞取消处（AacStreamPlayer/OpusStreamPlayer/MicRecorder/BaseMediaCodecSynchronous）；死代码 `AacEncoder.kt:187-222`（`getAudioEncodingCsd0`/`getSampleFrequencyIndex`，无调用点）。目标：`onFailure { if (it is CancellationException) throw it; LogContext.log.w(...) }`；删死代码并清理 import。回归：低。

---

## 6. camerax

### CX-1 [HIGH/P1] SoundManager 单例持 Fragment Context（T1）
- 位置：`utils/SoundManager.kt:17`（注入点 `base/BaseCameraXFragment.kt:152`）。目标：存 applicationContext；`soundPool` 改可空并支持 release 后重建：
```kotlin
class SoundManager private constructor(context: Context) {
    private val appCtx = context.applicationContext
    private var soundPool: SoundPool? = null
    suspend fun loadSounds() = withContext(Dispatchers.IO) { soundPool?.release(); soundPool = SoundPool.Builder()...build().apply { load(appCtx, R.raw...) } }
    fun release() { runCatching { soundPool?.autoPause(); soundPool?.release() }; soundPool = null }
    private fun playSound(id: Int, v: Float) { soundPool?.play(id, v, v, 1, 0, 1f) } // No-op until loaded.
}
// Injection: SoundManager.getInstance(requireContext().applicationContext)
```
回归：低（play 未加载由崩溃变 no-op）。

### CX-2 [HIGH/P1] captureForBytes 未在 finally 关闭 ImageProxy（T3）
- 位置：`base/BaseCameraXFragment.kt:226-297`（close 在 247 中段）。目标：`image.use{}`：
```kotlin
override fun onCaptureSuccess(image: ImageProxy) {
    try {
        val oriImageBytes = image.use { proxy ->
            proxy.planes[0].buffer.toByteArray()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            showShutterAnimation(viewFinder)
            soundManager.playShutterSound()
            val saved = withContext(Dispatchers.Default) { decodeRotateAndBuild(oriImageBytes) }
            onImageSaved(saved, null)
        }
    } catch (e: Exception) {
        LogContext.log.e(logTag, "onCaptureSuccess error", e)
        activity?.runOnUiThread {
            val owner = viewLifecycleOwnerLiveData.value
            if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.INITIALIZED) == true) {
                onImageSaved(null, e)
            }
        }
    }
}
```
`onCaptureSuccess()` 运行在 `cameraExecutor`，不能直接操作 View；`requireActivity()` 也不能用于 detach 后的错误路径。回归：中。

### CX-3 [HIGH/P1] 依赖 ViewBinding 的任务用 Fragment lifecycleScope（T5）
- 位置：`CameraFragment.kt:185/609/624/682`、`base:249/363`。目标：触碰 View/binding 的协程改 `viewLifecycleOwner.lifecycleScope`；相机回调（可能离开页面后触发）用 `lifecycleScope` + `withContext(Main){ if(!isAdded||view==null) return@withContext; onImageSaved(...) }`。回归：中，回归“进入相机→立即返回”。

### CX-4 [HIGH/P1] binding 永久 lateinit，onDestroyView 未清理（T5）
- 位置：`base/BaseCameraXFragment.kt:112`。目标：可空 backing + 非空访问器：
```kotlin
private var _binding: B? = null
// Preserve the published binding getter/setter visibility until the compatibility migration.
// New internal code reads only through a View-lifecycle-bound accessor.
val viewBinding: B get() = checkNotNull(_binding) { "binding accessed outside of view lifecycle" }
override fun onCreateView(...): View { _binding = getViewBinding(...); return viewBinding.root }
override fun onDestroyView() { _binding = null; super.onDestroyView() }
```
- 兼容/边界：当前 `lateinit var binding` 是公开属性。直接改成 `protected val` 会删除公开 setter、降低 getter 可见性，属于源码和二进制不兼容。落地前用 binary compatibility 工具确认 API；必要时保留并弃用旧属性，在下一个 major 版本再移除。与 CX-3 互补。回归：中高。

### CX-5 [DECISION] bindToLifecycle(this) 使 use case 长于 View
- 位置：`CameraFragment.kt:408`（VideoFragment 已用 viewLifecycleOwner）。目标（评估后）：对齐 `viewLifecycleOwner`。**需先确认**是否有意在 View 短暂重建期保活相机；若是则保留并加注释说明，否则统一为 `viewLifecycleOwner`。回归：中。

### CX-6 [MEDIUM/P2] PhotoFragment/VideoFragment `!!` NPE（T7）
- 位置：`PhotoFragment.kt:41`、`VideoFragment.kt:488`。目标：`mediaFile` 判空早退；`getFileRealPath(...)` 返回 null 时记录并 `return@let`（SAF/content:// 常无真实路径）。回归：低。

### CX-7 [MEDIUM/P2] ExtensionsManager 每次 bind 重复初始化
- 位置：`base/BaseCameraXFragment.kt:661-738`。目标：缓存 `ExtensionsManager` 实例复用（`await()` 需改 suspend，或最小改动仅缓存实例），六条诊断日志降级 `d`。回调触及 binding 须配合 CX-3/4 判空。回归：中（若改 suspend 牵动 bindCameraUseCases 调用链）。

### CX-8 [MEDIUM/P3] adjustBitmapRotation getBitmapAndFree() `!!`（T7）
- 位置：`base/BaseCameraXFragment.kt:416,433,451`。目标：
```kotlin
private fun BitmapProcessor.resultOr(fallback: Bitmap): Bitmap =
    getBitmapAndFree() ?: run { LogContext.log.e(logTag,"BitmapProcessor null; fallback"); fallback }
```
注意确认回退用源 bitmap 时其未被 native 释放（核对 `lib-image`）。回归：低-中（null 路径由崩溃变未旋转图，需产品确认或改错误上报）。

### CX-9a [LOW/P3] CameraExt CameraCharacteristics.get(...) `!!`（T7）
- 位置：`utils/CameraExt.kt:30,33,36,39,48`。目标：`SCALER_STREAM_CONFIGURATION_MAP` 用 `checkNotNull`（带信息），其余给默认（无闪光=false、硬件级别按 LEGACY、fps=emptyArray）。回归：低。

### CX-9b [LOW/P3] LuminosityAnalyzer 每帧装箱
- 位置：`analyzer/LuminosityAnalyzer.kt:75`。目标：
```kotlin
var sum = 0L; for (b in data) sum += (b.toInt() and 0xFF)
val luma = if (data.isEmpty()) 0.0 else sum.toDouble() / data.size
```
顺带 `image.use{}` 保证 close。回归：低（数值等价）。

### CX-9c [LOW/P3] PermissionsFragment repeatOnLifecycle 误用于一次性导航
- 位置：`fragments/PermissionsFragment.kt:58-66`。目标：改 `viewLifecycleOwner.lifecycle.withStarted { navigate(...) }`（一次性）；注意 `onCreate` 已授权分支调用须移到 `onViewCreated`（否则 viewLifecycleOwner 未就绪）。回归：中。

---

## 7. circle-progressbar

### CPB-1 [HIGH/P1] 无限 ValueAnimator 未随 detach/状态切换取消
- 位置：`CircleProgressbar.kt:76,273,525,542-554`；`setIdle/setFinish/setError:267-302`。目标：
```kotlin
override fun onAttachedToWindow() { super.onAttachedToWindow(); if (currState==State.Type.STATE_INDETERMINATE && ::internalIndeterminateAnimator.isInitialized && !internalIndeterminateAnimator.isStarted) internalIndeterminateAnimator.start() }
override fun onDetachedFromWindow() { if (::internalIndeterminateAnimator.isInitialized) internalIndeterminateAnimator.cancel(); super.onDetachedFromWindow() }
// First line of setIdle/setFinish/setError: cancel the initialized animator.
```
用 `cancel()`（非 end）避免 detach 时多一次 update/invalidate。回归：中。

### CPB-2 [HIGH/P1] 未覆写 onMeasure → wrap_content 退化 0×0
- 位置：`CircleProgressbar.kt`（全类无 onMeasure）。目标：
```kotlin
override fun onMeasure(w: Int, h: Int) { val d = computeDesiredSize(); setMeasuredDimension(resolveDesiredSize(d,w), resolveDesiredSize(d,h)) }
private fun computeDesiredSize(): Int {
    val icon = maxOf(idleItem.width,finishItem.width,errorItem.width,cancelItem.width, idleItem.height,finishItem.height,errorItem.height,cancelItem.height)
    val ring = ((internalProgressMargin + internalProgressPaint.strokeWidth) * 2).toInt()
    return icon + ring + maxOf(paddingLeft+paddingRight, paddingTop+paddingBottom)
}
private fun resolveDesiredSize(desired: Int, spec: Int): Int = when (MeasureSpec.getMode(spec)) {
    MeasureSpec.EXACTLY -> MeasureSpec.getSize(spec)
    MeasureSpec.AT_MOST -> min(desired, MeasureSpec.getSize(spec)); else -> desired
}
```
回归：中（wrap_content 尺寸此前若“碰巧”可见会变化）。

### CPB-3 [MEDIUM/P2] maxProgress 允许 0/负 → getDegrees 除零 NaN
- 位置：`:149,193-198,556-557`。目标：setter/XML `coerceAtLeast(1)`，currentProgress 同步 `coerceIn(0,max)`；getDegrees `val safeMax = internalMaxProgress.coerceAtLeast(1)`。回归：低。

### CPB-4 [MEDIUM/P2] 每帧 Drawable.setTint() 绘制热路径冗余
- 位置：`:418,440-441`。目标：把 tint 移到 `State` 的 `iconTint`/`internalIcon` setter（颜色变化时一次），onDraw 不再 setTint；setter 内建议 `mutate()` 防串色。回归：中。

### CPB-5 [MEDIUM/P2] currentProgress 无下限 / animDuration 不同步 / `!!` / 无障碍
- 位置：`:199-205,206-211,508`、`State.kt:26`、init 块。目标：`currentProgress = progress.coerceIn(0,max)`；animDuration setter 同步 `internalIndeterminateAnimator.duration`；`onRestoreInstanceState` `?: STATE_IDLE` 去 `!!`；`State.getIcon()` 用带上下文的非法状态错误。无障碍不能只设置 `isClickable/isFocusable`：同时覆写 `performClick()`、维护 content description，并通过 `ViewCompat.setStateDescription()` 暴露 idle/progress/finished/error 状态。回归：中。

### CPB-6 [LOW/P3] 监听器列表遍历期被回调修改 → CME
- 位置：`:337-339,370-388`。目标：遍历前 `toList()` 快照。回归：低。

---

## 8. http

### HTTP-1 [HIGH/P0] 默认 BODY 日志 + 敏感 Header 无脱敏 + 宿主无法单独关闭
- 位置：`retrofit/base/BaseHttpRequest.kt:43-47,67-83`、`okhttp/HttpLoggingInterceptor.kt:143-155,214-227`。目标：
```kotlin
// BaseHttpRequest: public level, default NONE; snapshot it when building the client.
@Volatile var logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE
private val logInterceptor get() = HttpLoggingInterceptor().apply { level = logLevel }
// getHeaderInterceptor: log header names, never injected values.
LogContext.log.d(TAG, "Add header: $k", outputType = LogOutType.HTTP_HEADER)
// HttpLoggingInterceptor: redact by case-insensitive header name.
private val SENSITIVE_HEADERS = setOf("authorization","proxy-authorization","cookie","set-cookie")
private fun redact(name: String, value: String) = if (name.lowercase() in SENSITIVE_HEADERS) "██" else value
logger.log("$name: ${redact(name, headers.value(i))}", outputType = LogOutType.HTTP_HEADER)
```
- 兼容/边界：默认 NONE 是行为变更（须在 CHANGELOG 标注）。BODY 模式还必须服从 HTTP-3 的双向大小限制；仅脱敏 Header 不能保护 JSON/form body 中的凭据。测试：`redact` 单测、level=HEADERS 不含明文敏感值、默认 NONE 无输出。回归：中。

### HTTP-2 [HIGH/P1] HttpRequest 共享可变 Retrofit.Builder 竞态
- 位置：`retrofit/HttpRequest.kt:16,21-25`。目标：不复用共享 builder，每次新建：
```kotlin
@Volatile private var headerMap: Map<String,String> = emptyMap()
fun initWithHeader(m: Map<String,String>) { headerMap = m.toMap() }
fun getRetrofit(baseUrl: String): Retrofit =
    getRetrofitBuilder(headerMap).baseUrl(baseUrl).build()
```
每次读取不可变 header 快照，不能保留调用方可继续修改的 Map。`getRetrofitBuilder` 为现有函数名；若为了连接池复用缓存 OkHttpClient，缓存 key 必须包含 header、timeout、TLS 和日志配置。回归：低-中。

### HTTP-3 [HIGH/P0] source.request(Long.MAX_VALUE) 整体读入内存 OOM
- 位置：`okhttp/HttpLoggingInterceptor.kt:166-175,235-251`。目标：请求和响应都限制日志副本。响应最多 `request(limit + 1)`，再从 `source.buffer.clone()` 只读取 `min(buffer.size, limit)` 字节；不能对整个 clone 调 `readString()`。请求体若 `contentLength > limit`、长度未知、one-shot 或 duplex，直接省略 BODY；其余通过有界 sink/Buffer 记录。charset 用 `?: DEFAULT_CHARSET`，日志注明截断。覆盖 gzip/encoded、已经预缓冲超过 limit、未知长度及大请求体测试。回归：中（业务 body 不得被消费或修改）。

### HTTP-4 [HIGH/P1] BaseProgressObserver Disposable 不暴露取消入口
- 位置：`observers/base/BaseProgressObserver.kt:19-22`。目标：
```kotlin
val isDisposed get() = mDisposable?.isDisposed ?: true
fun cancel() { mDisposable?.takeIf { !it.isDisposed }?.dispose() }
```
宿主在 onDestroy 调 `cancel()`。回归：低（纯新增 API）。

### HTTP-5 [MEDIUM/P2] onError 丢堆栈 / internal promisesBody / charset `!!`
- 位置：`BaseProgressObserver.kt:31`、`HttpLoggingInterceptor.kt:11,170,228,242`。目标：`log.e(tag, "onError: ${e.message}", e)`；用公开 `hasReadableBody()`（复刻 promisesBody 语义，覆盖 204/304/HEAD）替代 internal import；charset 改 `?: DEFAULT_CHARSET`。回归：低（需覆盖 body 判断边界）。

### HTTP-6 [MEDIUM/P3] 测试覆盖不足
- 位置：`http/src/test/...`（仅 1 文件）。目标：为 `isPlaintext`/`bodyEncoded`/`BaseProgressObserver.onError` 异常分类补 JUnit5 纯单测（`bodyEncoded`/charset 解析按需提 `internal`）。回归：无。

### HTTP-7 [LOW/P3] 无效 @Suppress + 二进制分支提前 return
- 位置：`NoProgressObserver.kt:14`、`HttpLoggingInterceptor.kt:244-247`。目标：删无效 `@Suppress("unchecked")`；二进制分支改 if/else 保证走到末尾分隔线（与 HTTP-3 同块合并）。回归：极低。

---

## 9. lib-bytes

> 本模块无 `log` 依赖，错误处理用 `require`/`android.util.Log`/rethrow，**不得引入 log**。

### LB-1 [HIGH/P1] ByteBuffer.copy/copyAll 未继承 order() → 小端静默损坏
- 位置：`ByteBufferExt.kt:41-54,76-87`。目标：`allocate` 后 `dst.order(order())`：
```kotlin
val dst = ByteBuffer.allocate(len)
dst.order(order()) // Preserve source byte order.
for (i in 0 until len) dst.put(i, this.get(i))
```
copy 与 copyAll 都加。测试：小端 putInt 复制后 `dst.order()==LITTLE_ENDIAN` 且 `getInt(0)` 一致；大端默认一致。回归：极低。

### LB-2 [MEDIUM/P2] toShortArray/LE 奇数长度静默丢字节
- 位置：`ByteArrayExt.kt:171-182`。目标：函数入口 `require(size % 2 == 0) { "ByteArray size must be even ... was $size" }`（用 IllegalArgumentException，不引入 log）。测试：奇数长度抛异常、空数组返回空、偶数往返。回归：中低（落地前 grep 调用点确认无“依赖丢末字节”）。

### LB-3 [DECISION] toAsciiString 编码契约未定 + 符号扩展
- 位置：`ByteArrayExt.kt:118-119`。**需先定契约**，三选一：
```kotlin
// A Strict ASCII: reject values above 0x7F.
// B ISO-8859-1: map each unsigned byte to the same code point.
// C UTF-8 text: String(this, Charsets.UTF_8), which changes the per-byte delimiter shape.
```
测试按契约命名（如 B：`0xFF → "ÿ"`）。回归：中（依赖当前 `￿` 损坏输出者会变；须定契约 + grep 调用点）。

### LB-4 [MEDIUM/P2] ByteBufferExt 三函数零测试覆盖
- 位置：`ByteBufferExt.kt:10-14,41-87`。目标：新建 `ByteBufferExtUnitTest` 覆盖 toByteArray（仅 remaining）、copy（内容 + 源 position/limit 恢复）、copyAll（整 buffer + 源 position 恢复）、大小端继承（联动 LB-1）、空 buffer。注意 copy 内部已 `flip()`，测试勿再 flip。回归：无。

### LB-5 [LOW/P3] readByte 掩码 no-op / readInt 缺括号 / 注释死代码
- 位置：`ByteArrayExt.kt:18,34-42,125-146`。目标：`readByte(index) = this[index]`（去无效掩码）；`readInt/readIntLE` 每字节段显式加括号 `(x and 0xFF) shl n`；删 `:125-146` 注释掉的旧 `toHexString`（确认 `HEX_CHARS` 仍被正式实现用）。均为等价重构，靠既有 `bytesToNumber` 测试守护。回归：极低。
