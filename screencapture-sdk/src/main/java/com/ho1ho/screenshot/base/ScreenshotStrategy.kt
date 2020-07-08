package com.ho1ho.screenshot.base

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.media.ImageUtil
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午1:53
 */
class ScreenshotStrategy private constructor(private val builder: Builder) : ScreenProcessor {

    companion object {
        private const val TAG = "ScrSt"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenshotThread: HandlerThread? = null
    private var screenshotHandler: Handler? = null
    private var previousDisplayTime = 0L

    class Builder(
        val width: Int,
        val height: Int,
        val dpi: Int,
        val mediaProjection: MediaProjection?,
        val screenDataListener: ScreenDataListener
    ) {
        @Suppress("WeakerAccess")
        var fps = 20F
            private set
        var sampleSize = 1
            private set

        var displayIntervalInMs: Int = 0
            private set

        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setSampleSize(sample: Int) = apply { this.sampleSize = sample }

        fun build(): ScreenshotStrategy {
            displayIntervalInMs = (1000 / (fps + 1)).toInt()
            LLog.w(TAG, "width=$width height=$height dpi=$dpi fps=$fps sampleSize=$sampleSize")
            return ScreenshotStrategy(this)
        }

    }

    @Throws(IOException::class)
    override fun onInit() {
        imageReader = ImageReader.newInstance(builder.width, builder.height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = builder.mediaProjection!!.createVirtualDisplay(
            "screenshot",
            builder.width, builder.height, builder.dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, // VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
            imageReader!!.surface, null, null
        )
    }

    override fun onStart() {
        previousDisplayTime = SystemClock.elapsedRealtime()
        try {
            screenshotThread = HandlerThread("screenshot-thread")
            screenshotThread!!.start()
            screenshotHandler = Handler(screenshotThread!!.looper)
            imageReader?.setOnImageAvailableListener(mOnImageAvailableListener, screenshotHandler)
        } catch (e: IOException) {
            LLog.e(TAG, "onStart error", e)
        }
    }

    override fun onRelease() {
        onStop()
        virtualDisplay?.release()
        imageReader?.close()
    }

    override fun onStop() {
        screenshotHandler?.removeCallbacksAndMessages(null)
        screenshotThread?.quitSafely()
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        if (image == null || screenshotHandler == null) return@OnImageAvailableListener

        if ((SystemClock.elapsedRealtime() - previousDisplayTime) < builder.displayIntervalInMs) {
            image.close()
            LLog.d(TAG, "Ignore image due to fps")
            return@OnImageAvailableListener
        }

        previousDisplayTime = SystemClock.elapsedRealtime()

        screenshotHandler!!.post {
            try {
                val width = image.width
                val height = image.height
                LLog.v(TAG, "Image width=$width height=$height")

//                var st = SystemClock.elapsedRealtimeNanos()
                val bitmap = ImageUtil.createBitmapFromImage(image)
//                CLog.i(TAG, "createBitmapFromImage cost=${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us")
//                st = SystemClock.elapsedRealtimeNanos()
                val targetBitmap: Bitmap
                if (builder.sampleSize != 1) {
                    targetBitmap = ImageUtil.compressBitmap(bitmap, builder.sampleSize)
                    bitmap.recycle()
                } else {
                    targetBitmap = bitmap
                }
//                CLog.i(TAG, "compressBitmap cost=${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us")
                builder.screenDataListener.onDataUpdate(targetBitmap)
                targetBitmap.recycle()
//                FileUtil.writeBitmapToFile(bitmap, 1)
            } catch (e: Exception) {
                LLog.e(TAG, "screenshotHandler error=${e.message}")
            } finally {
                image.close()
            }
        }
    }
}