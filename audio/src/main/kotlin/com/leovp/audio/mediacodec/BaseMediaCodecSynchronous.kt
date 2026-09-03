@file:Suppress("unused")

package com.leovp.audio.mediacodec

import android.media.AudioFormat
import android.media.MediaCodec
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

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
    }

    /**
     * Marks that the worker loop terminated because of a codec/error condition rather than a
     * normal end-of-stream, so we do not report a fake EOS after an error.
     */
    private val codecFailed = AtomicBoolean(false)

    /**
     * Reports a codec failure to the owner. Override to rebuild the codec or surface the error via
     * an independent error callback. Default implementation just logs. Do NOT busy-retry on the
     * same (already broken) codec instance here.
     */
    open fun notifyCodecFailure(e: Throwable) {
        LogContext.log.e(TAG, "Codec failure reported", e)
    }

    override fun onCodecStarted() {
        codecFailed.set(false)
        codecJob = ioScope.launch {
            do {
                ensureActive()
            } while (process())
            // Only signal normal EOS. Error termination goes through notifyCodecFailure(), and an
            // intentional teardown (isReleasing) must not masquerade as a clean end-of-stream.
            if (!codecFailed.get() && !isReleasing) onEndOfStream()
        }
    }

    private fun process(): Boolean = withCodecOperationLock {
        if (isReleasing) return@withCodecOperationLock false
        processWithCodecLock()
    }

    /** Processes one input/output iteration while lifecycle teardown is excluded. */
    @Suppress("ReturnCount")
    private fun processWithCodecLock(): Boolean {
        var isFinish = false
        try {
            // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
            val inputIndex: Int = codec.dequeueInputBuffer(0)
            if (inputIndex > -1) {
                val inputBuf = codec.getInputBuffer(inputIndex) ?: return true
                // Clear exist data.
                inputBuf.clear()
                // Fill inputBuffer with valid data.
                val size = onInputData(inputBuf)
                // LogContext.log.d(TAG, "    -> inputBuf size=${inputBuf.remaining()}")
                val pts = computePresentationTimeUs()
                when {
                    pts < 0 -> {
                        isFinish = true
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    }
                    // Every dequeued input buffer must be returned to MediaCodec. Queueing an
                    // empty buffer avoids permanently exhausting the codec's input slots when a
                    // non-blocking producer has no data available this round.
                    else -> codec.queueInputBuffer(inputIndex, 0, size.coerceAtLeast(0), pts, 0)
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var buffer: ByteBuffer?
            val st = System.currentTimeMillis()
            // Track whether any output was actually drained this round, so an idle stream (a
            // non-blocking producer with no data) does not spam a "Decode cost" log per iteration
            // (remediation R-5).
            var drainedOutput = false
            // Start decoding and get output index
            var outputIndex: Int = codec.dequeueOutputBuffer(bufferInfo, 0)
            // LogContext.log.d(TAG, "outputIndex=$outputIndex")
            while (outputIndex > -1) {
                buffer = codec.getOutputBuffer(outputIndex)
                // A null buffer means the index is no longer valid (e.g. invalidated by a
                // concurrent flush/release). Stop draining this round instead of spinning on the
                // same index forever; the worker loop re-checks cancellation via ensureActive()
                // on the next outer iteration (remediation R-1).
                if (buffer == null) {
                    LogContext.log.w(TAG, "getOutputBuffer($outputIndex) null; stop draining")
                    break
                }
                when {
                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 ->
                        onOutputData(buffer, bufferInfo, isConfig = true, isKeyFrame = false)

                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 ->
                        onOutputData(buffer, bufferInfo, isConfig = false, isKeyFrame = true)

                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 -> {
                        LogContext.log.w(TAG, "onEndOfStream()")
                        onEndOfStream()
                    }

                    else -> onOutputData(buffer, bufferInfo, isConfig = false, isKeyFrame = false)
                }

                // Must clear decoded data before next loop. Otherwise, you will get the same data
                // while looping.
                codec.releaseOutputBuffer(outputIndex, false)
                drainedOutput = true
                // Get data again.
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
            if (drainedOutput) {
                LogContext.log.d(TAG, "Decode cost: ${System.currentTimeMillis() - st}ms")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaCodec.CodecException) {
            // Must be caught BEFORE IllegalStateException because CodecException subclasses it.
            if (isReleasing) return stopWorkerDuringRelease("CodecException")
            LogContext.log.e(TAG, "CodecException", e)
            codecFailed.set(true)
            notifyCodecFailure(e)
            return false
        } catch (e: IllegalStateException) {
            if (isReleasing) return stopWorkerDuringRelease("Codec illegal state")
            LogContext.log.e(TAG, "Codec illegal state, stopping", e)
            codecFailed.set(true)
            return false
        } catch (e: Exception) {
            if (isReleasing) return stopWorkerDuringRelease("Decode error")
            LogContext.log.e(TAG, "Unexpected decode error, stopping", e)
            codecFailed.set(true)
            return false
        }
        return !isFinish
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
