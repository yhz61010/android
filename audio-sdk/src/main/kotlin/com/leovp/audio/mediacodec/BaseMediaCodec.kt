@file:Suppress("unused")

package com.leovp.audio.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2023/4/25 16:39
 */
abstract class BaseMediaCodec(
    private val codecName: String,
    protected open val sampleRate: Int,
    protected open val channelCount: Int,
    private val isEncoding: Boolean = false) : IAudioMediaCodec {
    companion object {
        private const val TAG = "BaseMediaCodec"
    }

    protected lateinit var format: MediaFormat
    protected lateinit var codec: MediaCodec

    private val ioScope = CoroutineScope(Dispatchers.IO + CoroutineName("base-mediacodec"))
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
        ioScope.cancel()
        require(::codec.isInitialized) { "Did you call start() before?" }
        stop()
        // These are the magic lines for Samsung phone. DO NOT try to remove or refactor me.
        codec.setCallback(null)
        runCatching { codec.release() }.onFailure { it.printStackTrace() }
    }

    open fun flush() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        codec.flush()
    }

    private fun createMediaFormat() {
        LogContext.log.i(TAG, "createMediaFormat() codec=$codecName sampleRate=$sampleRate channelCount=$channelCount")
        format = MediaFormat.createAudioFormat(codecName, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8 * 1024)
        setFormatOptions(format)
    }

    private fun createCodec() {
        LogContext.log.i(TAG, "createCodec() codec=$codecName isEncoding=$isEncoding")
        val mediaCodec = if (isEncoding) MediaCodec.createEncoderByType(codecName) else MediaCodec.createDecoderByType(codecName)
        codec = mediaCodec.apply {
            configure(format, null, null, if (isEncoding) MediaCodec.CONFIGURE_FLAG_ENCODE else 0)
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
                    // LogContext.log.d(TAG, "onOutputBufferAvailable length=${info.size}")
                    // val copiedBuffer = it.copyAll()
                    // if (BuildConfig.DEBUG) LogContext.log.d(TAG,
                    //     "copiedBuffer ori[${copiedBuffer.remaining()}]=${copiedBuffer.toByteArray().toHexStringLE()}")
                    // val outBytes = it.toByteArray()
                    ioScope.launch {
                        when {
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 ->
                                onOutputData(it, info, isConfig = true, isKeyFrame = false)

                            (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 ->
                                onOutputData(it, info, isConfig = false, isKeyFrame = true)

                            (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 -> onEndOfStream()
                            else -> onOutputData(it, info, isConfig = false, isKeyFrame = false)
                        }
                        codec.releaseOutputBuffer(index, false)
                    }
                }
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
        // LogContext.log.d(TAG, "computePresentationTimeUs=${frameIndex * 1_000_000L / sampleRate}")
        return frameIndex * 1_000_000L / sampleRate
    }

    override fun getPresentationTimeUs(): Long {
        return computePresentationTimeUs(++frameCount, sampleRate)
    }
}
