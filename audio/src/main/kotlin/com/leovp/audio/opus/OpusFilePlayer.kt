package com.leovp.audio.opus

import android.content.Context
import android.media.AudioAttributes
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.audio.base.runCatchingPreservingCancellation
import com.leovp.audio.mediacodec.bean.OpusCsd
import com.leovp.audio.mediacodec.utils.AudioCodecUtil
import com.leovp.bytes.readLongLE
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot OPUS file player with deterministic codec and AudioTrack ownership.
 *
 * Author: Michael Leo
 * Date: 2023/5/5 16:03
 */
class OpusFilePlayer(
    ctx: Context,
    private val audioDecoderInfo: AudioDecoderInfo,
    // AudioAttributes.USAGE_VOICE_COMMUNICATION  AudioAttributes.USAGE_MEDIA
    usage: Int = AudioAttributes.USAGE_MEDIA,
    // AudioAttributes.CONTENT_TYPE_SPEECH  AudioAttributes.CONTENT_TYPE_MUSIC
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
) {
    companion object {
        private const val TAG = "OpusFilePlayer"
        private const val DECODED_QUEUE_CAPACITY = 64
        private const val OUTPUT_DRAIN_TIMEOUT_MS = 3_000L
        private const val AUDIO_TRACK_DRAIN_TIMEOUT_MS = 3_000L
        private const val QUEUE_LOG_INTERVAL_FRAMES = 50L
        private const val INPUT_RETRY_DELAY_MS = 5L
        const val START_CODE = "|leo|"
    }

    private val queue = ArrayBlockingQueue<ByteArray>(DECODED_QUEUE_CAPACITY)
    private val playbackScopeJob = SupervisorJob()
    private val ioScope = CoroutineScope(
        playbackScopeJob + Dispatchers.IO + CoroutineName("opus-file-player")
    )
    private val terminalScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("opus-file-player-terminal")
    )
    private val lifecycleMutex = Mutex()

    private val audioTrackPlayer =
        AudioTrackPlayer(ctx, audioDecoderInfo, usage = usage, contentType = contentType)
    private val inputFile = AtomicReference<RandomAccessFile?>(null)
    private var decoder: OpusDecoder? = null
    private var completionCallback: (() -> Unit)? = null
    private var failureCallback: ((Throwable) -> Unit)? = null

    private val started = AtomicBoolean(false)
    private val terminalStarted = AtomicBoolean(false)
    private val terminalCompletion = CompletableDeferred<Unit>()
    private val codecEos = CompletableDeferred<Unit>()
    private val queuedPcmCount = AtomicLong(0)
    private val consumedPcmCount = AtomicLong(0)
    private val writtenAudioFrames = AtomicLong(0)
    private val droppedPcmCount = AtomicLong(0)

    /**
     * Initializes and starts this one-shot player on [Dispatchers.IO].
     *
     * The function returns after playback jobs have started. Initialization failures are cleaned
     * up and rethrown. Errors that happen later are delivered through [errorCallback] after all
     * player resources have been released.
     */
    suspend fun playOpus(
        opusFile: File,
        endCallback: () -> Unit,
        errorCallback: (Throwable) -> Unit = {},
    ) {
        check(started.compareAndSet(false, true)) { "OpusFilePlayer can only be started once" }
        completionCallback = endCallback
        failureCallback = errorCallback

        try {
            withContext(Dispatchers.IO) {
                lifecycleMutex.withLock {
                    check(!terminalStarted.get()) { "OpusFilePlayer is stopping" }
                    val playbackInput = createPlaybackInput(opusFile)
                    val playbackDecoder = createDecoder(playbackInput.opusCsd)
                    decoder = playbackDecoder
                    playbackDecoder.start()
                    audioTrackPlayer.play()
                    launchPlaybackJobs(playbackInput, playbackDecoder)
                }
            }
        } catch (e: Exception) {
            try {
                finishPlayback(TerminalReason.ExplicitStop)
            } catch (cleanupFailure: Throwable) {
                e.addSuppressed(cleanupFailure)
            }
            throw e
        }
    }

    /** Stops playback and waits until every owned job and native resource has been released. */
    suspend fun stop() {
        withContext(Dispatchers.IO) { finishPlayback(TerminalReason.ExplicitStop) }
    }

    private fun createPlaybackInput(opusFile: File): OpusPlaybackInput {
        val file = RandomAccessFile(opusFile, "r")
        check(inputFile.compareAndSet(null, file)) { "OPUS input file is already open" }
        LogContext.log.w(TAG, "File length=${file.length()}")
        val framedFileReader = OpusFramedFileReader(file, START_CODE.encodeToByteArray())
        val csdPayload = framedFileReader.readPayload(0)
        val firstAudioFramePosition = requireNotNull(csdPayload.nextStartCodePosition) {
            "OPUS file does not contain an audio frame"
        }
        val opusCsd = requireNotNull(
            AudioCodecUtil.parseOpusConfigFrame(csdPayload.data, ByteOrder.LITTLE_ENDIAN)
        ) { "Invalid OPUS codec configuration" }
        LogContext.log.w(TAG, "csd0=${opusCsd.csd0.toHexString()}")
        LogContext.log.w(TAG, "csd1=${opusCsd.csd1.readLongLE()} ${opusCsd.csd1.toHexString()}")
        LogContext.log.w(TAG, "csd2=${opusCsd.csd2.readLongLE()} ${opusCsd.csd2.toHexString()}")
        return OpusPlaybackInput(framedFileReader, firstAudioFramePosition, opusCsd)
    }

    private fun createDecoder(opusCsd: OpusCsd): OpusDecoder = OpusDecoder(
        sampleRate = audioDecoderInfo.sampleRate,
        channelCount = audioDecoderInfo.channelCount,
        audioFormat = audioDecoderInfo.audioFormat,
        csd0 = opusCsd.csd0,
        csd1 = opusCsd.csd1,
        csd2 = opusCsd.csd2,
        callback = object : IDecodeCallback {
            override fun onDecoded(pcmData: ByteArray) {
                if (pcmData.isEmpty() || terminalStarted.get()) return
                if (queue.offer(pcmData)) {
                    queuedPcmCount.incrementAndGet()
                    return
                }
                val dropped = droppedPcmCount.incrementAndGet()
                if (dropped == 1L || dropped % QUEUE_LOG_INTERVAL_FRAMES == 0L) {
                    LogContext.log.w(TAG, "Drop decoded PCM: queue is full; dropped=$dropped")
                }
            }
        },
        endCallback = { codecEos.complete(Unit) },
        errorCallback = { requestFailure(it) }
    )

    private fun launchPlaybackJobs(input: OpusPlaybackInput, playbackDecoder: OpusDecoder) {
        ioScope.launch { produceDecoderInput(input, playbackDecoder) }
        ioScope.launch { consumeDecodedPcm() }
        ioScope.launch { awaitNaturalCompletion() }
    }

    private suspend fun produceDecoderInput(
        input: OpusPlaybackInput,
        playbackDecoder: OpusDecoder,
    ) {
        var startCodeBeginPos = input.firstAudioFramePosition
        var submittedFrameCount = 0L
        val maxFrameSizeMs = 20L
        val baseDelay = 10L
        val maxFrameSize = audioDecoderInfo.sampleRate / 1_000L * maxFrameSizeMs
        var frame = 0L
        var calcDelay = baseDelay
        var delayChanged = false

        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val payload = input.reader.readPayload(startCodeBeginPos)
                require(payload.data.isNotEmpty()) { "Empty OPUS audio frame" }
                submitWithBackpressure(playbackDecoder, payload.data)
                submittedFrameCount++

                val delayMs = calcDelay.coerceAtMost(maxFrameSizeMs)
                if (queue.isEmpty()) {
                    calcDelay = baseDelay
                    delayChanged = false
                    frame = 0
                } else if (queue.size in 1..10) {
                    delayChanged = false
                    frame = 0
                } else {
                    if (!delayChanged) {
                        delayChanged = true
                        calcDelay++
                    }
                    if (++frame % (maxFrameSize / maxFrameSizeMs).coerceAtLeast(1L) == 0L) {
                        delayChanged = false
                    }
                }
                if (submittedFrameCount % QUEUE_LOG_INTERVAL_FRAMES == 0L) {
                    LogContext.log.d(
                        TAG,
                        "queue[${queue.size}] delay=$delayMs delayChanged=$delayChanged " +
                            "maxFrameSize=$maxFrameSize frame=$frame"
                    )
                }
                delay(delayMs)
                startCodeBeginPos = payload.nextStartCodePosition ?: break
            }
            signalEndOfStreamWithBackpressure(playbackDecoder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!terminalStarted.get()) {
                LogContext.log.e(TAG, "Decode OPUS file failed", e)
                requestFailure(e)
            }
        } finally {
            closeInputFile()
        }
    }

    private suspend fun submitWithBackpressure(decoder: OpusDecoder, data: ByteArray) {
        while (!decoder.decode(data)) {
            currentCoroutineContext().ensureActive()
            check(decoder.isAcceptingInput) { "OPUS decoder is not accepting input" }
            delay(INPUT_RETRY_DELAY_MS)
        }
    }

    private suspend fun signalEndOfStreamWithBackpressure(decoder: OpusDecoder) {
        while (!decoder.signalEndOfStream()) {
            currentCoroutineContext().ensureActive()
            check(decoder.isAcceptingInput) { "OPUS decoder stopped before accepting EOS" }
            delay(INPUT_RETRY_DELAY_MS)
        }
    }

    private suspend fun consumeDecodedPcm() {
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val pcmData = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val writtenBytes = audioTrackPlayer.write(pcmData)
                check(writtenBytes >= 0) { "AudioTrack write failed with code $writtenBytes" }
                consumedPcmCount.incrementAndGet()
                writtenAudioFrames.addAndGet((writtenBytes / pcmFrameBytes()).toLong())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!terminalStarted.get()) {
                LogContext.log.e(TAG, "Play decoded OPUS audio failed", e)
                requestFailure(e)
            }
        }
    }

    private suspend fun awaitNaturalCompletion() {
        codecEos.await()
        val softwareDrained = withTimeoutOrNull(OUTPUT_DRAIN_TIMEOUT_MS) {
            while (
                queue.isNotEmpty() || consumedPcmCount.get() < queuedPcmCount.get()
            ) {
                delay(20)
            }
            true
        } ?: false
        if (!softwareDrained) {
            LogContext.log.w(
                TAG,
                "Timed out draining decoded OPUS PCM: queued=${queuedPcmCount.get()} " +
                    "consumed=${consumedPcmCount.get()} queue=${queue.size}"
            )
        }

        val hardwareDrained = awaitAudioTrackDrain()
        if (!hardwareDrained) {
            LogContext.log.w(
                TAG,
                "Timed out draining AudioTrack: written=${writtenAudioFrames.get()} " +
                    "played=${unsignedPlaybackHeadPosition()}"
            )
        }
        LogContext.log.i(
            TAG,
            "Playback completed: queued=${queuedPcmCount.get()} " +
                "consumed=${consumedPcmCount.get()} dropped=${droppedPcmCount.get()}"
        )
        terminalScope.launch { finishPlayback(TerminalReason.NaturalEnd) }
    }

    private suspend fun awaitAudioTrackDrain(): Boolean =
        withTimeoutOrNull(AUDIO_TRACK_DRAIN_TIMEOUT_MS) {
            while (unsignedPlaybackHeadPosition() < writtenAudioFrames.get()) delay(20)
            true
        } ?: false

    private fun unsignedPlaybackHeadPosition(): Long =
        audioTrackPlayer.playbackHeadPosition.toLong() and 0xFFFF_FFFFL

    private fun pcmFrameBytes(): Int = Short.SIZE_BYTES * audioDecoderInfo.channelCount

    private fun requestFailure(error: Throwable) {
        if (terminalStarted.get()) return
        terminalScope.launch { finishPlayback(TerminalReason.Failure(error)) }
    }

    private suspend fun finishPlayback(reason: TerminalReason) {
        if (!terminalStarted.compareAndSet(false, true)) {
            terminalCompletion.await()
            return
        }

        val onCompletion = completionCallback
        val onFailure = failureCallback
        var releaseFailure: Throwable? = null
        withContext(NonCancellable) {
            try {
                lifecycleMutex.withLock {
                    try {
                        // Stop producers first, close input to wake reads, then stop AudioTrack to
                        // wake a blocking write. Native resources are released only after all jobs
                        // exit.
                        playbackScopeJob.cancel()
                        closeInputFile()
                        audioTrackPlayer.stop()
                        playbackScopeJob.cancelAndJoin()
                        val playbackDecoder = decoder
                        decoder = null
                        playbackDecoder?.releaseAndJoin()
                    } catch (failure: Throwable) {
                        releaseFailure = failure
                    } finally {
                        try {
                            queue.clear()
                            audioTrackPlayer.release()
                        } catch (cleanupFailure: Throwable) {
                            releaseFailure?.addSuppressed(cleanupFailure)
                                ?: run { releaseFailure = cleanupFailure }
                        } finally {
                            completionCallback = null
                            failureCallback = null
                            terminalCompletion.complete(Unit)
                        }
                    }
                }

                when (reason) {
                    TerminalReason.NaturalEnd -> invokeClientCallback(onCompletion)
                    is TerminalReason.Failure -> invokeClientCallback {
                        onFailure?.invoke(reason.error)
                    }
                    TerminalReason.ExplicitStop -> Unit
                }
            } finally {
                terminalScope.cancel()
            }
        }
        releaseFailure?.let { throw it }
    }

    private fun invokeClientCallback(callback: (() -> Unit)?) {
        runCatchingPreservingCancellation { callback?.invoke() }
            .onFailure { LogContext.log.e(TAG, "OPUS playback callback failed", it) }
    }

    private fun closeInputFile() {
        val file = inputFile.getAndSet(null) ?: return
        try {
            file.close()
        } catch (e: IOException) {
            LogContext.log.e(TAG, "Close OPUS input failed", e)
        }
    }
}

private data class OpusPlaybackInput(
    val reader: OpusFramedFileReader,
    val firstAudioFramePosition: Long,
    val opusCsd: OpusCsd,
)

private sealed interface TerminalReason {
    data object ExplicitStop : TerminalReason
    data object NaturalEnd : TerminalReason
    data class Failure(val error: Throwable) : TerminalReason
}
