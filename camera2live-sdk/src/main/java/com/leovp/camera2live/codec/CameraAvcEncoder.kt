package com.leovp.camera2live.codec

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import com.leovp.androidbase.exts.toHexadecimalString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.camera2live.listeners.CallbackListener
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Author: Michael Leo
 * Date: 20-3-24 下午5:35
 */
class CameraAvcEncoder @JvmOverloads constructor(
    private val width: Int, private val height: Int,
    private val bitrate: Int, private val frameRate: Int,
    private val iFrameInterval: Int = DEFAULT_KEY_I_FRAME_INTERVAL,
    private val bitrateMode: Int = DEFAULT_BITRATE_MODE
) {
    val queue = ConcurrentLinkedQueue<ByteArray>()
    private var dataUpdateCallback: CallbackListener? = null
    lateinit var h264Encoder: MediaCodec
        private set
    private var outputFormat: MediaFormat? = null

    init {
        initEncoder()
    }

    @SuppressWarnings("unused")
    var csd: ByteArray? = null
        private set
    private var mFrameCount: Long = 0

    @SuppressLint("InlinedApi")
    private fun initEncoder() {
        LogContext.log.i(
            TAG,
            "initEncoder width=$width height=$height bitrate=$bitrate kb/s frameRate=$frameRate bitrateMode=$bitrateMode iFrameInterval=$iFrameInterval"
        )

        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        with(mediaFormat) {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
            setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode)

            // Traffic statistic Image in 1920X1080
            // Profile/Level                Traffic(KB/s)
            // 2130706434/65536(0x10000)    24K/s
            // 8/65536(0x10000)             76K/s
            // 2/65536(0x10000)             76K/s
            // 1/65536(0x10000)             76K/s
            // 1/2048(800)                  76K/s
            val profileLevelPair = CodecUtil.getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC)
//                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline }
//                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
//                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain }
                .firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline }
//                .maxByOrNull { it.profile }
            val usedProfile = profileLevelPair?.profile ?: MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            val usedLevel = profileLevelPair?.level ?: MediaCodecInfo.CodecProfileLevel.AVCLevel4

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                LogContext.log.w(TAG, "KEY_PROFILE: $usedProfile")
                setInteger(MediaFormat.KEY_PROFILE, usedProfile)
            } else {
                LogContext.log.w(TAG, "KEY_PROFILE static: AVCProfileBaseline")
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            }
//            setInteger(MediaFormat.KEY_COMPLEXITY, bitrateMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // You must specify KEY_LEVEL on Android 6.0+
                // AVCLevel51
                // AVCLevel4
                LogContext.log.w(TAG, "KEY_LEVEL: $usedLevel")
                setInteger(MediaFormat.KEY_LEVEL, usedLevel)
            } else {
                LogContext.log.w(TAG, "KEY_LEVEL static: AVCLevel4")
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Actually, this key has been used in Android 6.0+ so you can use it safely only if your device is Android 6.0+.
                // It's merely being opened as of Android 10.
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, frameRate.toFloat())
            }
        }

        val mediaCodecCallback = object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
                try {
                    val inputBuffer = codec.getInputBuffer(inputBufferId)

                    // fill inputBuffer with valid data
                    inputBuffer?.clear()
                    val data = queue.poll()?.also { inputBuffer?.put(it) }

                    codec.queueInputBuffer(inputBufferId, 0, data?.size ?: 0, computePresentationTimeUs(++mFrameCount), 0)
                } catch (e: Exception) {
                    LogContext.log.e(TAG, "You can ignore this error safely.", e)
                }
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
                val outputBuffer = codec.getOutputBuffer(outputBufferId)
                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
                // bufferFormat is equivalent to member variable outputFormat
                // outputBuffer is ready to be processed or rendered.
                outputBuffer?.let {
                    val encodedBytes = ByteArray(info.size)
                    it.get(encodedBytes)

                    when (info.flags) {
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
                            csd = encodedBytes.copyOf()
                            LogContext.log.w(TAG, "Found SPS/PPS frame: ${csd?.toHexadecimalString()}")
                        }
                        MediaCodec.BUFFER_FLAG_KEY_FRAME -> LogContext.log.i(TAG, "Found Key Frame[" + info.size + "]")
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
                    dataUpdateCallback?.onCallback(encodedBytes)
                }
                codec.releaseOutputBuffer(outputBufferId, false)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                LogContext.log.w(TAG, "onOutputFormatChanged format=$format")
                // Subsequent data will conform to new format.
                // Can ignore if using getOutputFormat(outputBufferId)
                outputFormat = format // option B
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                LogContext.log.e(TAG, "onError e=${e.message}")
            }
        }

//        h264Encoder = MediaCodec.createByCodecName("OMX.google.h264.encoder")
        h264Encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            outputFormat = it.outputFormat // option B
            it.setCallback(mediaCodecCallback)
            it.start()
        }
    }

    fun offerDataIntoQueue(data: ByteArray) {
        queue.offer(data)
    }

    fun setDataUpdateCallback(callback: CallbackListener?) {
        dataUpdateCallback = callback
    }

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / frameRate

    @SuppressWarnings("unused")
    fun stop() {
        try {
            h264Encoder.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Release sources.
     */
    fun release() {
        stop()
        h264Encoder.release()
    }

    companion object {
        private const val TAG = "CameraEncoder"
        const val DEFAULT_KEY_I_FRAME_INTERVAL = 5
        const val DEFAULT_BITRATE_MODE = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
    }
}