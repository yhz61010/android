package com.leovp.audio.aac

import com.leovp.audio.base.runCatchingPreservingCancellation

import android.content.Context
import android.media.AudioTrack
import android.os.SystemClock
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.BuildConfig
import com.leovp.audio.base.StreamPlayerStopper
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 20-8-20 下午5:18
 */
class AacStreamPlayer(ctx: Context, private val audioDecoderInfo: AudioDecoderInfo) {
    companion object {
        private const val TAG = "AacStreamPlayer"
        private const val AUDIO_DATA_QUEUE_CAPACITY = 10
        private const val RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES = 3
        private const val AUDIO_INIT_LATENCY_IN_MS = 1200
        private val REASSIGN_LATENCY_TIME_THRESHOLD = 5.seconds
        private const val AUDIO_ALLOW_LATENCY_LIMIT_IN_MS = 500
    }

    private var audioLatencyThresholdInMs = AUDIO_INIT_LATENCY_IN_MS

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    /** Private monitor serializing init/decode/flush/stop. Never lock on `this`. */
    private val lock = Any()

    /** Bumped on every release; used to discard tasks that arrive after teardown. */
    private var generation = 0

    //    private var outputFormat: MediaFormat? = null
    private var frameCount = 0
    private var dropFrameTimes = AtomicLong(0)
    private var playStartTimeInUs: Long = 0

    private val audioTrackPlayer = AudioTrackPlayer(ctx, audioDecoderInfo)
    private var audioDecoder: AacDecoder? = null

    private var csd0: ByteArray? = null
    private val stopped = AtomicBoolean(false)

    private val streamPlayerStopper =
        StreamPlayerStopper(TAG, ioScope, audioTrackPlayer, ::detachDecoderForStop)

    private fun initAudioDecoder(csd0: ByteArray) {
        LogContext.log.i(TAG, "initAudioDecoder: $audioDecoderInfo")
        audioDecoder = AacDecoder(
            audioDecoderInfo.sampleRate,
            audioDecoderInfo.channelCount,
            audioDecoderInfo.audioFormat,
            csd0,
            object : IDecodeCallback {
                override fun onDecoded(pcmData: ByteArray) {
                    // Gated: this fires once per decoded frame, and the message is built before
                    // the log level is ever consulted. Same treatment AudioTrackPlayer and
                    // AudioPlayer already give their per-frame logs.
                    if (BuildConfig.DEBUG) {
                        LogContext.log.i(TAG, "onDecoded PCM[${pcmData.size}]")
                    }
                    if (pcmData.isNotEmpty()) {
                        if (AudioTrack.STATE_UNINITIALIZED == audioTrackPlayer.state) return
                        if (AudioTrack.PLAYSTATE_PLAYING == audioTrackPlayer.playState) {
                            if (BuildConfig.DEBUG) {
                                LogContext.log.i(TAG, "Play PCM[${pcmData.size}]")
                            }
                            // Play decoded audio data in PCM
                            audioTrackPlayer.write(pcmData)
                        }
                    }
                }
            }
        ).apply { start() }
    }

    fun startPlayingStream(audioData: ByteArray, dropFrameCallback: () -> Unit) {
        if (stopped.get()) return
        // dropFrameCallback is invoked OUTSIDE the monitor to avoid callback re-entry deadlocks.
        val shouldResync = synchronized(lock) {
            if (stopped.get()) return
            // We should use a better way to check CSD0
            if (audioData.size < 10) {
                if (audioData.size < 2) {
                    LogContext.log.e(
                        TAG,
                        "Invalid AAC codec configuration: ${audioData.size} bytes"
                    )
                    return
                }
                val incomingCsd = audioData.copyOfRange(audioData.size - 2, audioData.size)
                val activeCsd = csd0
                if (activeCsd != null) {
                    if (activeCsd.contentEquals(incomingCsd)) {
                        LogContext.log.d(TAG, "Ignore duplicate AAC codec configuration")
                    } else {
                        LogContext.log.e(
                            TAG,
                            "Ignore changed AAC codec configuration during an active session"
                        )
                    }
                    return
                }
                initDecoderLocked(audioData)
                return
            }
            val decoder = audioDecoder
            if (csd0 == null || decoder == null) {
                LogContext.log.e(TAG, "csd0 is null. Can not play!")
                return
            }
            decodeOrDropLocked(audioData, decoder)
        }
        if (shouldResync) dropFrameCallback.invoke()
    }

    /**
     * Initialize the decoder under the lock. Only commit [csd0] AFTER decoder init and
     * audioTrack.play() succeed (AUD-5); on failure roll everything back so the invariant
     * "csd0 != null ⇔ decoder ready" holds.
     */
    private fun initDecoderLocked(audioData: ByteArray) {
        frameCount = 0
        val newCsd0 = byteArrayOf(audioData[audioData.size - 2], audioData[audioData.size - 1])
        LogContext.log.w(TAG, "Audio csd0=HEX[${newCsd0.toHexString()}]")
        val capturedGeneration = generation
        runCatchingPreservingCancellation {
            initAudioDecoder(newCsd0)
            audioTrackPlayer.play()
        }.onSuccess {
            csd0 = newCsd0
            playStartTimeInUs = SystemClock.elapsedRealtimeNanos() / 1000
            ioScope.launch {
                delay(REASSIGN_LATENCY_TIME_THRESHOLD)
                synchronized(lock) {
                    if (capturedGeneration == generation) {
                        audioLatencyThresholdInMs = AUDIO_ALLOW_LATENCY_LIMIT_IN_MS
                        LogContext.log.w(
                            TAG,
                            "Change latency limit to $AUDIO_ALLOW_LATENCY_LIMIT_IN_MS"
                        )
                    }
                }
            }
            LogContext.log.w(TAG, "Play audio at: $playStartTimeInUs")
        }.onFailure {
            // Detach before releasing: the rollback below runs asynchronously, so nothing may
            // still reach this half-built decoder through the player's state.
            val failedDecoder = audioDecoder
            audioDecoder = null
            csd0 = null
            releaseDecoderAsync(failedDecoder)
            LogContext.log.e(TAG, "init failed, rolled back. msg=${it.message}", it)
        }
    }

    /**
     * Releases a decoder that failed to initialize, off the caller's thread.
     *
     * The caller holds [lock] and is not suspending, so the deterministic release cannot be
     * awaited here. NonCancellable rather than a plain ioScope child: StreamPlayerStopper
     * cancels ioScope while stopping, and a concurrent stop would otherwise kill this rollback
     * and leak a decoder it has already detached and can no longer reach.
     *
     * The body must not throw. NonCancellable replaces the parent job and this scope carries no
     * CoroutineExceptionHandler, so an escaping throwable would reach the platform default
     * handler and kill the process.
     */
    private fun releaseDecoderAsync(decoder: AacDecoder?) {
        val failed = decoder ?: return
        ioScope.launch(NonCancellable) {
            runCatchingPreservingCancellation { failed.releaseAndJoin() }
                .onFailure { LogContext.log.e(TAG, "rollback release failed", it) }
        }
    }

    /** Decode or drop the current frame under the lock. Returns true if a resync is required. */
    private fun decodeOrDropLocked(audioData: ByteArray, decoder: AacDecoder): Boolean {
        var shouldResync = false
        val elapsedUs = SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs
        val latencyInMs = elapsedUs / 1000 - getAudioTimeUs() / 1000
        // Gated: one call per incoming frame, concatenating four values - including a second
        // getAudioTimeUs(), which reads AudioTrack.playbackHeadPosition through a native call.
        // `cal` reuses elapsedUs instead of re-reading the clock, so the logged value is the one
        // latencyInMs was derived from.
        if (BuildConfig.DEBUG) {
            LogContext.log.d(
                TAG,
                "st=$playStartTimeInUs\t cal=${elapsedUs / 1000}\t " +
                    "play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
            )
        }
        if (decoder.queueSize >= AUDIO_DATA_QUEUE_CAPACITY ||
            abs(latencyInMs) > audioLatencyThresholdInMs
        ) {
            dropFrameTimes.incrementAndGet()
            LogContext.log.w(
                TAG,
                "Drop[${dropFrameTimes.get()}]|full[${decoder.queueSize}] " +
                    "latency[$latencyInMs] play=${getAudioTimeUs() / 1000}"
            )
            frameCount = 0
            runCatchingPreservingCancellation { decoder.flush() }.getOrNull()
            runCatchingPreservingCancellation { audioTrackPlayer.pause() }.getOrNull()
            runCatchingPreservingCancellation { audioTrackPlayer.play() }.getOrNull()
            if (dropFrameTimes.get() >= RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES) {
                // If drop frame times exceeds RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES-1 times, we need
                // to do sync again.
                dropFrameTimes.set(0)
                shouldResync = true
            }
            playStartTimeInUs = SystemClock.elapsedRealtimeNanos() / 1000
        } else {
            decoder.decode(audioData)
        }
        if (frameCount++ % 50 == 0) {
            LogContext.log.i(TAG, "AU[${audioData.size}][$latencyInMs]")
        }
        return shouldResync
    }

    /**
     * Deterministic teardown. Detaches decoder/CSD and advances the generation under the lock,
     * then awaits the old decoder OUTSIDE the lock to avoid suspension or callback re-entry.
     *
     * Replaces the removed non-suspend stopPlaying(). Migration: await this from a coroutine the
     * owner controls; it returns only once the decoder worker has exited and the track is
     * released, where stopPlaying() released the track synchronously but returned without
     * waiting for the decoder worker to exit.
     */
    suspend fun stopPlayingAndJoin() {
        stopped.set(true)
        streamPlayerStopper.stopAndJoin { it.releaseAndJoin() }
    }

    private fun detachDecoderForStop(): AacDecoder? = synchronized(lock) {
        stopped.set(true)
        generation++
        val old = audioDecoder
        audioDecoder = null
        csd0 = null
        frameCount = 0
        dropFrameTimes.set(0)
        old
    }

    @Suppress("unused")
    fun getPlayState() = audioTrackPlayer.playState

    private fun getAudioTimeUs(): Long = runCatchingPreservingCancellation {
        val numFramesPlayed: Int = audioTrackPlayer.playbackHeadPosition
        numFramesPlayed * 1_000_000L / audioDecoderInfo.sampleRate
    }.getOrDefault(0L)
}
