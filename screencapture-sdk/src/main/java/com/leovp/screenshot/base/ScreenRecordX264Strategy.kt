package com.leovp.screenshot.base

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.leovp.androidbase.annotations.NotImplemented
import com.leovp.androidbase.utils.LLog
import com.leovp.androidbase.utils.media.YuvUtil
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-6-8 下午2:53
 */
@NotImplemented("This is not used in project. Just for debug.")
class ScreenRecordX264Strategy private constructor(private val builder: Builder) : ScreenProcessor {

    companion object {
        private const val TAG = "ScrSt"
    }

    private var timespan: Int = 0
    private var time: Long = 0

    //    private var x264: x264Encoder? = null
//    private val x264DataListener: x264Encoder.DataUpdateListener = x264Encoder.DataUpdateListener { buffer, length ->
//        try {
//            builder.screenDataListener.onDataUpdate(buffer)
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenRecThread: HandlerThread? = null
    private var screenRecHandler: Handler? = null
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
        var bitrate = width * height
            private set

        var displayIntervalInMs: Int = 0
            private set

        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }

        fun build(): ScreenRecordX264Strategy {
            displayIntervalInMs = (1000 / (fps + 1)).toInt()
            LLog.w(TAG, "width=$width height=$height dpi=$dpi fps=$fps bitrate=$bitrate")
            return ScreenRecordX264Strategy(this)
        }

    }

    @Throws(IOException::class)
    override fun onInit() {
        timespan = (builder.bitrate / builder.fps).toInt()
//        x264 = x264Encoder(x264DataListener)
//        x264!!.initX264Encode(builder.width, builder.height, builder.fps.toInt(), builder.bitrate)
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
            screenRecThread = HandlerThread("scr-x264-thread")
            screenRecThread!!.start()
            screenRecHandler = Handler(screenRecThread!!.looper)
            imageReader?.setOnImageAvailableListener(mOnImageAvailableListener, screenRecHandler)
        } catch (e: IOException) {
            LLog.e(TAG, "onStart error", e)
        }
    }

    override fun onRelease() {
        virtualDisplay?.release()
        imageReader?.close()
    }

    override fun onStop() {
        screenRecHandler?.removeCallbacksAndMessages(null)
        screenRecThread?.quitSafely()
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        if (image == null || screenRecHandler == null) return@OnImageAvailableListener
        if ((SystemClock.elapsedRealtime() - previousDisplayTime) < builder.displayIntervalInMs) {
            image.close()
            LLog.d(TAG, "Ignore image due to fps")
            return@OnImageAvailableListener
        }
        previousDisplayTime = SystemClock.elapsedRealtime()
        screenRecHandler!!.post {
            try {
                val width = image.width
                val height = image.height
                image.format
                LLog.v(TAG, "Image width=$width height=$height")

                val rgbaBytes = ByteArray(image.planes[0].buffer.remaining())
                image.planes[0].buffer.get(rgbaBytes)
                val yuvBytes = ByteArray(rgbaBytes.size * 3 / 2)
                val st = SystemClock.elapsedRealtime()
                YuvUtil.rgb2YCbCr420(rgbaBytes, yuvBytes, width, height)
                val ed = SystemClock.elapsedRealtime()
                LLog.e(TAG, "rgb2YCbCr420 cost=${ed - st} time=$time timespan=$timespan")
                time += timespan
//                x264!!.encodeData(yuvBytes, yuvBytes.size, time)
            } catch (e: Exception) {
                LLog.e(TAG, "screenRecHandler error=${e.message}")
            } finally {
                image.close()
            }
        }
    }
}