@file:Suppress("unused")

package com.leovp.audio.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 2023/4/25 16:39
 */
abstract class BaseMediaCodecAsynchronous(codecName: String, sampleRate: Int, channelCount: Int, isEncoding: Boolean = false) :
    BaseMediaCodec(codecName, sampleRate, channelCount, isEncoding) {
    companion object {
        private const val TAG = "MediaCodecAsync"
    }

    override fun setMediaCodecOptions(codec: MediaCodec) {
        codec.setCallback(mediaCodecCallback)
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            runCatching {
                val inputBuf = codec.getInputBuffer(index) ?: return
                // Clear exist data.
                inputBuf.clear()
                // Fill inputBuffer with valid data.
                onInputData(inputBuf)
                inputBuf.flip()
                // if (BuildConfig.DEBUG) LogContext.log.d(TAG, "inputBuffer data=${data?.size}")
                codec.queueInputBuffer(index, 0, inputBuf.remaining(), getPresentationTimeUs(), 0)
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
            }.onFailure { it.printStackTrace() }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            this@BaseMediaCodecAsynchronous.format = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            this@BaseMediaCodecAsynchronous.onError(codec, e)
        }
    }
}
