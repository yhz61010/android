package com.leovp.audio.aac

import android.content.Context
import android.media.AudioTrack
import android.os.SystemClock
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
        private const val REASSIGN_LATENCY_TIME_THRESHOLD_IN_MS: Long = 5000
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

    private fun initAudioDecoder(csd0: ByteArray) {
        LogContext.log.i(TAG, "initAudioDecoder: $audioDecoderInfo")
        audioDecoder = AacDecoder(
            audioDecoderInfo.sampleRate,
            audioDecoderInfo.channelCount,
            audioDecoderInfo.audioFormat,
            csd0,
            object : IDecodeCallback {
                override fun onDecoded(pcmData: ByteArray) {
                    LogContext.log.i(TAG, "onDecoded PCM[${pcmData.size}]")
                    if (pcmData.isNotEmpty()) {
                        if (AudioTrack.STATE_UNINITIALIZED == audioTrackPlayer.state) return
                        if (AudioTrack.PLAYSTATE_PLAYING == audioTrackPlayer.playState) {
                            LogContext.log.i(TAG, "Play PCM[${pcmData.size}]")
                            // Play decoded audio data in PCM
                            audioTrackPlayer.write(pcmData)
                        }
                    }
                }
            }
        ).apply { start() }
    }

    fun startPlayingStream(audioData: ByteArray, dropFrameCallback: () -> Unit) {
        // dropFrameCallback is invoked OUTSIDE the monitor to avoid callback re-entry deadlocks.
        val shouldResync = synchronized(lock) {
            // We should use a better way to check CSD0
            if (audioData.size < 10) {
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
        runCatching {
            initAudioDecoder(newCsd0)
            audioTrackPlayer.play()
        }.onSuccess {
            csd0 = newCsd0
            playStartTimeInUs = SystemClock.elapsedRealtimeNanos() / 1000
            ioScope.launch {
                delay(REASSIGN_LATENCY_TIME_THRESHOLD_IN_MS)
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
            runCatching { audioDecoder?.release() }
            audioDecoder = null
            csd0 = null
            LogContext.log.e(TAG, "init failed, rolled back. msg=${it.message}", it)
        }
    }

    /** Decode or drop the current frame under the lock. Returns true if a resync is required. */
    private fun decodeOrDropLocked(audioData: ByteArray, decoder: AacDecoder): Boolean {
        var shouldResync = false
        val latencyInMs =
            (
                SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs
                ) / 1000 - getAudioTimeUs() / 1000
        LogContext.log.d(
            TAG,
            "st=$playStartTimeInUs\t " +
                "cal=${(SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000}\t " +
                "play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
        )
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
            runCatching { decoder.flush() }.getOrNull()
            runCatching { audioTrackPlayer.pause() }.getOrNull()
            runCatching { audioTrackPlayer.play() }.getOrNull()
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
     */
    suspend fun stopPlayingAndJoin() {
        LogContext.log.w(TAG, "Stop playing audio")
        val decoderToRelease = detachDecoderForStop()
        ioScope.cancel()
        releaseAudioTrack()

        LogContext.log.w(TAG, "Releasing AudioDecoder...")
        decoderToRelease?.let { dec ->
            try {
                dec.releaseAndJoin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogContext.log.e(TAG, "audioDecoder release error. msg=${e.message}", e)
            }
        }
        LogContext.log.w(TAG, "stopPlaying() done")
    }

    /**
     * Legacy non-suspend entry point. It preserves synchronous resource release, but cannot wait
     * for the decoder worker to finish; use [stopPlayingAndJoin] when completion matters.
     */
    @Deprecated(
        "Non-suspend stop cannot guarantee the decoder has been released. " +
            "Use stopPlayingAndJoin() for deterministic shutdown.",
        ReplaceWith("stopPlayingAndJoin()")
    )
    fun stopPlaying() {
        LogContext.log.w(TAG, "Stop playing audio")
        val decoderToRelease = detachDecoderForStop()
        ioScope.cancel()
        releaseAudioTrack()
        runCatching { decoderToRelease?.release() }
            .onFailure { LogContext.log.e(TAG, "audioDecoder release error", it) }
        LogContext.log.w(TAG, "stopPlaying() done")
    }

    private fun detachDecoderForStop(): AacDecoder? = synchronized(lock) {
        generation++
        val old = audioDecoder
        audioDecoder = null
        csd0 = null
        frameCount = 0
        dropFrameTimes.set(0)
        old
    }

    private fun releaseAudioTrack() {
        runCatching { audioTrackPlayer.release() }
            .onFailure { LogContext.log.e(TAG, "audioTrack release error. msg=${it.message}", it) }
    }

    @Suppress("unused")
    fun getPlayState() = audioTrackPlayer.playState

    private fun getAudioTimeUs(): Long = runCatching {
        val numFramesPlayed: Int = audioTrackPlayer.playbackHeadPosition
        numFramesPlayed * 1_000_000L / audioDecoderInfo.sampleRate
    }.getOrDefault(0L)
}
