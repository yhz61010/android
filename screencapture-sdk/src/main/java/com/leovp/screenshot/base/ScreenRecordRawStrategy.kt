package com.leovp.screenshot.base

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import com.leovp.androidbase.utils.log.LogContext

/**
 * Author: Michael Leo
 * Date: 21-3-16 上午11:37
 */
class ScreenRecordRawStrategy private constructor(private val builder: Builder) : ScreenProcessor {

    private var virtualDisplay: VirtualDisplay? = null

    private var imageReader: ImageReader? = null

    private var videoEncoderLoop = false
    private var videoDataSendThread: HandlerThread? = null
    private var videoDataSendHandler: Handler? = null

    private val imageReaderThread: HandlerThread = HandlerThread("srrs-h").apply { start() }
    private val imageReaderHandler: Handler = Handler(imageReaderThread.looper)

    class Builder(
        val width: Int,
        val height: Int,
        val dpi: Int,
        val mediaProjection: MediaProjection?,
        val screenDataListener: ScreenDataListener
    ) {
        var fps = 20F
            private set

        fun setFps(fps: Float) = apply { this.fps = fps }

        fun build(): ScreenRecordRawStrategy {
            LogContext.log.i(TAG, "width=$width height=$height dpi=$dpi fps=$fps")
            return ScreenRecordRawStrategy(this)
        }
    }

    @SuppressLint("InlinedApi")
    @Throws(Exception::class)
    override fun onInit() {
        imageReader = ImageReader.newInstance(builder.width, builder.height, ImageFormat.YUV_420_888, IMAGE_BUFFER_SIZE).apply {
            setOnImageAvailableListener({ reader ->
                val image: Image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                runCatching { builder.screenDataListener.onDataUpdate(image) }.also { image.close() }
            }, imageReaderHandler)
        }
        virtualDisplay = builder.mediaProjection!!.createVirtualDisplay(
            "screen-record", builder.width, builder.height, builder.dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, imageReader!!.surface, null, null
        )

        initHandler()
    }

    override fun onRelease() {
        if (!videoEncoderLoop) {
            return
        }
        LogContext.log.i(TAG, "onRelease()")
        onStop()
        virtualDisplay?.release()
    }

    override fun onStop() {
        if (!videoEncoderLoop) {
            return
        }
        LogContext.log.i(TAG, "onStop()")
        videoEncoderLoop = false
        releaseHandler()
        builder.mediaProjection?.stop()
    }

    override fun onStart() {
        if (videoEncoderLoop) {
            LogContext.log.e(TAG, "Your previous recording is not finished. Stop it automatically.")
            onStop()
        }

        LogContext.log.i(TAG, "onStart()")
        initHandler()
        videoEncoderLoop = true
    }

    private fun initHandler() {
        releaseHandler()
        videoDataSendThread = HandlerThread("scr-rec-send")
        videoDataSendThread!!.start()
        videoDataSendHandler = Handler(videoDataSendThread!!.looper)
    }

    private fun releaseHandler() {
        videoDataSendHandler?.removeCallbacksAndMessages(null)
        videoDataSendThread?.interrupt()
        videoDataSendThread?.quitSafely()
    }

    companion object {
        private const val TAG = "ScrRec"

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3
    }
}