package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.leovp.androidbase.exts.android.getBaseDirString
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.media.VideoUtil
import com.leovp.leoandroidbaseutil.basic_components.BasicFragment
import com.leovp.lib_image.compressBitmap
import com.leovp.lib_image.writeToFile
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import com.leovp.screencapture.screenrecord.ScreenCapture
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordMediaCodecStrategy
import io.karn.notify.Notify
import okhttp3.internal.closeQuietly
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Android Q+(Android 10+) MediaProjection must be used in Service and with android:foregroundServiceType="mediaProjection" permission.
 * Example:
 * <pre>
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 *
 * <service
 *  android:name=".MediaProjectionService"
 *  tools:targetApi="q"
 *  android:enabled="true"
 *  android:exported="false"
 *  android:foregroundServiceType="mediaProjection" />
 * </pre>
 */

class MediaProjectionService : Service() {

    companion object {
        val VIDEO_ENCODE_TYPE = ScreenRecordMediaCodecStrategy.EncodeType.H265
    }

    var outputH26xFile = false
    private lateinit var videoH26xFile: File
    private lateinit var videoH26xOs: BufferedOutputStream

    private var resultCode = -1
    private lateinit var data: Intent

    private lateinit var serviceThread: HandlerThread
    private lateinit var serviceHandler: Handler
    private val binder = CustomBinder()
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    var screenProcessor: ScreenProcessor? = null
        private set

    val vpsSpsPps get() = run { (screenProcessor as? ScreenRecordMediaCodecStrategy)?.vpsSpsPpsBuf }

    var screenDataUpdateListener: ScreenDataUpdateListener? = null

    private val screenDataListener = object : ScreenDataListener {
        override fun onDataUpdate(buffer: Any, flags: Int, presentationTimeUs: Long) {
            val data = buffer as ByteArray
            if (outputH26xFile) {
                try {
                    videoH26xOs.write(data)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
//            LogContext.log.e("Data[${buffer.size}]≈${buffer.size*1.0f/1024/1024} flag=$flags")
            screenDataUpdateListener?.onUpdate(data, flags, presentationTimeUs)

            // Bitmap for screenshot
//            val file = FileUtil.createImageFile(app, "bmp")
//            ImageUtil.writeBitmapToFile(file, buffer as Bitmap, 90)
        }
    }

    override fun onCreate() {
        LogContext.log.i(ITAG, "=====> onCreate <=====")
        super.onCreate()
        serviceThread = HandlerThread("service-thread")
        serviceThread.start()
        serviceHandler = Handler(serviceThread.looper)
        startForeground()
    }

    private fun setDebugInfo() {
        if (outputH26xFile) {
            try {
                val baseFolder = File(getExternalFilesDir(null)?.absolutePath + File.separator + "leo-media")
                LogContext.log.w(ITAG, "Output H.26x file path=$baseFolder")
                baseFolder.mkdirs()
                videoH26xFile = File(
                    baseFolder,
                    "screen" + when (VIDEO_ENCODE_TYPE) {
                        ScreenRecordMediaCodecStrategy.EncodeType.H264 -> ".h264"
                        ScreenRecordMediaCodecStrategy.EncodeType.H265 -> ".h265"
                    }
                )
                videoH26xOs = BufferedOutputStream(FileOutputStream(videoH26xFile))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        LogContext.log.i(ITAG, "=====> onBind <=====")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogContext.log.i(ITAG, "=====> onStartCommand <=====")
        return START_STICKY
    }

    override fun onRebind(intent: Intent?) {
        LogContext.log.i(ITAG, "=====> onRebind <=====")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        LogContext.log.w(ITAG, "=====> onUnbind <=====")
        stopScreenShare()
        return false
    }

    override fun onDestroy() {
        LogContext.log.w(ITAG, "=====> onDestroy <=====")
        super.onDestroy()
    }

    private fun startForeground() {
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
                    Intent(this@MediaProjectionService, BasicFragment::class.java),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

                )
                cancelOnClick = false
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(10086, notify.asBuilder().build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(10086, notify.asBuilder().build())
        }
    }

    /**
     * This method must be called before `startScreenShare()`
     */
    fun setData(resultCode: Int, data: Intent) {
        this.resultCode = resultCode
        this.data = data
    }

    // ==========================================

    inner class CustomBinder : Binder() {
        val service: MediaProjectionService get() = this@MediaProjectionService
    }

    /**
     * This method must be following `setData()` method
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun startScreenShare(setting: ScreenShareSetting) {
        LogContext.log.i(ITAG, "startScreenShare: ${setting.toJsonString()}")
        setDebugInfo()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        screenProcessor = ScreenCapture.Builder(
            setting.width, // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
            setting.height, // 800 1024 1280
            setting.dpi,
            mediaProjection,
            // BY_IMAGE_2_H26x
            // BY_MEDIA_CODEC
            // BY_RAW_BMP
            ScreenCapture.BY_MEDIA_CODEC,
            screenDataListener
        )
            .setEncodeType(VIDEO_ENCODE_TYPE)
            .setGoogleEncoder(false)
            .setFps(setting.fps)
            // For H26x and x264
            .setBitrate(setting.bitrate)
            // For H26x
            .setBitrateMode(setting.bitrateMode)
            .setKeyFrameRate(setting.keyFrameRate)
            .setIFrameInterval(setting.iFrameInterval) // 20
            // For bitmap
//          .setSampleSize(2)
            .build().apply {
                onInit()
                onStart()
            }
    }

    fun stopScreenShare() {
        LogContext.log.w(ITAG, "stopScreenShare")
        if (outputH26xFile) {
            try {
                videoH26xOs.flush()
                videoH26xOs.closeQuietly()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        serviceHandler.post {
            LogContext.log.w(ITAG, "screenProcessor onStop()")
            screenProcessor?.onStop()
        }
    }

    fun onReleaseScreenShare() {
        LogContext.log.w(ITAG, "onReleaseScreenShare()")
        stopScreenShare()
        serviceHandler.post {
            LogContext.log.w(ITAG, "onReleaseScreenShare onRelease()")
            screenProcessor?.onRelease()
        }
    }

    @Suppress("WeakerAccess")
    fun triggerIFrame() {
        LogContext.log.w(ITAG, "triggerIFrame()")
        val encoder = (screenProcessor as? ScreenRecordMediaCodecStrategy)?.h26xEncoder
        encoder?.let { VideoUtil.sendIdrFrameByManual(it) }
    }

    fun takeScreenshot(width: Int?, height: Int?) {
        LogContext.log.w("Prepare to call takeScreenshot($width, $height)...")
        screenProcessor?.takeScreenshot(width, height) { bmp ->
            val compressedBmp = bmp.compressBitmap()
            bmp.recycle()
            val jpegFile = File(this@MediaProjectionService.getBaseDirString("screenshot"), "screenshot.jpg")
            compressedBmp.writeToFile(jpegFile)
            compressedBmp.recycle()
            LogContext.log.w("onScreenshot[${jpegFile.length()}]")
            toast("Screenshot saved[${jpegFile.length()}]")
        }
    }
}

interface ScreenDataUpdateListener {
    fun onUpdate(data: ByteArray, flags: Int, presentationTimeUs: Long)
}