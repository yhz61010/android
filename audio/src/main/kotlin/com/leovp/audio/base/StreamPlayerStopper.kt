package com.leovp.audio.base

import com.leovp.audio.AudioTrackPlayer
import com.leovp.log.LogContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/** Coordinates the shared teardown order for stream players without exposing a public API. */
internal class StreamPlayerStopper<Decoder>(
    private val tag: String,
    private val ioScope: CoroutineScope,
    private val audioTrackPlayer: AudioTrackPlayer,
    private val detachDecoder: () -> Decoder?,
) {
    fun stop(releaseDecoder: (Decoder) -> Unit) {
        val decoder = prepareStop()
        runCatching { decoder?.let(releaseDecoder) }
            .onFailure { LogContext.log.e(tag, "audioDecoder release error", it) }
        LogContext.log.w(tag, "stopPlaying() done")
    }

    suspend fun stopAndJoin(releaseDecoderAndJoin: suspend (Decoder) -> Unit) {
        val decoder = prepareStop()
        decoder?.let { dec ->
            try {
                releaseDecoderAndJoin(dec)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogContext.log.e(tag, "audioDecoder release error. msg=${e.message}", e)
            }
        }
        LogContext.log.w(tag, "stopPlaying() done")
    }

    private fun prepareStop(): Decoder? {
        LogContext.log.w(tag, "Stop playing audio")
        val decoder = detachDecoder()
        ioScope.cancel()
        runCatching { audioTrackPlayer.release() }
            .onFailure {
                LogContext.log.e(tag, "audioTrack release error. msg=${it.message}", it)
            }
        LogContext.log.w(tag, "Releasing AudioDecoder...")
        return decoder
    }
}
