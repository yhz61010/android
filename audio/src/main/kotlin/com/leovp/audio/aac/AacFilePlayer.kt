package com.leovp.audio.aac

import com.leovp.audio.base.runCatchingPreservingCancellation

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.mediacodec.BaseMediaCodecSynchronous
import com.leovp.bytes.toByteArray
import com.leovp.log.LogContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
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
 * Author: Michael Leo
 * Date: 2020/9/17 下午5:01
 */
class AacFilePlayer(
    ctx: Context,
    private val audioDecodeInfo: AudioDecoderInfo,
    // AudioAttributes.USAGE_VOICE_COMMUNICATION  AudioAttributes.USAGE_MEDIA
    usage: Int = AudioAttributes.USAGE_MEDIA,
    // AudioAttributes.CONTENT_TYPE_SPEECH  AudioAttributes.CONTENT_TYPE_MUSIC
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
) : BaseMediaCodecSynchronous(
    MediaFormat.MIMETYPE_AUDIO_AAC,
    audioDecodeInfo.sampleRate,
    audioDecodeInfo.channelCount
) {
    companion object {
        private const val TAG = "AacFilePlayer"
        private const val AUDIO_TRACK_DRAIN_TIMEOUT_MS = 3_000L
    }

    private val audioTrackPlayer: AudioTrackPlayer =
        AudioTrackPlayer(ctx, audioDecodeInfo, usage = usage, contentType = contentType)

    private var mediaFormat: MediaFormat? = null
    private var mime: String? = null
    private var mediaExtractor: MediaExtractor? = null
    private var currentSampleTimeUs = -1L
    private val writtenAudioFrames = AtomicLong(0)

    private var cb: (() -> Unit)? = null
    private val started = AtomicBoolean(false)
    private val terminalStarted = AtomicBoolean(false)
    private val terminalCompletion = CompletableDeferred<Unit>()
    private val terminalScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("aac-file-player-terminal")
    )
    private val lifecycleMutex = Mutex()

    override fun setFormatOptions(format: MediaFormat) {}

    override fun createMediaFormat() {}

    override fun createCodec() {
        val codecMime = requireNotNull(mime) { "AAC mime is not initialized" }
        val codecFormat = requireNotNull(mediaFormat) { "AAC format is not initialized" }
        codec = MediaCodec.createDecoderByType(codecMime)
        codec.configure(codecFormat, null, null, 0)
        // LogContext.log.w(TAG, "mediaFormat=$mediaFormat")
    }

    override fun onInputData(inBuf: ByteBuffer): Int {
        val extractor = mediaExtractor
        currentSampleTimeUs = extractor?.sampleTime ?: -1
        val sampleSize = extractor?.readSampleData(inBuf, 0) ?: -1
        if (sampleSize > -1) {
            extractor?.advance()
        } else {
            currentSampleTimeUs = -1
            LogContext.log.d(TAG, "readSampleData sampleSize=$sampleSize")
        }
        return sampleSize
    }

    override fun onOutputData(
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
            val writtenBytes = audioTrackPlayer.write(chunkPCM)
            if (writtenBytes > 0) {
                val pcmFrameBytes = Short.SIZE_BYTES * audioDecodeInfo.channelCount
                writtenAudioFrames.addAndGet((writtenBytes / pcmFrameBytes).toLong())
            }
        }
    }

    override fun computePresentationTimeUs(): Long = currentSampleTimeUs

    override fun onEndOfStream() {
        // Teardown must run outside the codec worker; releaseAndJoin() cannot join its caller.
        terminalScope.launch { finishPlayback(notifyCompletion = true) }
    }

    /**
     * Starts this one-shot player.
     *
     * Initialization failures are cleaned up and then rethrown to the caller. Calling this method
     * more than once always fails before any active playback state is changed.
     *
     * @throws IllegalStateException if playback was already started or the file has no AAC track.
     * @throws Exception if the extractor, AudioTrack, or MediaCodec cannot be initialized.
     */
    suspend fun playAac(aacFile: File, endCallback: () -> Unit) {
        check(started.compareAndSet(false, true)) { "AacFilePlayer can only be started once" }
        cb = endCallback
        try {
            withContext(Dispatchers.IO) {
                lifecycleMutex.withLock {
                    check(!terminalStarted.get()) { "AacFilePlayer is stopping" }
                    val extractor = MediaExtractor()
                    mediaExtractor = extractor
                    extractor.setDataSource(aacFile.absolutePath)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        mime = format.getString(MediaFormat.KEY_MIME)
                        if (mime?.startsWith("audio/") == true) {
                            extractor.selectTrack(i)
                            mediaFormat = format
                            break
                        }
                    }
                    check(mediaFormat != null && !mime.isNullOrBlank()) {
                        "AAC file does not contain a supported audio track"
                    }
                    audioTrackPlayer.play()
                    start()
                }
            }
        } catch (e: Exception) {
            LogContext.log.e(TAG, "AAC playback start failed", e)
            try {
                finishPlayback(notifyCompletion = false)
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
        withContext(Dispatchers.IO) { finishPlayback(notifyCompletion = false) }
    }

    private suspend fun finishPlayback(notifyCompletion: Boolean) {
        if (!terminalStarted.compareAndSet(false, true)) {
            terminalCompletion.await()
            return
        }

        val completionCallback = if (notifyCompletion) cb else null
        var releaseFailure: Throwable? = null
        withContext(NonCancellable) {
            try {
                lifecycleMutex.withLock {
                    try {
                        if (notifyCompletion) awaitAudioTrackDrain()
                        // stop() wakes a potentially blocking write before releaseAndJoin() waits
                        // for the codec worker, so AudioTrack.release() never races that write.
                        audioTrackPlayer.stop()
                        releaseAndJoin()
                    } catch (failure: Throwable) {
                        releaseFailure = failure
                    } finally {
                        try {
                            releaseExternalResources()
                        } catch (cleanupFailure: Throwable) {
                            releaseFailure?.addSuppressed(cleanupFailure)
                                ?: run { releaseFailure = cleanupFailure }
                        } finally {
                            // Complete the teardown barrier before invoking client code. A
                            // completion callback may synchronously call stop(), which must return
                            // instead of waiting on itself.
                            terminalCompletion.complete(Unit)
                        }
                    }
                }

                runCatchingPreservingCancellation { completionCallback?.invoke() }
                    .onFailure { LogContext.log.e(TAG, "AAC completion callback failed", it) }
            } finally {
                terminalScope.cancel()
            }
        }
        releaseFailure?.let { throw it }
    }

    private suspend fun awaitAudioTrackDrain() {
        val drained = withTimeoutOrNull(AUDIO_TRACK_DRAIN_TIMEOUT_MS) {
            while (unsignedPlaybackHeadPosition() < writtenAudioFrames.get()) {
                delay(20)
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
        audioTrackPlayer.playbackHeadPosition.toLong() and 0xFFFF_FFFFL

    private fun releaseExternalResources() {
        val extractor = mediaExtractor
        mediaExtractor = null
        runCatchingPreservingCancellation { extractor?.release() }
            .onFailure { LogContext.log.e(TAG, "MediaExtractor release failed", it) }
        audioTrackPlayer.release()
        currentSampleTimeUs = -1
        cb = null
    }
}
