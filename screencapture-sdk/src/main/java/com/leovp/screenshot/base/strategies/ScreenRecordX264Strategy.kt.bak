package com.leovp.screenshot.base

import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.leovp.androidbase.annotations.NotImplemented
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.media.YuvUtil
import com.leovp.x264.X264EncodeResult
import com.leovp.x264.X264Encoder
import com.leovp.x264.X264InitResult
import com.leovp.x264.X264Params
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

    private var x264Encoder: X264Encoder? = null
    private var params: X264Params? = null

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
            LogContext.log.w(TAG, "width=$width height=$height dpi=$dpi fps=$fps bitrate=$bitrate")
            return ScreenRecordX264Strategy(this)
        }

    }

    @Throws(IOException::class)
    override fun onInit() {
        timespan = (builder.bitrate / builder.fps).toInt()

        x264Encoder = X264Encoder()
        params = X264Params().apply {
            width = builder.width
            height = builder.height
            bitrate = builder.bitrate
            fps = builder.fps.toInt()
            gop = fps * 2
            preset = "ultrafast"
            profile = "baseline"
        }

        val initRs: X264InitResult = x264Encoder!!.initEncoder(params)
        if (initRs.err == 0) {
            val sps = initRs.sps
            val pps = initRs.pps
            builder.screenDataListener.onDataUpdate(sps + pps, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
        }

        imageReader = ImageReader.newInstance(builder.width, builder.height, ImageFormat.YUV_420_888, 2)
        virtualDisplay = builder.mediaProjection!!.createVirtualDisplay(
            "x264",
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
            LogContext.log.e(TAG, "onStart error", e)
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
            LogContext.log.d(TAG, "Ignore image due to fps")
            return@OnImageAvailableListener
        }
        previousDisplayTime = SystemClock.elapsedRealtime()

        val encodedFrame: X264EncodeResult = x264Encoder!!.encodeFrame(YuvUtil.getBytesFromImage(image), X264Params.CSP_NV21, previousDisplayTime)
        if (encodedFrame.err == 0) {
            builder.screenDataListener.onDataUpdate(encodedFrame.data, if (encodedFrame.isKey) MediaCodec.BUFFER_FLAG_KEY_FRAME else -1)
        }

        image.close()
    }
}