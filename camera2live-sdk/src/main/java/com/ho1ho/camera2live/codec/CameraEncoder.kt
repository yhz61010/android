package com.ho1ho.camera2live.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.camera2live.listeners.CallbackListener
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Author: Michael Leo
 * Date: 20-3-24 下午5:35
 */
class CameraEncoder @JvmOverloads constructor(
    private val width: Int, private val height: Int,
    private val bitrate: Int, private val frameRate: Int,
    private val iFrameInterval: Int = DEFAULT_KEY_I_FRAME_INTERVAL,
    private val bitrateMode: Int = DEFAULT_BITRATE_MODE
) {
    init {
        initEncoder()
    }

    val queue = ConcurrentLinkedQueue<ByteArray>()
    private var dataUpdateCallback: CallbackListener? = null
    private lateinit var h264Encoder: MediaCodec
    private var outputFormat: MediaFormat? = null

    @SuppressWarnings("unused")
    var csd: ByteArray? = null
        private set
    private var mFrameCount: Long = 0

    private fun initEncoder() {
        CLog.i(
            TAG,
            String.format("initEncoder width=%d height=%d bitrate=%d frameRate=%d", width, height, bitrate, frameRate)
        )

        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        with(mediaFormat) {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
            setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            }
//            setInteger(MediaFormat.KEY_COMPLEXITY, bitrateMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // You must specify KEY_LEVEL on Android 6.0+
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51)
            }
        }

        val mediaCodecCallback = object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
                val inputBuffer = codec.getInputBuffer(inputBufferId)

                // fill inputBuffer with valid data
                inputBuffer?.clear()
                val data = queue.poll()?.also { inputBuffer?.put(it) }

                codec.queueInputBuffer(inputBufferId, 0, data?.size ?: 0, computePresentationTimeUs(++mFrameCount), 0)
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
                            CLog.w(TAG, "Found SPS/PPS frame: ${csd!!.contentToString()}")
                        }
                        MediaCodec.BUFFER_FLAG_KEY_FRAME -> CLog.i(TAG, "Found Key Frame[" + info.size + "]")
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
                CLog.w(TAG, "onOutputFormatChanged format=$format")
                // Subsequent data will conform to new format.
                // Can ignore if using getOutputFormat(outputBufferId)
                outputFormat = format // option B
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                CLog.e(TAG, "onError e=${e.message}")
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