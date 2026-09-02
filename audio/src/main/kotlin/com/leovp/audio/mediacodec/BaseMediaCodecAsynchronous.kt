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
                    val inputBuf = codec.getInputBuffer(index) ?: return@withCodecOperationLock
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
                        codec.queueInputBuffer(index, 0, size, pts, 0)
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
                    val outputBuffer = codec.getOutputBuffer(index) ?: return@withCodecOperationLock
                    try {
                        when {
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 ->
                                onOutputData(
                                    outputBuffer,
                                    info,
                                    isConfig = true,
                                    isKeyFrame = false
                                )

                            (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 ->
                                onOutputData(
                                    outputBuffer,
                                    info,
                                    isConfig = false,
                                    isKeyFrame = true
                                )

                            (
                                info.flags and
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                ) != 0 -> onEndOfStream()
                            else ->
                                onOutputData(
                                    outputBuffer,
                                    info,
                                    isConfig = false,
                                    isKeyFrame = false
                                )
                        }
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
