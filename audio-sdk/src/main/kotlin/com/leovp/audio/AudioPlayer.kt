@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio

import android.content.Context
import android.media.AudioTrack
import android.os.SystemClock
import com.leovp.audio.aac.AacStreamPlayer
import com.leovp.audio.base.AudioDecoderManager
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.AudioDecoderWrapper
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.audio.opus.OpusStreamPlayer
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2020/9/16 下午5:03
 */
class AudioPlayer(
    ctx: Context,
    private val audioDecoderInfo: AudioDecoderInfo,
    private val type: AudioType = AudioType.COMPRESSED_PCM,
    minPlayBufferSizeRatio: Int = 1) {
    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var decoderWrapper: AudioDecoderWrapper? = null
    private var aacStreamPlayer: AacStreamPlayer? = null
    private var opusStreamPlayer: OpusStreamPlayer? = null

    private var audioTrackPlayer: AudioTrackPlayer? = null
    private val audioTrackPlayerRef: AudioTrackPlayer by lazy { audioTrackPlayer!! }

    init {
        when (type) {
            AudioType.AAC -> {
                LogContext.log.w(TAG, "AAC Decoder")
                aacStreamPlayer = AacStreamPlayer(ctx, audioDecoderInfo)
            }

            AudioType.OPUS -> {
                LogContext.log.w(TAG, "OPUS Decoder")
                opusStreamPlayer = OpusStreamPlayer(ctx, audioDecoderInfo)
            }

            else -> {
                audioTrackPlayer = AudioTrackPlayer(ctx, audioDecoderInfo, minPlayBufferSizeRatio)
                decoderWrapper = AudioDecoderManager.getWrapper(
                    type,
                    audioDecoderInfo,
                    object : OutputCallback {
                        override fun output(out: ByteArray) {
                            val st = SystemClock.elapsedRealtime()
                            audioTrackPlayerRef.play(out)
                            if (BuildConfig.DEBUG) LogContext.log.d(TAG,
                                "Play audio[${out.size}] cost=${SystemClock.elapsedRealtime() - st}")
                        }
                    }
                )
                LogContext.log.w(TAG, "decoderWrapper=$decoderWrapper")
            }
        }
    }

    fun play(chunkAudioData: ByteArray) {
        try {
            if (type == AudioType.PCM || type == AudioType.COMPRESSED_PCM) {
                if (AudioTrack.STATE_UNINITIALIZED == audioTrackPlayerRef.getPlayState()) {
                    return
                }
                if (AudioTrack.PLAYSTATE_PLAYING == audioTrackPlayerRef.getPlayState()) {
                    if (type == AudioType.PCM) {
                        // Play decoded audio data in PCM
                        audioTrackPlayerRef.play(chunkAudioData)
                    } else {
                        decoderWrapper?.decode(chunkAudioData)
                    }
                }
            } else {
                when (type) {
                    AudioType.AAC -> aacStreamPlayer?.startPlayingStream(chunkAudioData) {
                        LogContext.log.w(TAG, "AAC Drop audio frame")
                    }

                    AudioType.OPUS -> opusStreamPlayer?.startPlayingStream(chunkAudioData) {
                        LogContext.log.w(TAG, "Opus Drop audio frame")
                    }

                    else -> Unit
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * After [pause] audio, this method **MUST** be called if you want to play audio again.
     *
     * Example:
     * ```kotlin
     * pcmPlayer.pause()
     * // Do anything what you want
     * pcmPlayer.resume()
     * pcmPlayer.play(pcmDataBytes)
     * ```
     */
    fun resume() {
        LogContext.log.w(TAG, "resume()")
        audioTrackPlayer?.resume()
    }

    /**
     * After [pause] audio, if you want to play audio again [resume] method **MUST** be called.
     *
     * Example:
     * ```kotlin
     * pcmPlayer.pause()
     * // Do anything what you want
     * pcmPlayer.resume()
     * pcmPlayer.play(pcmDataBytes)
     * ```
     */
    fun pause() {
        LogContext.log.w(TAG, "pause()")
        audioTrackPlayer?.pause()
    }

    /**
     * After [stop] audio, if you want to play audio again [resume] method **MUST** be called.
     *
     * Example:
     * ```kotlin
     * pcmPlayer.stop()
     * // Do anything what you want
     * pcmPlayer.resume()
     * pcmPlayer.play(pcmDataBytes)
     * ```
     */
    fun stop() {
        LogContext.log.w(TAG, "stop()")
        audioTrackPlayer?.stop()
    }

    fun release() {
        LogContext.log.w(TAG, "release()")
        audioTrackPlayer?.release()

        decoderWrapper?.release()
        aacStreamPlayer?.stopPlaying()
        opusStreamPlayer?.stopPlaying()
    }

    fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / audioDecoderInfo.sampleRate

    fun getAudioTimeUs(): Long = runCatching {
        val numFramesPlayed: Int = audioTrackPlayer?.getPlaybackHeadPosition() ?: 0
        numFramesPlayed * 1_000_000L / audioDecoderInfo.sampleRate
    }.getOrDefault(0L)
}
