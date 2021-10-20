package com.leovp.screencapture.screenrecord.base.strategies

import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.leovp.log_sdk.LogContext
import com.leovp.min_base_sdk.exception
import com.leovp.min_base_sdk.toHexStringLE
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午1:54
 */
class ScreenRecordMediaCodecStrategy private constructor(private val builder: Builder) : ScreenProcessor {

    private var virtualDisplay: VirtualDisplay? = null

    var h264Encoder: MediaCodec? = null
        private set
    private var videoEncoderLoop = false
    private var videoDataSendThread: HandlerThread? = null
    private var videoDataSendHandler: Handler? = null

    @Suppress("WeakerAccess")
    var spsPpsBuf: ByteArray? = null
        private set

    private var outputFormat: MediaFormat? = null
    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
//            val inputBuffer = codec.getInputBuffer(inputBufferId)
//            // fill inputBuffer with valid data
//
//            codec.queueInputBuffer(inputBufferId,)
//            LogContext.log.d(TAG, "onInputBufferAvailable inputBufferId=$inputBufferId")
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
            try {
//            LogContext.log.d(TAG, "onOutputBufferAvailable outputBufferId=$outputBufferId")
                val outputBuffer = codec.getOutputBuffer(outputBufferId)
                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
                // bufferFormat is equivalent to member variable outputFormat
                // outputBuffer is ready to be processed or rendered.
                outputBuffer?.let { onSendAvcFrame(it, info.flags, info.size, info.presentationTimeUs) }
                codec.releaseOutputBuffer(outputBufferId, false)
            } catch (e: Exception) {
                e.printStackTrace()
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
        val width: Int,
        val height: Int,
        val dpi: Int,
        val mediaProjection: MediaProjection?,
        val screenDataListener: ScreenDataListener
    ) {
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

        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setKeyFrameRate(keyFrameRate: Int) = apply { this.keyFrameRate = keyFrameRate }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }
        fun setGoogleEncoder(useGoogleEncoder: Boolean) = apply { this.useGoogleEncoder = useGoogleEncoder }

        fun build(): ScreenRecordMediaCodecStrategy {
            LogContext.log.i(
                TAG,
                "width=$width height=$height dpi=$dpi fps=$fps bitrate=$bitrate bitrateMode=$bitrateMode keyFrameRate=$keyFrameRate iFrameInterval=$iFrameInterval"
            )
            return ScreenRecordMediaCodecStrategy(this)
        }
    }

    @SuppressLint("InlinedApi")
    @Throws(Exception::class)
    override fun onInit() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, builder.width, builder.height)
        with(format) {
            // MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            // MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
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
                // Actually, this key has been used in Android 6.0+. However just been opened as of Android 10.
                @Suppress("unchecked")
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, builder.fps)
            }
//            val profileLevelPair = CodecUtil.getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC)
////                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline }
////                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
////                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain }
//                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline }
////                .maxByOrNull { it.profile }
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
        h264Encoder = if (builder.useGoogleEncoder) {
            MediaCodec.createByCodecName("OMX.google.h264.encoder")
        } else {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        }
        h264Encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        outputFormat = h264Encoder?.outputFormat // option B
        h264Encoder?.setCallback(mediaCodecCallback)
        val surface = h264Encoder!!.createInputSurface()
        virtualDisplay = builder.mediaProjection!!.createVirtualDisplay(
            "screen-record", builder.width, builder.height, builder.dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null
        )

        initHandler()
    }

    override fun onRelease() {
        if (!videoEncoderLoop) {
            return
        }
        LogContext.log.i(TAG, "onRelease()")
        onStop()
        h264Encoder?.release()
        virtualDisplay?.release()
    }

    override fun onStop() {
        if (!videoEncoderLoop) {
            return
        }
        LogContext.log.i(TAG, "onStop()")
        videoEncoderLoop = false
        releaseHandler()
        h264Encoder?.stop()
        builder.mediaProjection?.stop()
    }

    override fun onStart() {
        if (videoEncoderLoop) {
            LogContext.log.e(TAG, "Your previous recording is not finished. Stop it automatically.")
            onStop()
        }

        LogContext.log.i(TAG, "onStart()")
        initHandler()
        h264Encoder?.start() ?: exception("You must initialize Video Encoder.")
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
        videoDataSendThread?.quitSafely()
    }

    private fun onSendAvcFrame(bb: ByteBuffer, flags: Int, bufferSize: Int, presentationTimeUs: Long) {
//        var naluIndex = 4
//        if (bb[2].toInt() == 0x01) {
//            naluIndex = 3
//        }

//        val naluType = H264Util.getNaluType(bb.get(naluIndex))
        val bytes = ByteArray(bufferSize)
        bb.get(bytes)

        if (MediaCodec.BUFFER_FLAG_CODEC_CONFIG == flags) {
            spsPpsBuf = bytes.copyOf()
            LogContext.log.w(TAG, "Found SPS/PPS=${spsPpsBuf?.toHexStringLE()}")
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

        videoDataSendHandler?.post { builder.screenDataListener.onDataUpdate(bytes, flags, presentationTimeUs) }
    }

    companion object {
        private const val TAG = "ScrRec"
    }
}