@file:Suppress("unused")

package com.leovp.audio.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec
import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 2023/4/25 16:39
 */
abstract class BaseMediaCodec(
    private val codecName: String,
    protected open val sampleRate: Int,
    protected open val channelCount: Int) : IAudioMediaCodec {
    companion object {
        private const val TAG = "BaseMediaCodec"
    }

    protected lateinit var format: MediaFormat
    protected lateinit var codec: MediaCodec

    private var frameCount: Long = 0

    abstract fun setFormatOptions(format: MediaFormat)

    open fun start() {
        createMediaFormat()
        createCodec()
        codec.start()
    }

    open fun stop() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        runCatching { codec.stop() }.onFailure { it.printStackTrace() }
    }

    /**
     * Release resource.
     */
    open fun release() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        stop()
        runCatching { codec.release() }.onFailure { it.printStackTrace() }
    }

    private fun createMediaFormat() {
        format = MediaFormat.createAudioFormat(codecName, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8 * 1024)
            setFormatOptions(this)
        }
    }

    private fun createCodec() {
        codec = MediaCodec.createEncoderByType(codecName).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            setCallback(mediaCodecCallback)
        }
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            runCatching {
                val inputBuffer = codec.getInputBuffer(index)
                // fill inputBuffer with valid data
                inputBuffer?.clear()
                val data = onInputData()?.also {
                    inputBuffer?.put(it)
                }
                // if (BuildConfig.DEBUG) LogContext.log.d(TAG, "inputBuffer data=${data?.size}")
                codec.queueInputBuffer(index, 0, data?.size ?: 0, getPresentationTimeUs(), 0)
            }.onFailure { it.printStackTrace() }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            runCatching {
                val outputBuffer: ByteBuffer? = codec.getOutputBuffer(index) // little endian
                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
                // bufferFormat is equivalent to member variable outputFormat
                // outputBuffer is ready to be processed or rendered.
                outputBuffer?.let {
                    // if (BuildConfig.DEBUG) LogContext.log.d(OpusEncoder.TAG, "onOutputBufferAvailable length=${info.size}")
                    // val copiedBuffer = it.copyAll()
                    // if (BuildConfig.DEBUG) LogContext.log.d(TAG,
                    //     "copiedBuffer ori[${copiedBuffer.remaining()}]=${copiedBuffer.toByteArray().toHexStringLE()}")
                    // val decodedBytes = it.toByteArray()
                    when {
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 -> onOutputData(it, isConfig = true, isKeyFrame = false)
                        (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 -> onOutputData(it, isConfig = false, isKeyFrame = true)
                        (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 -> onEndOfStream()
                        else -> onOutputData(it, isConfig = false, isKeyFrame = false)
                    }
                }
                codec.releaseOutputBuffer(index, false)
            }.onFailure { it.printStackTrace() }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            this@BaseMediaCodec.format = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            this@BaseMediaCodec.onError(codec, e)
        }
    }

    /**
     * Calculate PTS.
     * Actually, it doesn't make any error if you return 0 directly.
     *
     * @return The calculated presentation time in microseconds.
     */
    private fun computePresentationTimeUs(frameIndex: Long, sampleRate: Int): Long {
        // LogContext.log.d(TAG, "computePresentationTimeUs=$result")
        return frameIndex * 1_000_000L / sampleRate
    }

    override fun getPresentationTimeUs(): Long {
        return computePresentationTimeUs(++frameCount, sampleRate)
    }
}
