package com.ho1ho.leoandroidbaseutil.ui.sharescreen.master

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.toJsonString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.media.H264Util
import com.ho1ho.androidbase.utils.notification.NotificationUtil
import com.ho1ho.leoandroidbaseutil.MainActivity
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.screenshot.ScreenCapture
import com.ho1ho.screenshot.base.ScreenDataListener
import com.ho1ho.screenshot.base.ScreenProcessor
import com.ho1ho.screenshot.base.ScreenRecordMediaCodecStrategy
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

    var outputH264File = false
    private lateinit var videoH264File: File
    private lateinit var videoH264Os: BufferedOutputStream

    private var resultCode = -1
    private lateinit var data: Intent

    private lateinit var serviceThread: HandlerThread
    private lateinit var serviceHandler: Handler
    private val binder = CustomBinder()
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    private var screenProcessor: ScreenProcessor? = null

    val spsPps get() = run { (screenProcessor as? ScreenRecordMediaCodecStrategy)?.spsPpsBuf }

    var screenDataUpdateListener: ScreenDataUpdateListener? = null

    private val screenDataListener = object : ScreenDataListener {
        override fun onDataUpdate(buffer: Any) {
            val buf = buffer as ByteArray
            if (outputH264File) {
                try {
                    videoH264Os.write(buf)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            screenDataUpdateListener?.onUpdate(buffer)
            // Bitmap for screenshot
//            val bitmap = buffer as Bitmap
//            FileUtil.writeBitmapToFile(bitmap, 0)
        }
    }

    override fun onCreate() {
        LLog.i(ITAG, "=====> onCreate <=====")
        super.onCreate()
        serviceThread = HandlerThread("service-thread")
        serviceThread.start()
        serviceHandler = Handler(serviceThread.looper)
        startForeground()
    }

    private fun setDebugInfo() {
        if (outputH264File) {
            try {
                val baseFolder = File(getExternalFilesDir(null)?.absolutePath + File.separator + "leo-media")
                baseFolder.mkdirs()
                videoH264File = File(baseFolder, "screen.h264")
                videoH264Os = BufferedOutputStream(FileOutputStream(videoH264File))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        LLog.i(ITAG, "=====> onBind <=====")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LLog.i(ITAG, "=====> onStartCommand <=====")
        return START_STICKY
    }

    override fun onRebind(intent: Intent?) {
        LLog.i(ITAG, "=====> onRebind <=====")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        LLog.w(ITAG, "=====> onUnbind <=====")
        stopScreenShare()
        return false
    }

    override fun onDestroy() {
        LLog.w(ITAG, "=====> onDestroy <=====")
        super.onDestroy()
    }

    private fun startForeground() {
        var channelId: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = NotificationUtil.createNotificationChannel(
                this,
                false,
                "default-foreground-notification",
                "Foreground Service"
            )
        }

        val notifyIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            SystemClock.elapsedRealtime().toInt(),
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationUtil.generateBigTextStyleNotification(
            this,
            channelId,
            R.mipmap.ic_launcher,
            R.mipmap.ic_launcher,
            getString(R.string.app_name),
            "App is running...",
            null,
            true,
            NotificationCompat.BADGE_ICON_SMALL,
            NotificationCompat.PRIORITY_DEFAULT,
            NotificationCompat.VISIBILITY_PUBLIC,
            null,
            mainPendingIntent
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(10086, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(10086, notification)
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
    fun startScreenShare(setting: ScreenShareSetting) {
        LLog.i(ITAG, "startScreenShare: ${setting.toJsonString()}")
        setDebugInfo()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        screenProcessor = ScreenCapture.Builder(
            setting.width, // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
            setting.height, // 800 1024 1280
            setting.dpi,
            mediaProjection,
            // SCREEN_CAPTURE_TYPE_IMAGE
            // SCREEN_CAPTURE_TYPE_MEDIACODEC
            // SCREEN_CAPTURE_TYPE_X264
            ScreenCapture.SCREEN_CAPTURE_TYPE_MEDIA_CODEC,
            screenDataListener
        )
            .setFps(setting.fps)
            // For H264 and x264
            .setBitrate(setting.bitrate)
            // For H264
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
        LLog.w(ITAG, "stopScreenShare")
        if (outputH264File) {
            try {
                videoH264Os.flush()
                videoH264Os.closeQuietly()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        serviceHandler.post {
            LLog.w(ITAG, "screenProcessor onStop()")
            screenProcessor?.onStop()
        }
    }

    fun onReleaseScreenShare() {
        LLog.w(ITAG, "onReleaseScreenShare()")
        stopScreenShare()
        serviceHandler.post {
            LLog.w(ITAG, "onReleaseScreenShare onRelease()")
            screenProcessor?.onRelease()
        }
    }

    @Suppress("WeakerAccess")
    fun triggerIFrame() {
        LLog.w(ITAG, "triggerIFrame()")
        val encoder = (screenProcessor as? ScreenRecordMediaCodecStrategy)?.h264Encoder
        encoder?.let { H264Util.sendIdrFrameByManual(it) }
    }
}

interface ScreenDataUpdateListener {
    fun onUpdate(data: Any)
}