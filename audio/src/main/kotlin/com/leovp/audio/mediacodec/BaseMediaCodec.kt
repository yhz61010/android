package com.leovp.audio.mediacodec

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.base.runCatchingPreservingCancellation
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec
import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext

/**
 * Author: Michael Leo
 * Date: 2023/5/4 10:18
 */
abstract class BaseMediaCodec(
    private val codecName: String,
    protected open val sampleRate: Int,
    protected open val channelCount: Int,
    protected open val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val isEncoding: Boolean = false,
) : IAudioMediaCodec {
    companion object {
        private const val TAG = "BaseMediaCodec"
    }

    internal lateinit var format: MediaFormat
    internal lateinit var codec: MediaCodec

    protected val ioScope = CoroutineScope(Dispatchers.IO + CoroutineName("base-mediacodec"))

    /**
     * The codec worker coroutine. Assigned by the synchronous worker launch (see
     * [BaseMediaCodecSynchronous]). Owned here so [releaseAndJoin] can join it deterministically.
     */
    protected var codecJob: Job? = null

    private val codecReleased = AtomicBoolean(false)

    /** Serializes codec callbacks and worker operations with stop/flush/release. */
    private val codecOperationLock = ReentrantLock()

    /**
     * Set as soon as an intentional teardown ([stop], [release], or [releaseAndJoin]) begins. A
     * worker or asynchronous callback may still be waiting to enter a codec operation; any
     * exception caused by that teardown is expected shutdown noise, not a real failure. Subclasses
     * check this flag before touching the codec or reporting a spurious failure (remediation R-4).
     */
    private val releasing = AtomicBoolean(false)

    /** Whether an intentional teardown is in progress; see [releasing]. */
    protected val isReleasing: Boolean get() = releasing.get()

    abstract fun setFormatOptions(format: MediaFormat)

    open fun setMediaCodecOptions(codec: MediaCodec) = Unit

    open fun start() {
        createMediaFormat()
        createCodec()
        codec.start()
    }

    open fun stop() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        releasing.set(true)
        withCodecOperationLock {
            runCatchingPreservingCancellation { codec.stop() }
                .onFailure { LogContext.log.e(TAG, "stop() error", it) }
        }
    }

    /**
     * Deterministic lifecycle teardown: cancel and join the codec worker, cancel [ioScope], then
     * release the codec exactly once.
     *
     * This MUST be called by an external owner, never by the codec worker itself, otherwise the
     * worker would join on itself and deadlock. If a worker needs to request teardown, it should
     * expose a separate `requestRelease()` returning a [Job] instead of calling this.
     */
    suspend fun releaseAndJoin() {
        val job = codecJob
        require(job !== currentCoroutineContext()[Job]) {
            "releaseAndJoin() must be called by an external owner, not the codec worker"
        }
        releasing.set(true)
        job?.cancelAndJoin()
        codecJob = null
        ioScope.cancel()
        releaseCodecOnce()
    }

    /**
     * Idempotent codec teardown. Only stops/releases the codec and flips the released flag.
     * Does NOT cancel scopes or start any fire-and-forget work.
     */
    protected fun releaseCodecOnce() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        withCodecOperationLock {
            if (!codecReleased.compareAndSet(false, true)) return@withCodecOperationLock
            runCatchingPreservingCancellation { codec.flush() }
                .onFailure { LogContext.log.e(TAG, "flush error", it) }

            // These are the magic lines for Samsung phone. DO NOT try to remove or refactor me.
            runCatchingPreservingCancellation { codec.release() }
                .onFailure { LogContext.log.e(TAG, "release error", it) }
            onCodecReleased()
        }
    }

    /** Clears subclass state after the codec has been released under [codecOperationLock]. */
    protected open fun onCodecReleased() = Unit

    /**
     * Release resource.
     */
    @Deprecated(
        "Non-suspend release cannot guarantee the worker has exited. " +
            "Use releaseAndJoin() for deterministic shutdown.",
        ReplaceWith("releaseAndJoin()")
    )
    open fun release() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        // Preserve the legacy synchronous return semantics. New code should use releaseAndJoin()
        // when it must also wait for the worker to finish.
        releasing.set(true)
        codecJob?.cancel()
        codecJob = null
        ioScope.cancel()
        releaseCodecOnce()
    }

    open fun flush() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        withCodecOperationLock {
            runCatchingPreservingCancellation { codec.flush() }
                .onFailure { LogContext.log.e(TAG, "flush error", it) }
        }
    }

    /**
     * Runs a synchronous codec operation under the same lock used by lifecycle teardown.
     *
     * Synchronous processing invokes subclass input/output callbacks while holding this lock, so
     * lifecycle operations wait for the current callback to return. Keep those callbacks bounded.
     */
    protected fun <T> withCodecOperationLock(action: () -> T): T =
        codecOperationLock.withLock(action)

    /**
     * Most of the time, you do NOT need to override this method.
     */
    open fun createMediaFormat() {
        LogContext.log.w(
            TAG,
            "createMediaFormat() codec=$codecName sampleRate=$sampleRate channelCount=$channelCount"
        )
        format = MediaFormat.createAudioFormat(codecName, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8 * 1024)
        setFormatOptions(format)
        LogContext.log.w(TAG, "format=$format")
    }

    /**
     * Most of the time, you do NOT need to override this method.
     */
    open fun createCodec() {
        LogContext.log.w(TAG, "createCodec() codec=$codecName isEncoding=$isEncoding")
        codec =
            if (isEncoding) {
                MediaCodec.createEncoderByType(codecName)
            } else {
                MediaCodec.createDecoderByType(codecName)
            }
        codec.configure(format, null, null, if (isEncoding) MediaCodec.CONFIGURE_FLAG_ENCODE else 0)
        setMediaCodecOptions(codec)
    }

    protected fun getBytesPerSample(): Int = when (audioFormat) {
        AudioFormat.ENCODING_PCM_8BIT -> 1

        AudioFormat.ENCODING_PCM_16BIT,
        AudioFormat.ENCODING_IEC61937,
        AudioFormat.ENCODING_DEFAULT -> 2

        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3

        AudioFormat.ENCODING_PCM_FLOAT,
        AudioFormat.ENCODING_PCM_32BIT -> 4

        else -> throw IllegalArgumentException("Bad audio format $audioFormat")
    }
}
