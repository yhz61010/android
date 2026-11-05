package com.leovp.audio.aac

import com.leovp.audio.base.runCatchingPreservingCancellation

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.leovp.audio.base.AndroidFileAudioOutput
import com.leovp.audio.base.FileAudioOutput
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.mediacodec.BaseMediaCodecSynchronous
import com.leovp.bytes.toByteArray
import com.leovp.log.LogContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot AAC file player that owns its extractor, decoder session and AudioTrack as one unit.
 *
 * This class no longer extends `BaseMediaCodecSynchronous`. Upgrading callers must replace any
 * inherited codec-level `releaseAndJoin()` call with [stop], which waits for the complete player
 * teardown. Precompiled consumers must be rebuilt because the superclass changed.
 *
 * Author: Michael Leo
 * Date: 2020/9/17 下午5:01
 */
class AacFilePlayer internal constructor(
    private val audioDecodeInfo: AudioDecoderInfo,
    private val audioOutput: FileAudioOutput,
    private val sourceFactory: AacSampleSourceFactory,
    private val decoderFactory: AacDecoderSessionFactory?,
) {
    constructor(
        ctx: Context,
        audioDecodeInfo: AudioDecoderInfo,
        // AudioAttributes.USAGE_VOICE_COMMUNICATION  AudioAttributes.USAGE_MEDIA
        usage: Int = AudioAttributes.USAGE_MEDIA,
        // AudioAttributes.CONTENT_TYPE_SPEECH  AudioAttributes.CONTENT_TYPE_MUSIC
        contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
    ) : this(
        audioDecodeInfo,
        AndroidFileAudioOutput(ctx, audioDecodeInfo, usage, contentType),
        AndroidAacSampleSourceFactory,
        null
    )

    companion object {
        private const val TAG = "AacFilePlayer"
        private val AUDIO_TRACK_DRAIN_TIMEOUT = 3.seconds

        /** Poll interval for a drain that has no completion signal of its own. */
        private val DRAIN_POLL_INTERVAL = 20.milliseconds
    }

    private var sampleSource: AacSampleSource? = null
    private var currentSampleTimeUs = -1L
    private val writtenAudioFrames = AtomicLong(0)

    private var cb: (() -> Unit)? = null
    private var errorCb: ((Throwable) -> Unit)? = null
    private val started = AtomicBoolean(false)
    private val terminalStarted = AtomicBoolean(false)
    private val terminalCompletion = CompletableDeferred<Unit>()
    private val terminalFailure = AtomicReference<Throwable?>(null)
    private val terminalExceptionHandler = CoroutineExceptionHandler { _, error ->
        LogContext.log.e(TAG, "Unhandled AAC terminal cleanup failure", error)
    }
    private val terminalScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("aac-file-player-terminal") +
            terminalExceptionHandler
    )
    private val lifecycleMutex = Mutex()
    private var decoder: AacDecoderSession? = null

    private fun readSample(inBuf: ByteBuffer): Int {
        val sample = sampleSource?.readSample(inBuf) ?: AacSample(-1, -1)
        currentSampleTimeUs = sample.presentationTimeUs
        if (sample.size < 0) {
            currentSampleTimeUs = -1
            LogContext.log.d(TAG, "readSampleData sampleSize=${sample.size}")
        }
        return sample.size
    }

    private fun writeDecodedPcm(
        outBuf: ByteBuffer,
        info: MediaCodec.BufferInfo,
        isConfig: Boolean,
        isKeyFrame: Boolean
    ) {
        // LogContext.log.e(TAG, "onOutputData isConfig=$isConfig isKeyFrame=$isKeyFrame")
        val chunkPCM = outBuf.toByteArray()
        if (chunkPCM.isNotEmpty()) {
            LogContext.log.i(
                TAG,
                "PCM data[${chunkPCM.size}] isConfig=$isConfig isKeyFrame=$isKeyFrame"
            )
            val writtenBytes = requireSuccessfulAacAudioWrite(audioOutput.write(chunkPCM))
            val pcmFrameBytes = Short.SIZE_BYTES * audioDecodeInfo.channelCount
            writtenAudioFrames.addAndGet((writtenBytes / pcmFrameBytes).toLong())
        }
    }

    private fun requestFailure(error: Throwable) {
        terminalScope.launch {
            finishPlayback(TerminalReason.Failure(error), propagateFailure = false)
        }
    }

    /** Owns the codec worker privately so callers cannot bypass the player's full teardown. */
    private inner class AndroidDecoderSession(
        private val mime: String,
        private val mediaFormat: MediaFormat,
    ) : BaseMediaCodecSynchronous(
        MediaFormat.MIMETYPE_AUDIO_AAC,
        audioDecodeInfo.sampleRate,
        audioDecodeInfo.channelCount
    ),
        AacDecoderSession {
        override fun setFormatOptions(format: MediaFormat) = Unit

        override fun createMediaFormat() = Unit

        override fun createCodec() {
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(mediaFormat, null, null, 0)
        }

        override fun onInputData(inBuf: ByteBuffer): Int = readSample(inBuf)

        override fun onOutputData(
            outBuf: ByteBuffer,
            info: MediaCodec.BufferInfo,
            isConfig: Boolean,
            isKeyFrame: Boolean,
        ) = writeDecodedPcm(outBuf, info, isConfig, isKeyFrame)

        override fun computePresentationTimeUs(): Long = currentSampleTimeUs

        override fun onEndOfStream() {
            terminalScope.launch {
                finishPlayback(TerminalReason.NaturalEnd, propagateFailure = false)
            }
        }

        override fun notifyCodecFailure(error: Throwable) = requestFailure(error)
    }

    private fun createDecoder(source: AacSampleSource): AacDecoderSession = decoderFactory?.create(
        audioDecodeInfo,
        ::readSample,
        { currentSampleTimeUs },
        ::writeDecodedPcm,
        { terminalScope.launch { finishPlayback(TerminalReason.NaturalEnd, false) } },
        ::requestFailure
    ) ?: AndroidDecoderSession(source.mime, source.mediaFormat)

    /**
     * Starts this one-shot player.
     *
     * Initialization failures are cleaned up and then rethrown to the caller. Calling this method
     * more than once always fails before any active playback state is changed. [endCallback] is
     * only invoked after natural completion; asynchronous failures invoke [errorCallback] after
     * cleanup, while an explicit [stop] invokes neither callback.
     *
     * @throws IllegalStateException if playback was already started or the file has no AAC track.
     * @throws Exception if the extractor, AudioTrack, or MediaCodec cannot be initialized.
     */
    suspend fun playAac(
        aacFile: File,
        endCallback: () -> Unit,
        errorCallback: (Throwable) -> Unit = {},
    ) {
        check(started.compareAndSet(false, true)) { "AacFilePlayer can only be started once" }
        cb = endCallback
        errorCb = errorCallback
        try {
            withContext(Dispatchers.IO) {
                lifecycleMutex.withLock {
                    check(!terminalStarted.get()) { "AacFilePlayer is stopping" }
                    val source = sourceFactory.open(aacFile)
                    sampleSource = source
                    val playbackDecoder = createDecoder(source)
                    decoder = playbackDecoder
                    audioOutput.play()
                    playbackDecoder.start()
                }
            }
        } catch (e: CancellationException) {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    finishPlayback(TerminalReason.ExplicitStop)
                }
            } catch (cleanupFailure: Throwable) {
                e.addSuppressed(cleanupFailure)
            }
            throw e
        } catch (e: Throwable) {
            LogContext.log.e(TAG, "AAC playback start failed", e)
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    finishPlayback(TerminalReason.ExplicitStop)
                }
            } catch (cleanupFailure: Throwable) {
                e.addSuppressed(cleanupFailure)
            }
            throw e
        }
    }

    /**
     * Stops this one-shot player and waits for its codec worker before releasing input resources.
     */
    suspend fun stop() {
        withContext(Dispatchers.IO) { finishPlayback(TerminalReason.ExplicitStop) }
    }

    private suspend fun finishPlayback(reason: TerminalReason, propagateFailure: Boolean = true) {
        if (!terminalStarted.compareAndSet(false, true)) {
            terminalCompletion.await()
            if (propagateFailure) terminalFailure.get()?.let { throw it }
            return
        }

        val completionCallback = cb
        val failureCallback = errorCb
        var releaseFailure: Throwable? = null
        fun recordReleaseFailure(failure: Throwable) {
            releaseFailure?.addSuppressed(failure) ?: run { releaseFailure = failure }
        }
        withContext(NonCancellable) {
            try {
                lifecycleMutex.withLock {
                    try {
                        if (reason == TerminalReason.NaturalEnd) {
                            try {
                                awaitAudioTrackDrain()
                            } catch (failure: Throwable) {
                                recordReleaseFailure(failure)
                            }
                        }
                        // stop() wakes a potentially blocking write before releaseAndJoin() waits
                        // for the codec worker, so AudioTrack.release() never races that write.
                        try {
                            audioOutput.stop()
                        } catch (failure: Throwable) {
                            recordReleaseFailure(failure)
                        }
                        val playbackDecoder = decoder
                        decoder = null
                        try {
                            playbackDecoder?.releaseAndJoin()
                        } catch (failure: Throwable) {
                            recordReleaseFailure(failure)
                        }
                    } finally {
                        try {
                            releaseExternalResources()
                        } catch (cleanupFailure: Throwable) {
                            recordReleaseFailure(cleanupFailure)
                        } finally {
                            // Complete the teardown barrier before invoking client code. A
                            // completion callback may synchronously call stop(), which must return
                            // instead of waiting on itself.
                            val playbackFailure = (reason as? TerminalReason.Failure)?.error
                            val cleanupError = releaseFailure
                            if (playbackFailure != null && cleanupError != null) {
                                playbackFailure.addSuppressed(cleanupError)
                            }
                            terminalFailure.set(playbackFailure ?: cleanupError)
                            terminalCompletion.complete(Unit)
                        }
                    }
                }

                val finalFailure = terminalFailure.get()
                when {
                    // An explicit stop() reports cleanup failures to its caller only.
                    reason == TerminalReason.ExplicitStop -> Unit
                    finalFailure != null -> invokeClientCallback {
                        failureCallback?.invoke(finalFailure)
                    }
                    reason == TerminalReason.NaturalEnd ->
                        invokeClientCallback(completionCallback)
                }
            } finally {
                terminalScope.cancel()
            }
        }
        if (propagateFailure) releaseFailure?.let { throw it }
    }

    private fun invokeClientCallback(callback: (() -> Unit)?) {
        runCatchingPreservingCancellation { callback?.invoke() }
            .onFailure { LogContext.log.e(TAG, "AAC playback callback failed", it) }
    }

    private suspend fun awaitAudioTrackDrain() {
        val drained = withTimeoutOrNull(AUDIO_TRACK_DRAIN_TIMEOUT) {
            while (unsignedPlaybackHeadPosition() < writtenAudioFrames.get()) {
                delay(DRAIN_POLL_INTERVAL)
            }
            true
        } ?: false
        if (!drained) {
            LogContext.log.w(
                TAG,
                "Timed out draining AudioTrack: written=${writtenAudioFrames.get()} " +
                    "played=${unsignedPlaybackHeadPosition()}"
            )
        }
    }

    private fun unsignedPlaybackHeadPosition(): Long =
        audioOutput.playbackHeadPosition.toLong() and 0xFFFF_FFFFL

    private fun releaseExternalResources() {
        val source = sampleSource
        sampleSource = null
        var releaseFailure: Throwable? = null
        try {
            source?.release()
        } catch (failure: Throwable) {
            releaseFailure = failure
        }
        try {
            audioOutput.release()
        } catch (failure: Throwable) {
            releaseFailure?.addSuppressed(failure) ?: run { releaseFailure = failure }
        } finally {
            currentSampleTimeUs = -1
            cb = null
            errorCb = null
        }
        releaseFailure?.let { throw it }
    }
}

internal fun requireSuccessfulAacAudioWrite(writtenBytes: Int): Int {
    check(writtenBytes > 0) { "AudioTrack accepted no AAC PCM bytes: $writtenBytes" }
    return writtenBytes
}

private sealed interface TerminalReason {
    data object ExplicitStop : TerminalReason
    data object NaturalEnd : TerminalReason
    data class Failure(val error: Throwable) : TerminalReason
}

internal data class AacSample(val size: Int, val presentationTimeUs: Long)

internal interface AacSampleSource {
    val mime: String
    val mediaFormat: MediaFormat

    fun readSample(buffer: ByteBuffer): AacSample

    fun release()
}

internal fun interface AacSampleSourceFactory {
    fun open(file: File): AacSampleSource
}

private object AndroidAacSampleSourceFactory : AacSampleSourceFactory {
    override fun open(file: File): AacSampleSource {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    extractor.selectTrack(index)
                    return AndroidAacSampleSource(extractor, mime, format)
                }
            }
            error("AAC file does not contain a supported audio track")
        } catch (error: Throwable) {
            runCatchingPreservingCancellation { extractor.release() }
                .onFailure(error::addSuppressed)
            throw error
        }
    }
}

private class AndroidAacSampleSource(
    private val extractor: MediaExtractor,
    override val mime: String,
    override val mediaFormat: MediaFormat,
) : AacSampleSource {
    override fun readSample(buffer: ByteBuffer): AacSample {
        val presentationTimeUs = extractor.sampleTime
        val size = extractor.readSampleData(buffer, 0)
        if (size >= 0) extractor.advance()
        return AacSample(size, if (size >= 0) presentationTimeUs else -1)
    }

    override fun release() = extractor.release()
}

internal interface AacDecoderSession {
    fun start()

    suspend fun releaseAndJoin()
}

internal fun interface AacDecoderSessionFactory {
    @Suppress("LongParameterList")
    fun create(
        decoderInfo: AudioDecoderInfo,
        readSample: (ByteBuffer) -> Int,
        presentationTimeUs: () -> Long,
        writeOutput: (ByteBuffer, MediaCodec.BufferInfo, Boolean, Boolean) -> Unit,
        onEndOfStream: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ): AacDecoderSession
}
