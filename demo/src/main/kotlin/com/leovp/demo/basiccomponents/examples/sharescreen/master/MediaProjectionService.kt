package com.leovp.demo.basiccomponents.examples.sharescreen.master

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.leovp.android.exts.getBaseDirString
import com.leovp.android.exts.toast
import com.leovp.image.compressBitmap
import com.leovp.image.writeToFile
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.screencapture.screenrecord.ScreenCapture
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordMediaCodecStrategy
import io.karn.notify.Notify
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

/**
 * On Android 14 or above, check the following blog:
 * https://stackoverflow.com/questions/77307867/screen-capture-mediaprojection-on-android-14
 *
 * On Android 14 or above, when you do screen capture, you **MUST** follow the correct steps:
 * 1. Create your own ScreenCaptureService.
 * 2. Request permission to capture screen.
 * 3. If permission is granted, then start your foreground service by calling `startForeground`.
 *
 * Please note that, request permission first, then start your foreground service by calling
 * `startForeground`.
 * Make sure you run in correct steps, otherwise, your app will crash.
 *
 * > The app must set the foregroundServiceType attribute to
 * > FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION in the element of the app's manifest file.
 * > For an app targeting SDK version U or later,
 * > the user must have granted the app with the permission to start a projection,
 * > before the app starts a foreground service with the type
 * > android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION.
 * > Additionally, the app must have started the foreground service with that type before calling
 * > this API here, or else it'll receive a SecurityException from this API call,
 * > unless it's a privileged app.
 *
 * Android Q+(Android 10+) MediaProjection must be used in Service and with
 * android:foregroundServiceType="mediaProjection" permission.
 * Example:
 * <pre>
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
 * android:minSdkVersion="34" />
 *
 * <service
 *  android:name=".MediaProjectionService"
 *  tools:targetApi="q"
 *  android:enabled="true"
 *  android:exported="false"
 *  android:foregroundServiceType="mediaProjection" />
 * </pre>
 *
 * The service owns output streams and its projection callback. The processor owns its display,
 * codec and EGL resources; both owners must tolerate the system stopping projection first.
 * Projection stop is a terminal producer operation, independent of releasing those app resources.
 */

class MediaProjectionService : Service() {

    companion object {
        private const val TAG = "MPS"
        val VIDEO_ENCODE_TYPE = ScreenRecordMediaCodecStrategy.EncodeType.H264
    }

    var outputH26xFile = false
    private var videoH26xOs: BufferedOutputStream? = null
    private val outputLock = Any()
    private val lifecycleLock = Any()
    private val captureGeneration = AtomicLong()

    @Volatile
    private var destroyed = false

    @Volatile
    private var acceptingWork = false
    private var cleanupQueued = false

    private var resultCode = -1
    private lateinit var data: Intent

    private lateinit var serviceThread: HandlerThread
    private lateinit var serviceHandler: Handler
    private val binder = CustomBinder()
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    @Volatile
    private var projectionCallback: ServiceProjectionCallback? = null

    private class ServiceProjectionCallback(
        service: MediaProjectionService,
        val projection: MediaProjection,
        val generation: Long,
    ) : MediaProjection.Callback() {
        private val owner = WeakReference(service)
        override fun onStop() {
            owner.get()?.onProjectionStopped(this)
        }
    }

    @Volatile
    var screenProcessor: ScreenProcessor? = null
        private set

    val vpsSpsPps
        get() = if (acceptingWork && !destroyed) {
            (screenProcessor as? ScreenRecordMediaCodecStrategy)?.vpsSpsPpsBuf
        } else {
            null
        }

    var screenDataUpdateListener: ScreenDataUpdateListener? = null

    private fun screenDataListenerFor(generation: Long) = object : ScreenDataListener {
        override fun onError(error: Throwable) {
            synchronized(lifecycleLock) {
                if (!isCurrentCapture(generation)) return
                LogContext.log.e(TAG, "Screen capture failed; releasing the service owner.", error)
                requestCaptureCleanup()
            }
        }

        override fun onDataUpdate(buffer: Any, flags: Int, presentationTimeUs: Long) {
            if (!isCurrentCapture(generation)) return
            val data = buffer as ByteArray
            synchronized(outputLock) {
                if (isCurrentCapture(generation) && outputH26xFile) {
                    try {
                        videoH26xOs?.write(data)
                    } catch (e: Exception) {
                        LogContext.log.e(TAG, "onDataUpdate error", e)
                    }
                }
            }
            // LogContext.log.e("Data[${buffer.size}]≈${buffer.size*1.0f/1024/1024} flag=$flags")
            if (isCurrentCapture(generation)) {
                screenDataUpdateListener?.onUpdate(data, flags, presentationTimeUs)
            }

            // Bitmap for screenshot
            //            val file = FileUtil.createImageFile(app, "bmp")
            //            ImageUtil.writeBitmapToFile(file, buffer as Bitmap, 90)
        }
    }

    override fun onCreate() {
        LogContext.log.i(TAG, "=====> onCreate <=====")
        super.onCreate()
        serviceThread = HandlerThread("service-thread")
        serviceThread.start()
        serviceHandler = Handler(serviceThread.looper)
        // startForeground()
    }

    private fun setDebugInfo() {
        if (outputH26xFile) {
            try {
                val baseFolder = File(
                    getExternalFilesDir(null)?.absolutePath + File.separator + "leo-media"
                )
                LogContext.log.w(TAG, "Output H.26x file path=$baseFolder")
                baseFolder.mkdirs()
                val videoH26xFile = File(
                    baseFolder,
                    "screen" + when (VIDEO_ENCODE_TYPE) {
                        ScreenRecordMediaCodecStrategy.EncodeType.H264 -> ".h264"
                        ScreenRecordMediaCodecStrategy.EncodeType.H265 -> ".h265"
                    }
                )
                videoH26xOs = BufferedOutputStream(FileOutputStream(videoH26xFile))
            } catch (e: Exception) {
                LogContext.log.e(TAG, "setDebugInfo() exception.", e)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        LogContext.log.i(TAG, "=====> onBind <=====")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogContext.log.i(TAG, "=====> onStartCommand <=====")
        // A process restart cannot restore single-use projection consent or the native session.
        return START_NOT_STICKY
    }

    override fun onRebind(intent: Intent?) {
        LogContext.log.i(TAG, "=====> onRebind <=====")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        LogContext.log.w(TAG, "=====> onUnbind <=====")
        stopScreenShare()
        return false
    }

    override fun onDestroy() {
        LogContext.log.w(TAG, "=====> onDestroy <=====")
        requestCaptureCleanup(destroyService = true)
        super.onDestroy()
    }

    // https://stackoverflow.com/questions/77307867/screen-capture-mediaprojection-on-android-14
    fun startForegroundNotification() {
        val notify = Notify.with(this)
            .alerting("update-app-notification") {
                channelName = "Foreground service channel name"
                channelDescription = "Foreground service channel desc"
                lockScreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                channelImportance = Notify.IMPORTANCE_NORMAL
            }
            .content {
                title = "App is running..."
            }.meta {
                clickIntent = PendingIntent.getActivity(
                    this@MediaProjectionService,
                    SystemClock.elapsedRealtime().toInt(),
                    Intent(this@MediaProjectionService, ScreenShareMasterActivity::class.java)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        ),
                    // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    // } else {
                    //     PendingIntent.FLAG_UPDATE_CURRENT
                    // }

                )
                cancelOnClick = false
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                10086,
                notify.asBuilder().build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(10086, notify.asBuilder().build())
        }
    }

    /**
     * This method must be called before `startScreenShare()`
     */
    fun setData(resultCode: Int, data: Intent) {
        synchronized(lifecycleLock) {
            if (destroyed) return
            this.resultCode = resultCode
            this.data = data
        }
    }

    // ==========================================

    inner class CustomBinder : Binder() {
        val service: MediaProjectionService get() = this@MediaProjectionService
    }

    /**
     * This method must be following `setData()` method
     */
    fun startScreenShare(setting: ScreenShareSetting) {
        synchronized(lifecycleLock) {
            if (destroyed || cleanupQueued || screenProcessor != null) return
            // An OEM failure to unregister must not lose the old callback or let a new session
            // overwrite its handle. Retry when the caller next starts or requests cleanup.
            runCleanupStep("previous projection callback unregister") {
                unregisterProjectionCallback()
            }
            if (projectionCallback != null) return
            LogContext.log.i(TAG, "startScreenShare: ${setting.toJsonString()}")
            val generation = captureGeneration.incrementAndGet()
            setDebugInfo()
            try {
                mediaProjectionManager =
                    applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as
                        MediaProjectionManager
                val mediaProjectionRef = mediaProjectionManager.getMediaProjection(resultCode, data)
                requireNotNull(mediaProjectionRef) { "MediaProjection can't be null" }
                mediaProjection = mediaProjectionRef
                val callback = ServiceProjectionCallback(this, mediaProjectionRef, generation)
                projectionCallback = callback
                mediaProjectionRef.registerCallback(callback, Handler(Looper.getMainLooper()))

                val processor = ScreenCapture.Builder(
                    // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
                    setting.width,
                    // 800 1024 1280
                    setting.height,
                    setting.dpi,
                    mediaProjectionRef,
                    // BY_IMAGE_2_H26x
                    // BY_MEDIA_CODEC
                    // BY_RAW_BMP
                    ScreenCapture.BY_MEDIA_CODEC,
                    screenDataListenerFor(generation)
                )
                    .setEncodeType(VIDEO_ENCODE_TYPE)
                    .setGoogleEncoder(false)
                    .setFps(setting.fps)
                    // For H26x and x264
                    .setBitrate(setting.bitrate)
                    // For H26x
                    .setBitrateMode(setting.bitrateMode)
                    // 20
                    .setIFrameInterval(setting.iFrameInterval)
                    // For bitmap
                    //          .setSampleSize(2)
                    .build()
                // Publish before lifecycle calls so every partially initialized owner is reachable.
                screenProcessor = processor
                processor.onInit()
                acceptingWork = true
                processor.onStart()
            } catch (error: Throwable) {
                acceptingWork = false
                captureGeneration.incrementAndGet()
                LogContext.log.e(TAG, "startScreenShare() failed; releasing the processor.", error)
                runCleanupStep("projection callback unregister after start failure") {
                    unregisterProjectionCallback()
                }
                val processor = screenProcessor
                val projection = mediaProjection
                if (processor != null && projection != null) {
                    releaseProcessorAfterStartFailure(processor, projection, error)
                } else {
                    runCleanupStep("projection stop after start failure") { projection?.stop() }
                }
                closeDebugOutput()
                screenProcessor = null
                mediaProjection = null
                throw error
            }
        }
    }

    /** Stops and releases this one-shot capture; a new session needs fresh projection consent. */
    fun stopScreenShare() {
        LogContext.log.w(TAG, "stopScreenShare")
        requestCaptureCleanup()
    }

    fun onReleaseScreenShare() {
        LogContext.log.w(TAG, "onReleaseScreenShare()")
        requestCaptureCleanup()
    }

    private fun onProjectionStopped(callback: ServiceProjectionCallback) {
        synchronized(lifecycleLock) {
            if (
                destroyed ||
                projectionCallback !== callback ||
                mediaProjection !== callback.projection ||
                captureGeneration.get() != callback.generation
            ) {
                return
            }
            requestCaptureCleanup()
        }
    }

    private fun unregisterProjectionCallback() {
        val callback = projectionCallback ?: return
        callback.projection.unregisterCallback(callback)
        // Clear only on success, so a failed unregister remains eligible for retry.
        projectionCallback = null
    }

    private fun requestCaptureCleanup(destroyService: Boolean = false) {
        synchronized(lifecycleLock) {
            if (destroyService) destroyed = true
            acceptingWork = false
            captureGeneration.incrementAndGet()
            if (cleanupQueued) return
            cleanupQueued = true
            // Keep the published owner until every step completes. Never remove older queued
            // cleanup: onDestroy joins that work and only then requests the Looper to quit.
            val cleanup = Runnable { releaseOwnedCapture() }
            if (!serviceHandler.post(cleanup)) cleanup.run()
        }
    }

    private fun releaseOwnedCapture() {
        val (processor, projection) = synchronized(lifecycleLock) {
            screenProcessor to mediaProjection
        }
        try {
            runCleanupStep("projection callback unregister") { unregisterProjectionCallback() }
            runCleanupStep("screenProcessor onStop") { processor?.onStop() }
            runCleanupStep("screenProcessor onRelease") { processor?.onRelease() }
            closeDebugOutput()
            // onStop may already stop the projection; this idempotent fallback also covers
            // partial startup and processor cleanup failures.
            runCleanupStep("projection stop") { projection?.stop() }
        } finally {
            val quit = synchronized(lifecycleLock) {
                screenProcessor = null
                mediaProjection = null
                cleanupQueued = false
                if (destroyed) screenDataUpdateListener = null
                destroyed
            }
            if (quit) serviceThread.quitSafely()
        }
    }

    private fun closeDebugOutput() {
        synchronized(outputLock) {
            val output = videoH26xOs ?: return
            try {
                runCleanupStep("output flush") { output.flush() }
            } finally {
                runCleanupStep("output close") { output.close() }
                videoH26xOs = null
            }
        }
    }

    private fun runCleanupStep(step: String, action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            LogContext.log.e(TAG, "$step failed.", error)
        }
    }

    private fun isCurrentCapture(generation: Long): Boolean =
        acceptingWork && !destroyed && captureGeneration.get() == generation

    /** Called on the main thread by the Activity's rotation observer. */
    fun changeOrientation() {
        synchronized(lifecycleLock) {
            if (!acceptingWork || destroyed) return
            try {
                screenProcessor?.changeOrientation()
            } catch (error: Exception) {
                LogContext.log.e(TAG, "Capture rotation failed; releasing the owner.", error)
                requestCaptureCleanup()
            }
        }
    }

    @Suppress("WeakerAccess")
    fun triggerIFrame() {
        synchronized(lifecycleLock) {
            if (!acceptingWork || destroyed) return
            LogContext.log.w(TAG, "triggerIFrame()")
            try {
                (screenProcessor as? ScreenRecordMediaCodecStrategy)?.requestKeyFrame()
            } catch (error: Exception) {
                LogContext.log.e(TAG, "Key-frame request failed.", error)
            }
        }
    }

    fun takeScreenshot(width: Int?, height: Int?) {
        synchronized(lifecycleLock) {
            if (!acceptingWork || destroyed) return
            val generation = captureGeneration.get()
            LogContext.log.w(TAG, "Prepare to call takeScreenshot($width, $height)...")
            try {
                screenProcessor?.takeScreenshot(width, height) { bmp ->
                    // Use the same FIFO as cleanup: an accepted save finishes before its owner is
                    // cleared and another capture can start, including when stop arrives during IO.
                    val posted = synchronized(lifecycleLock) {
                        isCurrentCapture(generation) &&
                            serviceHandler.post { saveScreenshot(bmp, generation) }
                    }
                    if (!posted) bmp.recycle()
                }
            } catch (error: Exception) {
                // Busy and already-terminal strategies may reject synchronously. Retirement still
                // owns terminal cleanup; an ordinary busy request must not stop the recording.
                LogContext.log.e(TAG, "Screenshot request rejected.", error)
                toast("Unable to capture screenshot", error = true)
            }
        }
    }

    private fun saveScreenshot(bitmap: Bitmap, generation: Long) {
        try {
            if (!isCurrentCapture(generation)) return
            val compressed = bitmap.compressBitmap()
            try {
                val jpegFile = File(getBaseDirString("screenshot"), "screenshot.jpg")
                compressed.writeToFile(jpegFile)
                LogContext.log.w(TAG, "onScreenshot[${jpegFile.length()}]")
                if (isCurrentCapture(generation)) toast("Screenshot saved[${jpegFile.length()}]")
            } finally {
                compressed.recycle()
            }
        } catch (error: Exception) {
            LogContext.log.e(TAG, "Saving screenshot failed.", error)
        } finally {
            bitmap.recycle()
        }
    }
}

interface ScreenDataUpdateListener {
    fun onUpdate(data: ByteArray, flags: Int, presentationTimeUs: Long)
}

/** Cleans both owners without replacing [startFailure] when either cleanup step also fails. */
internal fun releaseProcessorAfterStartFailure(
    processor: ScreenProcessor,
    mediaProjection: MediaProjection,
    startFailure: Throwable,
) {
    try {
        processor.onRelease()
    } catch (releaseFailure: Throwable) {
        if (releaseFailure !== startFailure) startFailure.addSuppressed(releaseFailure)
    }
    try {
        mediaProjection.stop()
    } catch (stopFailure: Throwable) {
        if (stopFailure !== startFailure) startFailure.addSuppressed(stopFailure)
    }
}
