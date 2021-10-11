package com.leovp.screenshot.base.strategies

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import com.leovp.log_sdk.LogContext
import com.leovp.screenshot.base.ScreenDataListener
import com.leovp.screenshot.base.ScreenProcessor
import com.leovp.screenshot.util.createBitmap
import java.util.*

/**
 * Author: Michael Leo
 * Date: 21-3-16 上午11:37
 */
class ScreenRecordRawBmpStrategy private constructor(private val builder: Builder) : ScreenProcessor {

    private var virtualDisplay: VirtualDisplay? = null

    private var imageReader: ImageReader? = null

    private var videoEncoderLoop = false

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

        fun build(): ScreenRecordRawBmpStrategy {
            LogContext.log.i(TAG, "width=$width height=$height dpi=$dpi fps=$fps")
            return ScreenRecordRawBmpStrategy(this)
        }
    }

    @SuppressLint("InlinedApi", "WrongConstant")
    @Throws(Exception::class)
    override fun onInit() {
        imageReader = ImageReader.newInstance(builder.width, builder.height, PixelFormat.RGBA_8888, IMAGE_BUFFER_SIZE).apply {
            setOnImageAvailableListener({ reader ->
                runCatching {
                    val image: Image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val bitmap = image.createBitmap()
                    image.close()
                    builder.screenDataListener.onDataUpdate(bitmap)
                }.onFailure { it.printStackTrace() }
            }, imageReaderHandler)
        }
        virtualDisplay = builder.mediaProjection!!.createVirtualDisplay(
            "screen-record", builder.width, builder.height, builder.dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, imageReader!!.surface, null, null
        )
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
        builder.mediaProjection?.stop()
    }

    override fun onStart() {
        if (videoEncoderLoop) {
            LogContext.log.e(TAG, "Your previous recording is not finished. Stop it automatically.")
            onStop()
        }

        LogContext.log.i(TAG, "onStart()")
        videoEncoderLoop = true
    }

    companion object {
        private const val TAG = "ScrRec"

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 2
    }
}