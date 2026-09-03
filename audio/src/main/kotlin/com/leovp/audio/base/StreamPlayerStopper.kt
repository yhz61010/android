package com.leovp.audio.base

import com.leovp.audio.AudioTrackPlayer
import com.leovp.log.LogContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext

/** Coordinates the shared teardown order for stream players without exposing a public API. */
internal class StreamPlayerStopper<Decoder>(
    private val tag: String,
    private val ioScope: CoroutineScope,
    private val audioTrackPlayer: AudioTrackPlayer,
    private val detachDecoder: () -> Decoder?,
) {
    fun stop(releaseDecoder: (Decoder) -> Unit) {
        val decoder = prepareStop()
        try {
            runCatchingPreservingCancellation { decoder?.let(releaseDecoder) }
                .onFailure { LogContext.log.e(tag, "audioDecoder release error", it) }
        } finally {
            releaseAudioTrack()
        }
        LogContext.log.w(tag, "stopPlaying() done")
    }

    suspend fun stopAndJoin(releaseDecoderAndJoin: suspend (Decoder) -> Unit) {
        withContext(NonCancellable) {
            val decoder = prepareStop()
            try {
                ioScope.coroutineContext[Job]?.cancelAndJoin()
                decoder?.let { releaseDecoderAndJoin(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogContext.log.e(tag, "audioDecoder release error. msg=${e.message}", e)
            } finally {
                releaseAudioTrack()
            }
        }
        LogContext.log.w(tag, "stopPlaying() done")
    }

    private fun prepareStop(): Decoder? {
        LogContext.log.w(tag, "Stop playing audio")
        val decoder = detachDecoder()
        ioScope.cancel()
        // Stop first to wake any blocking write. Release only after workers and decoder callbacks
        // can no longer access the track.
        runCatchingPreservingCancellation { audioTrackPlayer.stop() }
            .onFailure {
                LogContext.log.e(tag, "audioTrack stop error. msg=${it.message}", it)
            }
        LogContext.log.w(tag, "Releasing AudioDecoder...")
        return decoder
    }

    private fun releaseAudioTrack() {
        runCatchingPreservingCancellation { audioTrackPlayer.release() }
            .onFailure {
                LogContext.log.e(tag, "audioTrack release error. msg=${it.message}", it)
            }
    }
}
