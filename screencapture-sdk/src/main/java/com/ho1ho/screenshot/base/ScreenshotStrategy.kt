package com.ho1ho.screenshot.base

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.ho1ho.androidbase.utils.LLog
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午1:53
 */
class ScreenshotStrategy private constructor(private val builder: Builder) : ScreenProcessor {

    companion object {
        private const val TAG = "ScrShotRec"
    }

    private var mFrameCount: Long = 0
    val queue = ConcurrentLinkedQueue<ByteArray>()

    @SuppressWarnings("unused")
    var spsPpsBytes: ByteArray? = null
        private set
    var h264Encoder: MediaCodec? = null
        private set
    private lateinit var screenshotThread: HandlerThread
    private lateinit var screenshotHandler: Handler

    private var outputFormat: MediaFormat? = null
    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
            try {
                LLog.d(TAG, "input queue[${queue.size}]")
                val inputBuffer = codec.getInputBuffer(inputBufferId)

                // fill inputBuffer with valid data
                inputBuffer?.clear()
                val data = queue.poll()?.also { inputBuffer?.put(it) }
                val pts = computePresentationTimeUs(++mFrameCount)
                LLog.d(TAG, "PTS=$pts data[${data?.size}]")
                codec.queueInputBuffer(inputBufferId, 0, data?.size ?: 0, pts, 0)
            } catch (e: Exception) {
                e.printStackTrace()
                LLog.e(TAG, "You can ignore this error safely.")
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
            LLog.d(TAG, "output queue[${queue.size}]")
            val outputBuffer = codec.getOutputBuffer(outputBufferId)
            // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
            // bufferFormat is equivalent to member variable outputFormat
            // outputBuffer is ready to be processed or rendered.
            outputBuffer?.let {
                val encodedBytes = ByteArray(info.size)
                it.get(encodedBytes)

                when (info.flags) {
                    MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
                        spsPpsBytes = encodedBytes.copyOf()
                        LLog.w(TAG, "Found SPS/PPS frame: ${spsPpsBytes!!.contentToString()}")
                    }
                    MediaCodec.BUFFER_FLAG_KEY_FRAME -> LLog.i(TAG, "Found Key Frame[" + info.size + "]")
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM -> {
                        // Do nothing
                    }
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> {
                        // Do nothing
                    }
                    else -> {
                        // Do nothing
                    }
                }
                screenshotHandler.post {
                    builder.screenDataListener.onDataUpdate(encodedBytes)
                }
            }
            codec.releaseOutputBuffer(outputBufferId, false)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
//            CLog.d(TAG, "onOutputFormatChanged format=${format.toJsonString()}")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LLog.d(TAG, "onError error=${e.message}", e)
        }
    }

    class Builder(
        val width: Int,
        val height: Int,
        val dpi: Int,
        val screenDataListener: ScreenDataListener
    ) {
        @Suppress("WeakerAccess")
        var fps = 20F
            private set
        var sampleSize = 1
            private set
        var bitrate = width * height
            private set
        var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            private set
        var keyFrameRate = 20
            private set
        var iFrameInterval = 1
            private set
        var displayIntervalInMs: Int = 0
            private set

        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setSampleSize(sample: Int) = apply { this.sampleSize = sample }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setKeyFrameRate(keyFrameRate: Int) = apply { this.keyFrameRate = keyFrameRate }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }

        fun build(): ScreenshotStrategy {
            displayIntervalInMs = (1000 / (fps + 1)).toInt()
            LLog.w(TAG, "width=$width height=$height dpi=$dpi fps=$fps sampleSize=$sampleSize")
            return ScreenshotStrategy(this)
        }

    }

    @Throws(IOException::class)
    override fun onInit() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, builder.width, builder.height)
        with(format) {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, builder.bitrate)
            setInteger(MediaFormat.KEY_BITRATE_MODE, builder.bitrateMode)
            setInteger(MediaFormat.KEY_FRAME_RATE, builder.keyFrameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, builder.iFrameInterval)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4 * 1024 * 1024)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Actually, this key has been used in Android 6.0+. However just been opened as of Android 10.
                @Suppress("unchecked")
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, builder.fps)
            }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // You must specify KEY_LEVEL on Android 6.0+
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51)
            }
        }
//        h264Encoder = MediaCodec.createByCodecName("OMX.google.h264.encoder")
        h264Encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            outputFormat = it.outputFormat // option B
            it.setCallback(mediaCodecCallback)
        }
        initHandler()
    }

    override fun onStart() {
        h264Encoder?.start()
    }

    override fun onStop() {
        h264Encoder?.stop()
    }

    override fun onRelease() {
        onStop()
        h264Encoder?.release()
        releaseHandler()
    }

    private fun initHandler() {
        screenshotThread = HandlerThread("scr-rec-send").apply { start() }
        screenshotHandler = Handler(screenshotThread.looper)
    }

    private fun releaseHandler() {
        if (::screenshotHandler.isInitialized) screenshotHandler.removeCallbacksAndMessages(null)
        if (::screenshotThread.isInitialized) screenshotThread.quitSafely();
    }

    private fun computePresentationTimeUs(frameIndex: Long): Long = (frameIndex * 1_000_000 / builder.fps).toLong()


}