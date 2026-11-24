@file:Suppress("unused")

package com.leovp.audio.mediacodec

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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

    private val callbackGeneration = AtomicLong(0)
    private val inputEosQueued = AtomicBoolean(false)

    override fun onBeforeCodecStart() {
        callbackGeneration.set(0)
        inputEosQueued.set(false)
    }

    override fun setMediaCodecOptions(codec: MediaCodec) {
        codec.setCallback(SessionCallback(callbackGeneration.get()))
    }

    private inner class SessionCallback(private val epoch: Long) : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) =
            withSessionOperation(epoch) {
                if (inputEosQueued.get()) return@withSessionOperation
                var inputQueued = false
                var fallbackFlags = 0
                try {
                    val inputBuf = codec.getInputBuffer(index)
                    if (inputBuf == null) {
                        codec.queueInputBuffer(index, 0, 0, 0, 0)
                        inputQueued = true
                        return@withSessionOperation
                    }
                    inputBuf.clear()
                    val size = onInputData(inputBuf)
                    val pts = computePresentationTimeUs()
                    if (pts < 0) {
                        fallbackFlags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        codec.queueInputBuffer(
                            index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEosQueued.set(true)
                    } else {
                        require(size >= 0) { "Codec input size must be non-negative" }
                        require(size <= inputBuf.capacity()) {
                            "Codec input size $size exceeds buffer capacity ${inputBuf.capacity()}"
                        }
                        codec.queueInputBuffer(index, 0, size, pts, 0)
                    }
                    inputQueued = true
                } catch (original: Throwable) {
                    if (!inputQueued) {
                        runCatching {
                            codec.queueInputBuffer(index, 0, 0, 0, fallbackFlags)
                        }.onFailure(original::addSuppressed)
                    }
                    throw original
                }
            }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) = withSessionOperation(epoch) {
            try {
                val outputBuffer = codec.getOutputBuffer(index)
                val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                val isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                val isEndOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                if (outputBuffer == null) {
                    LogContext.log.w(TAG, "getOutputBuffer($index) returned null")
                } else if (info.size > 0 || isConfig) {
                    onOutputData(outputBuffer, info, isConfig, isKeyFrame)
                }
                if (isEndOfStream) onEndOfStream()
            } finally {
                codec.releaseOutputBuffer(index, false)
            }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) =
            withSessionOperation(epoch) {
                this@BaseMediaCodecAsynchronous.format = format
                this@BaseMediaCodecAsynchronous.onOutputFormatChanged(codec, format)
            }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) =
            withSessionOperation(epoch) {
                try {
                    this@BaseMediaCodecAsynchronous.onError(codec, e)
                } catch (hookFailure: Exception) {
                    e.addSuppressed(hookFailure)
                }
                // A successful subclass hook does not recover a native terminal error.
                throw e
            }
    }

    /** Captures accepted-operation failures under the codec lock and notifies unlocked. */
    private fun withSessionOperation(epoch: Long, action: () -> Unit) {
        var failure: Exception? = null
        var notifyOwner = false
        withCodecOperationLock {
            if (!isRunning || epoch != callbackGeneration.get()) return@withCodecOperationLock
            try {
                action()
            } catch (error: Exception) {
                // Hooks may reenter flush(), so the entry check alone cannot identify this error.
                if (epoch == callbackGeneration.get()) {
                    failure = error
                    notifyOwner = captureCodecOperationFailure(error)
                }
            }
        }
        failure?.let { error ->
            LogContext.log.e(TAG, "Codec callback failed", error)
            if (notifyOwner) notifyTransitionedCodecFailure(error)
        }
    }

    /**
     * Resets asynchronous processing by retiring the native codec and configuring a fresh one.
     *
     * Unlike the old flush/start implementation, callbacks bind to their native session at
     * registration, so delayed old buffer indices cannot be mistaken for newly issued indices.
     * Reset has the cost of codec recreation and re-emits codec configuration/format events.
     * Subclasses overriding createCodec() must create a fresh native instance on every call;
     * setFormatOptions() must reconstruct the input configuration, including codec-specific data.
     * Already queued subclass input is retained; callers must coordinate their own stream queues.
     */
    override fun flush() {
        var failure: Throwable? = null
        var transitionedToFailed = false
        withCodecOperationLock {
            check(isRunning) { "flush() requires a running codec session" }
            callbackGeneration.incrementAndGet()
            inputEosQueued.set(false)
            val retiringCodec = checkNotNull(detachCodec())
            try {
                try {
                    retiringCodec.flush()
                } catch (error: Throwable) {
                    failure = error
                }
                try {
                    retiringCodec.release()
                } catch (error: Throwable) {
                    failure?.addSuppressed(error) ?: run { failure = error }
                }
                failure?.let { throw it }
                createMediaFormat()
                createCodec()
                if (codec === retiringCodec) {
                    detachCodec()
                    error("Asynchronous reset requires a new native codec")
                }
                if (!isRunning) {
                    // A concurrent release owner will dispose of the newly published codec.
                    return@withCodecOperationLock
                }
                codec.start()
            } catch (error: Throwable) {
                failure = error
                transitionedToFailed = transitionToCodecFailure(error)
            }
        }
        failure?.let { error ->
            LogContext.log.e(TAG, "flush and restart failed", error)
            if (transitionedToFailed) notifyTransitionedCodecFailure(error)
            if (error is Error) throw error
        }
    }
}
