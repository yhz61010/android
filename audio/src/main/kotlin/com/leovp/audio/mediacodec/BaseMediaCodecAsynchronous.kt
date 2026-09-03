@file:Suppress("unused")

package com.leovp.audio.mediacodec

import com.leovp.audio.base.runCatchingPreservingCancellation

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2023/4/25 16:39
 */
abstract class BaseMediaCodecAsynchronous(
    codecName: String,
    sampleRate: Int,
    channelCount: Int,
    audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    isEncoding: Boolean = false,
) : BaseMediaCodec(codecName, sampleRate, channelCount, audioFormat, isEncoding) {
    companion object {
        private const val TAG = "MediaCodecAsync"
    }

    override fun setMediaCodecOptions(codec: MediaCodec) {
        codec.setCallback(mediaCodecCallback)
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (isReleasing) return
            runCatchingPreservingCancellation {
                withCodecOperationLock {
                    if (isReleasing) return@withCodecOperationLock
                    var inputQueued = false
                    try {
                        val inputBuf = codec.getInputBuffer(index)
                        if (inputBuf == null) {
                            codec.queueInputBuffer(index, 0, 0, 0, 0)
                            inputQueued = true
                            return@withCodecOperationLock
                        }
                        // Clear exist data.
                        inputBuf.clear()
                        // Fill inputBuffer with valid data.
                        val size = onInputData(inputBuf)
                        // LogContext.log.d(TAG, "    -> inputBuf size=${inputBuf.remaining()}")
                        val pts = computePresentationTimeUs()
                        if (pts < 0) {
                            codec.queueInputBuffer(
                                index,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                        } else {
                            require(size >= 0) { "Codec input size must be non-negative" }
                            require(size <= inputBuf.capacity()) {
                                "Codec input size $size exceeds buffer capacity " +
                                    inputBuf.capacity()
                            }
                            codec.queueInputBuffer(index, 0, size, pts, 0)
                        }
                        inputQueued = true
                    } catch (original: Throwable) {
                        if (!inputQueued) {
                            runCatching {
                                codec.queueInputBuffer(index, 0, 0, 0, 0)
                            }.onFailure(original::addSuppressed)
                        }
                        throw original
                    }
                }
            }.onFailure {
                if (!isReleasing) LogContext.log.e(TAG, "Input buffer callback failed", it)
            }
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            if (isReleasing) return
            runCatchingPreservingCancellation {
                withCodecOperationLock {
                    if (isReleasing) return@withCodecOperationLock
                    val outputBuffer = codec.getOutputBuffer(index)
                    if (outputBuffer == null) {
                        codec.releaseOutputBuffer(index, false)
                        return@withCodecOperationLock
                    }
                    try {
                        val isConfig =
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val isKeyFrame =
                            info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        val isEndOfStream =
                            info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.size > 0 || isConfig) {
                            onOutputData(outputBuffer, info, isConfig, isKeyFrame)
                        }
                        if (isEndOfStream) onEndOfStream()
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }.onFailure {
                if (!isReleasing) LogContext.log.e(TAG, "Output buffer callback failed", it)
            }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (isReleasing) return
            withCodecOperationLock {
                if (isReleasing) return@withCodecOperationLock
                // Subsequent data will conform to new format.
                // Can ignore if using getOutputFormat(outputBufferId)
                this@BaseMediaCodecAsynchronous.format = format // option B
                this@BaseMediaCodecAsynchronous.onOutputFormatChanged(codec, format)
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            if (isReleasing) return
            withCodecOperationLock {
                if (isReleasing) return@withCodecOperationLock
                this@BaseMediaCodecAsynchronous.onError(codec, e)
            }
        }
    }
}
