package com.leovp.screencapture.screenrecord.base.strategies

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import com.leovp.bytes.toHexString
import com.leovp.image.createBitmap
import com.leovp.log.LogContext
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午1:54
 */
class ScreenRecordMediaCodecStrategy private constructor(private val builder: Builder) :
    ScreenProcessor {

    private var virtualDisplay: VirtualDisplay? = null

    var h26xEncoder: MediaCodec? = null
        private set
    private var videoEncoderLoop = AtomicBoolean(false)
    private var videoDataSendThread: HandlerThread? = null
    private var videoDataSendHandler: Handler? = null

    @Suppress("WeakerAccess")
    var vpsSpsPpsBuf: ByteArray? = null
        private set

    private var outputFormat: MediaFormat? = null

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        private var beginPTS: Long = 0L

        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
            //            val inputBuffer = codec.getInputBuffer(inputBufferId)
            //            // fill inputBuffer with valid data
            //
            //            codec.queueInputBuffer(inputBufferId,)
            //            LogContext.log.d(TAG, "onInputBufferAvailable inputBufferId=$inputBufferId")
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            outputBufferId: Int,
            info: MediaCodec.BufferInfo
        ) {
            runCatching {
                //                    LogContext.log.d(TAG, "onOutputBufferAvailable outputBufferId=$outputBufferId")
                val outputBuffer = codec.getOutputBuffer(outputBufferId)
                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
                // bufferFormat is equivalent to member variable outputFormat
                // outputBuffer is ready to be processed or rendered.
                outputBuffer?.let {
                    val calcPTS = if (beginPTS == 0L) {
                        beginPTS = info.presentationTimeUs
                        0
                    } else {
                        info.presentationTimeUs - beginPTS
                    }
                    //                    LogContext.log.w(
                    //                        TAG, "ori presentationTimeUs=${currentPTS / 1000 / 1000} new=${info.presentationTimeUs} " +
                    //                                "currentTimeMillis=${System.currentTimeMillis()} " +
                    //                                "elapsedRealtime=${SystemClock.elapsedRealtime()} " +
                    //                                "currentThreadTimeMillis=${SystemClock.currentThreadTimeMillis()} " +
                    //                                "uptimeMillis=${SystemClock.uptimeMillis()}"
                    //                    )
                    onSendAvcFrame(it, info.flags, info.size, calcPTS)
                }
                codec.releaseOutputBuffer(outputBufferId, false)
            }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            //            LogContext.log.d(TAG, "onOutputFormatChanged format=${format.toJsonString()}")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LogContext.log.d(TAG, "onError error=${e.message}", e)
        }
    }

    class Builder(
        var width: Int,
        var height: Int,
        val dpi: Int,
        val mediaProjection: MediaProjection?,
        val screenDataListener: ScreenDataListener
    ) {
        var encodeType: EncodeType = EncodeType.H264
            private set
        var fps = 20F
            private set
        var bitrate = width * height
            private set
        var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            private set
        var keyFrameRate = 20
            private set
        var iFrameInterval = 1
            private set
        var useGoogleEncoder = false
            private set

        fun setEncodeType(encodeType: EncodeType) = apply { this.encodeType = encodeType }
        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setKeyFrameRate(keyFrameRate: Int) = apply { this.keyFrameRate = keyFrameRate }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }
        fun setGoogleEncoder(useGoogleEncoder: Boolean) =
            apply { this.useGoogleEncoder = useGoogleEncoder }

        fun build(): ScreenRecordMediaCodecStrategy {
            LogContext.log.i(
                TAG,
                "encodeType=$encodeType width=$width height=$height dpi=$dpi fps=$fps " +
                    "bitrate=$bitrate bitrateMode=$bitrateMode keyFrameRate=$keyFrameRate " +
                    "iFrameInterval=$iFrameInterval"
            )
            return ScreenRecordMediaCodecStrategy(this)
        }
    }

    /**
     * This method must be called on main thread.
     */
    @SuppressLint("InlinedApi")
    override fun onInit() {
        val format = MediaFormat.createVideoFormat(
            when (builder.encodeType) {
                EncodeType.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                EncodeType.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
            },
            builder.width, builder.height
        )

        with(format) {
            // MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            // MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, builder.bitrate)
            setInteger(MediaFormat.KEY_BITRATE_MODE, builder.bitrateMode)
            setInteger(MediaFormat.KEY_FRAME_RATE, builder.keyFrameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, builder.iFrameInterval)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInteger(MediaFormat.KEY_LATENCY, 0)
            }
            // Set the encoder priority to realtime.
            setInteger(MediaFormat.KEY_PRIORITY, 0x00)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Actually, this key has been used in Android 6.0+ although it just has been opened as of Android 10.
                @Suppress("unchecked")
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, builder.fps)
            }
            //            val profileLevelPair = CodecUtil.getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC)
            // //                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline }
            // //                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
            // //                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain }
            //                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline }
            // //                .maxByOrNull { it.profile }
            //            val usedProfile = profileLevelPair?.profile ?: MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            //            val usedLevel = profileLevelPair?.level ?: MediaCodecInfo.CodecProfileLevel.AVCLevel4
            //            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            //                LogContext.log.w(TAG, "KEY_PROFILE: $usedProfile")
            //                setInteger(MediaFormat.KEY_PROFILE, usedProfile)
            //            } else {
            //                LogContext.log.w(TAG, "KEY_PROFILE static: AVCProfileBaseline")
            //                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            //            }
            //            setInteger(MediaFormat.KEY_COMPLEXITY, bitrateMode)
            //            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //                // You must specify KEY_LEVEL on Android 6.0+
            //                // AVCLevel51
            //                // AVCLevel4
            //                LogContext.log.w(TAG, "KEY_LEVEL: $usedLevel")
            //                setInteger(MediaFormat.KEY_LEVEL, usedLevel)
            //            } else {
            //                LogContext.log.w(TAG, "KEY_LEVEL static: AVCLevel4")
            //                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
            //            }
        }
        h26xEncoder = if (builder.useGoogleEncoder) {
            when (builder.encodeType) {
                EncodeType.H264 -> MediaCodec.createByCodecName("OMX.google.h264.encoder")
                EncodeType.H265 -> {
                    val hevcEncoderName = getHevcCodec(encoder = true)[0].name
                    LogContext.log.i(TAG, "hevcEncoderName=$hevcEncoderName")
                    MediaCodec.createByCodecName(hevcEncoderName)
                } // "c2.android.hevc.encoder"
            }
        } else {
            when (builder.encodeType) {
                EncodeType.H264 -> MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                EncodeType.H265 -> MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            }
        }
        h26xEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        outputFormat = h26xEncoder?.outputFormat // option B
        h26xEncoder?.setCallback(mediaCodecCallback)
        val surface = h26xEncoder!!.createInputSurface()
        virtualDisplay = builder.mediaProjection!!.createVirtualDisplay(
            "screen-record",
            builder.width,
            builder.height,
            builder.dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            surface,
            null,
            null
        )

        initHandler()
    }

    override fun onRelease() {
        if (!videoEncoderLoop.get()) {
            return
        }
        LogContext.log.i(TAG, "onRelease()")
        onStop()
        h26xEncoder?.release()
        virtualDisplay?.release()
    }

    override fun onStop() {
        if (!videoEncoderLoop.get()) {
            return
        }
        LogContext.log.i(TAG, "onStop()")
        videoEncoderLoop.set(false)
        releaseHandler()
        h26xEncoder?.stop()
        builder.mediaProjection?.stop()
    }

    override fun onStart() {
        if (videoEncoderLoop.get()) {
            LogContext.log.e(TAG, "Your previous recording is not finished. Stop it automatically.")
            onStop()
        }

        LogContext.log.i(TAG, "onStart()")
        initHandler()
        h26xEncoder?.start() ?: throw Exception("You must initialize Video Encoder.")
        videoEncoderLoop.set(true)
    }

    override fun getVideoSize(): Size = Size(builder.width, builder.height)

    /**
     * This method must be called on main thread.
     */
    override fun changeOrientation() {
        runCatching { virtualDisplay?.release() }.onFailure { it.printStackTrace() }
        virtualDisplay = null
        runCatching { h26xEncoder?.release() }.onFailure { it.printStackTrace() }
        h26xEncoder = null
        //        mediaCodecCallback = null
        runCatching { releaseHandler() }.onFailure { it.printStackTrace() }

        videoEncoderLoop.set(false)
        val oriWidth = builder.width
        builder.width = builder.height
        builder.height = oriWidth
        LogContext.log.w(TAG, "Size after orientation: ${builder.width}x${builder.height}")

        //        mediaCodecCallback = createMediaCodecCallback()
        onInit()
        onStart()
    }

    private fun initHandler() {
        releaseHandler()
        videoDataSendThread = HandlerThread("scr-rec-send").apply { start() }
        videoDataSendHandler = Handler(videoDataSendThread!!.looper)
    }

    private fun releaseHandler() {
        videoDataSendHandler?.removeCallbacksAndMessages(null)
        videoDataSendHandler = null
        videoDataSendThread?.quitSafely()
        videoDataSendThread = null
    }

    private fun onSendAvcFrame(
        bb: ByteBuffer,
        flags: Int,
        bufferSize: Int,
        presentationTimeUs: Long
    ) {
        //        var naluIndex = 4
        //        if (bb[2].toInt() == 0x01) {
        //            naluIndex = 3
        //        }

        //        val naluType = H264Util.getNaluType(bb.get(naluIndex))
        val bytes = ByteArray(bufferSize)
        bb.get(bytes)

        if (MediaCodec.BUFFER_FLAG_CODEC_CONFIG == flags) {
            vpsSpsPpsBuf = bytes.copyOf()
            when (builder.encodeType) {
                EncodeType.H264 -> LogContext.log.w(
                    TAG,
                    "Found SPS/PPS=${vpsSpsPpsBuf?.toHexString()}"
                )
                EncodeType.H265 -> LogContext.log.w(
                    TAG,
                    "Found VPS/SPS/PPS=${vpsSpsPpsBuf?.toHexString()}"
                )
            }
        }

        //        val naluTypeStr = when (naluType) {
        //            NAL_SPS -> {
        //                spsPpsBuf = bytes.copyOf()
        //                "SPS"
        //            }
        //            NAL_PPS -> {
        //                "PPS"
        //            }
        //            NAL_SLICE_IDR -> {
        //                "I"
        //            }
        //            else -> {
        //                "P"
        //            }
        //        }
        //        if (BuildConfig.DEBUG) {
        //            LogContext.log.d(
        //                TAG,
        //                "$naluTypeStr:Len=${bytes.size}${if (NAL_SPS == naluType) "[" + bytes.toHexStringLE() + "]" else ""}"
        //            )
        //        }

        videoDataSendHandler?.post {
            builder.screenDataListener.onDataUpdate(
                bytes,
                flags,
                presentationTimeUs
            )
        }
    }

    @SuppressLint("WrongConstant")
    @Synchronized
    override fun takeScreenshot(width: Int?, height: Int?, result: (bitmap: Bitmap) -> Unit) {
        val finalWidth = width ?: builder.width
        val finalHeight = height ?: builder.height
        // TODO PixelFormat.RGBA_8888 is a wrong constant? Using ImageFormat instead.
        val imageReader: ImageReader =
            ImageReader.newInstance(finalWidth, finalHeight, PixelFormat.RGBA_8888, 3)
        val virtualDisplayForImageReader: VirtualDisplay? =
            builder.mediaProjection!!.createVirtualDisplay(
                "screen-record",
                finalWidth,
                finalHeight,
                builder.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                imageReader.surface,
                null,
                null
            )
        imageReader.setOnImageAvailableListener({ reader ->
            //            LogContext.log.e(TAG, "takeScreenshotFlag=${takeScreenshotFlag.get()}")
            val image: Image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            runCatching {
                result.invoke(image.createBitmap())

                imageReader.setOnImageAvailableListener(null, videoDataSendHandler)
                runCatching { imageReader.close() }.onFailure { it.printStackTrace() }
                runCatching { virtualDisplayForImageReader?.release() }.onFailure { it.printStackTrace() }
            }.also { image.close() }
        }, null)
    }

    enum class EncodeType {
        H264,
        H265
    }

    companion object {
        private const val TAG = "ScrRec"
    }
}
