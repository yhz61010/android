package com.ho1ho.screenshot.base

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.ho1ho.androidbase.exts.exception
import com.ho1ho.androidbase.exts.toJsonString
import com.ho1ho.androidbase.utils.CLog
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
//            CLog.d(TAG, "onInputBufferAvailable inputBufferId=$inputBufferId")
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
//            CLog.d(TAG, "onOutputBufferAvailable outputBufferId=$outputBufferId")
            val outputBuffer = codec.getOutputBuffer(outputBufferId)
            // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
            // bufferFormat is equivalent to member variable outputFormat
            // outputBuffer is ready to be processed or rendered.
            outputBuffer?.let { onSendAvcFrame(it, info.flags, info.size) }
            codec.releaseOutputBuffer(outputBufferId, false)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
//            CLog.d(TAG, "onOutputFormatChanged format=${format.toJsonString()}")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            CLog.d(TAG, "onError error=${e.message}", e)
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

        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setKeyFrameRate(keyFrameRate: Int) = apply { this.keyFrameRate = keyFrameRate }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }

        fun build(): ScreenRecordMediaCodecStrategy {
            CLog.i(
                TAG,
                "width=$width height=$height dpi=$dpi fps=$fps bitrate=$bitrate bitrateMode=$bitrateMode keyFrameRate=$keyFrameRate iFrameInterval=$iFrameInterval"
            )
            return ScreenRecordMediaCodecStrategy(this)
        }
    }

    @Throws(Exception::class)
    override fun onInit() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, builder.width, builder.height)
        with(format) {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, builder.bitrate)
            setInteger(MediaFormat.KEY_BITRATE_MODE, builder.bitrateMode)
            setInteger(MediaFormat.KEY_FRAME_RATE, builder.keyFrameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, builder.iFrameInterval)
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
//        h264Encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        h264Encoder = MediaCodec.createByCodecName("OMX.google.h264.encoder")
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
        CLog.i(TAG, "onRelease()")
        onStop()
        h264Encoder?.release()
        virtualDisplay?.release()
    }

    override fun onStop() {
        if (!videoEncoderLoop) {
            return
        }
        CLog.i(TAG, "onStop()")
        videoEncoderLoop = false
        releaseHandler()
        h264Encoder?.stop()
        builder.mediaProjection?.stop()
    }

    override fun onStart() {
        if (videoEncoderLoop) {
            CLog.e(TAG, "Your previous recording is not finished. Stop it automatically.")
            onStop()
        }

        CLog.i(TAG, "onStart()")
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

    private fun onSendAvcFrame(bb: ByteBuffer, flags: Int, bufferSize: Int) {
//        var naluIndex = 4
//        if (bb[2].toInt() == 0x01) {
//            naluIndex = 3
//        }

//        val naluType = H264Util.getNaluType(bb.get(naluIndex))
        val bytes = ByteArray(bufferSize)
        bb.get(bytes)

        if (MediaCodec.BUFFER_FLAG_CODEC_CONFIG == flags) {
            spsPpsBuf = bytes.copyOf()
            CLog.w(TAG, "Found SPS/PPS=${spsPpsBuf?.toJsonString()}")
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
//            CLog.d(
//                TAG,
//                "$naluTypeStr:Len=${bytes.size}${if (NAL_SPS == naluType) "[" + bytes.toHexStringLE() + "]" else ""}"
//            )
//        }

        videoDataSendHandler?.post {
            builder.screenDataListener.onDataUpdate(bytes)
        }
    }

    companion object {
        private const val TAG = "ScrRec"

        @Suppress("unused")
        const val NAL_SLICE = 1

        //        const val NAL_SLICE_DPA = 2
        //        const val NAL_SLICE_DPB = 3
        //        const val NAL_SLICE_DPC = 4
        @Suppress("unused")
        const val NAL_SLICE_IDR = 5

        //        const val NAL_SEI = 6
        @Suppress("unused")
        const val NAL_SPS = 7

        @Suppress("unused")
        const val NAL_PPS = 8
        //        const val NAL_AUD = 9
        //        const val NAL_FILLER = 12
    }
}