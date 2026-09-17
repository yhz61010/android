@file:Suppress("unused")

package com.leovp.audio.mediacodec

import android.media.AudioFormat
import android.media.MediaCodec
import com.leovp.audio.base.runCatchingPreservingCancellation
import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Author: Michael Leo
 * Date: 2023/5/4 10:18
 */
abstract class BaseMediaCodecSynchronous(
    codecName: String,
    sampleRate: Int,
    channelCount: Int,
    audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    isEncoding: Boolean = false,
) : BaseMediaCodec(codecName, sampleRate, channelCount, audioFormat, isEncoding) {
    companion object {
        private const val TAG = "MediaCodecSync"
        private const val EOS_OUTPUT_TIMEOUT_US = 10_000L
        private val EOS_RETRY_DELAY = 1.milliseconds
        private const val DEFAULT_EOS_DRAIN_TIMEOUT_MS = 3_000L
    }

    /**
     * Marks that the worker loop terminated because of a codec/error condition rather than a
     * normal end-of-stream, so we do not report a fake EOS after an error.
     */
    private val codecFailed = AtomicBoolean(false)
    private var inputEosQueued = false
    private var outputEosReceived = false

    /** Maximum wall-clock wait for output EOS after input EOS has been queued. */
    protected open val eosDrainTimeoutMs: Long = DEFAULT_EOS_DRAIN_TIMEOUT_MS

    override fun onCodecStarted() {
        codecFailed.set(false)
        inputEosQueued = false
        outputEosReceived = false
        codecJob = ioScope.launch {
            while (!inputEosQueued) {
                ensureActive()
                if (!process()) break
            }
            if (!codecFailed.get() && inputEosQueued && !outputEosReceived) {
                val receivedEos = withTimeoutOrNull(eosDrainTimeoutMs.milliseconds) {
                    while (!outputEosReceived) {
                        ensureActive()
                        if (!process()) return@withTimeoutOrNull outputEosReceived
                        delay(EOS_RETRY_DELAY)
                    }
                    true
                } ?: false
                // A failure inside the drain loop has already been reported by process(); only a
                // genuine timeout may synthesize a second, otherwise misleading, failure.
                if (!receivedEos && !isReleasing && !codecFailed.get()) {
                    val failure = IllegalStateException(
                        "Timed out waiting for codec output EOS after ${eosDrainTimeoutMs}ms"
                    )
                    codecFailed.set(true)
                    reportCodecFailure(failure)
                }
            }
            // Only signal normal EOS. Error termination goes through notifyCodecFailure(), and an
            // intentional teardown (isReleasing) must not masquerade as a clean end-of-stream.
            if (!codecFailed.get() && !isReleasing && outputEosReceived) onEndOfStream()
        }
    }

    private fun process(): Boolean {
        try {
            return withCodecOperationLock {
                if (isReleasing) return@withCodecOperationLock false
                processWithCodecLock()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaCodec.CodecException) {
            // Must be caught BEFORE IllegalStateException because CodecException subclasses it.
            if (isReleasing) return stopWorkerDuringRelease("CodecException")
            LogContext.log.e(TAG, "CodecException", e)
            codecFailed.set(true)
            reportCodecFailure(e)
        } catch (e: IllegalStateException) {
            if (isReleasing) return stopWorkerDuringRelease("Codec illegal state")
            LogContext.log.e(TAG, "Codec illegal state, stopping", e)
            codecFailed.set(true)
            reportCodecFailure(e)
        } catch (e: Exception) {
            if (isReleasing) return stopWorkerDuringRelease("Decode error")
            LogContext.log.e(TAG, "Unexpected decode error, stopping", e)
            codecFailed.set(true)
            reportCodecFailure(e)
        }
        return false
    }

    /** Processes one input/output iteration while lifecycle teardown is excluded. */
    @Suppress("ReturnCount")
    private fun processWithCodecLock(): Boolean {
        queueInputIfNeeded()
        drainOutput()
        return !outputEosReceived
    }

    /** Queues at most one input buffer, and stops requesting input after input EOS is queued. */
    private fun queueInputIfNeeded() {
        if (inputEosQueued) return
        // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
        val inputIndex = codec.dequeueInputBuffer(0)
        if (inputIndex < 0) return

        try {
            val inputBuf = codec.getInputBuffer(inputIndex)
            if (inputBuf == null) {
                LogContext.log.w(TAG, "getInputBuffer($inputIndex) null; return empty buffer")
                codec.queueInputBuffer(inputIndex, 0, 0, 0, 0)
                return
            }
            inputBuf.clear()
            val size = onInputData(inputBuf)
            val pts = computePresentationTimeUs()
            if (pts < 0) {
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                inputEosQueued = true
            } else {
                require(size >= 0) { "Codec input size must be non-negative" }
                require(size <= inputBuf.capacity()) {
                    "Codec input size $size exceeds buffer capacity ${inputBuf.capacity()}"
                }
                codec.queueInputBuffer(inputIndex, 0, size, pts, 0)
            }
        } catch (original: Throwable) {
            // A dequeued input index belongs to the client until it is queued back. Return an empty
            // buffer before propagating every failure, including cancellation, so one interrupted
            // callback cannot starve the codec pool.
            runCatchingPreservingCancellation {
                codec.queueInputBuffer(inputIndex, 0, 0, 0, 0)
            }.onFailure(original::addSuppressed)
            throw original
        }
    }

    /** Drains available output and keeps polling after input EOS until output EOS is observed. */
    private fun drainOutput() {
        val bufferInfo = MediaCodec.BufferInfo()
        val startedAt = System.currentTimeMillis()
        var drainedOutput = false
        var outputIndex = codec.dequeueOutputBuffer(
            bufferInfo,
            if (inputEosQueued) EOS_OUTPUT_TIMEOUT_US else 0
        )
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val outputFormat = codec.outputFormat
            format = outputFormat
            onOutputFormatChanged(codec, outputFormat)
            outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        }

        while (outputIndex >= 0) {
            val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            var hasOutputBuffer = false
            try {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer == null) {
                    LogContext.log.w(TAG, "getOutputBuffer($outputIndex) null; stop draining")
                } else {
                    hasOutputBuffer = true
                    val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    val isKeyFrame = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                    if (bufferInfo.size > 0 || isConfig) {
                        onOutputData(outputBuffer, bufferInfo, isConfig, isKeyFrame)
                    }
                }
            } finally {
                codec.releaseOutputBuffer(outputIndex, false)
            }
            drainedOutput = true
            if (isEos) {
                outputEosReceived = true
                LogContext.log.w(TAG, "Output end of stream received")
                break
            }
            if (!hasOutputBuffer) break
            outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        }
        if (drainedOutput) {
            LogContext.log.d(TAG, "Decode cost: ${System.currentTimeMillis() - startedAt}ms")
        }
    }

    /**
     * The worker touched the codec while it was being torn down (see [isReleasing]). This is
     * expected shutdown noise, not a real failure: stop the loop quietly without reporting a codec
     * failure or firing a fake end-of-stream (remediation R-4).
     */
    private fun stopWorkerDuringRelease(what: String): Boolean {
        LogContext.log.w(TAG, "$what during intentional release; stopping worker")
        return false
    }
}
