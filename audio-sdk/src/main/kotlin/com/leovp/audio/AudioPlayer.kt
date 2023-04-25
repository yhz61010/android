@file:Suppress("unused")

package com.leovp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import com.leovp.audio.aac.AacStreamPlayer
import com.leovp.audio.base.AudioDecoderManager
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.AudioDecoderWrapper
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.audio.opus.OpusStreamPlayer
import com.leovp.bytes.toShortArrayLE
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2020/9/16 下午5:03
 */
class AudioPlayer(
    ctx: Context,
    private val audioDecoderInfo: AudioDecoderInfo,
    private val type: AudioType = AudioType.COMPRESSED_PCM,
    minPlayBufferSizeRatio: Int = 1
) {
    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var decoderWrapper: AudioDecoderWrapper? = null
    private var aacStreamPlayer: AacStreamPlayer? = null
    private var opusStreamPlayer: OpusStreamPlayer? = null

    private var audioManager: AudioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack

    init {
        val minBufferSize = AudioTrack.getMinBufferSize(
            audioDecoderInfo.sampleRate,
            audioDecoderInfo.channelConfig,
            audioDecoderInfo.audioFormat
        ) * minPlayBufferSizeRatio
        LogContext.log.w(TAG, "PCM Codec=$audioDecoderInfo minPlayBufferSizeRatio=$minPlayBufferSizeRatio minBufferSize=$minBufferSize")
        val sessionId = audioManager.generateAudioSessionId()
        val audioAttributesBuilder = AudioAttributes.Builder().apply {
            setUsage(AudioAttributes.USAGE_MEDIA) // AudioAttributes.USAGE_MEDIA          AudioAttributes.USAGE_VOICE_COMMUNICATION
            setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // AudioAttributes.CONTENT_TYPE_MUSIC   AudioAttributes.CONTENT_TYPE_SPEECH
        }
        val audioFormat = AudioFormat.Builder().setSampleRate(audioDecoderInfo.sampleRate)
            .setEncoding(audioDecoderInfo.audioFormat)
            .setChannelMask(audioDecoderInfo.channelConfig)
            .build()
        // If buffer size is not insufficient, it will crash when you release it.
        // Please check [AudioReceiver#stopServer]
        audioTrack = AudioTrack(audioAttributesBuilder.build(), audioFormat, minBufferSize, AudioTrack.MODE_STREAM, sessionId)

        if (AudioTrack.STATE_INITIALIZED == audioTrack.state) {
            LogContext.log.i(TAG, "Start playing audio...")
            audioTrack.play()
        } else {
            LogContext.log.w(TAG, "AudioTrack state is not STATE_INITIALIZED")
        }

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
                decoderWrapper = AudioDecoderManager.getWrapper(
                    type,
                    audioDecoderInfo,
                    object : OutputCallback {
                        override fun output(out: ByteArray) {
                            val st = SystemClock.elapsedRealtime()
                            audioTrack.write(out.toShortArrayLE(), 0, out.size / 2)
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
        runCatching {
            if (AudioTrack.STATE_UNINITIALIZED == audioTrack.state) return
            if (AudioTrack.PLAYSTATE_PLAYING == audioTrack.playState) {
                // val st = SystemClock.elapsedRealtime()
                when (type) {
                    AudioType.PCM -> {
                        // Play decoded audio data in PCM
                        val playData = chunkAudioData.toShortArrayLE()
                        audioTrack.write(playData, 0, playData.size)
                        if (BuildConfig.DEBUG) LogContext.log.d(TAG, "Play PCM[${chunkAudioData.size}]")
                    }

                    AudioType.COMPRESSED_PCM -> decoderWrapper?.decode(chunkAudioData)
                    AudioType.AAC -> aacStreamPlayer?.startPlayingStream(chunkAudioData) {
                        LogContext.log.w(TAG, "AAC Drop audio frame")
                    }

                    AudioType.OPUS -> opusStreamPlayer?.startPlayingStream(chunkAudioData) {
                        LogContext.log.w(TAG, "Opus Drop audio frame")
                    }
                }
            }
        }.onFailure { it.printStackTrace() }
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
    @Suppress("WeakerAccess")
    fun resume() {
        LogContext.log.w(TAG, "resume()")
        runCatching { audioTrack.play() }.onFailure { it.printStackTrace() }
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
    @Suppress("WeakerAccess")
    fun pause() {
        LogContext.log.w(TAG, "pause()")
        runCatching {
            if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack.pause()
                audioTrack.flush()
            }
        }.onFailure { it.printStackTrace() }
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
        pause()
        runCatching { if (audioTrack.state == AudioTrack.STATE_INITIALIZED) audioTrack.stop() }.onFailure { it.printStackTrace() }
    }

    fun release() {
        LogContext.log.w(TAG, "release()")
        stop()
        runCatching { if (audioTrack.state == AudioTrack.STATE_INITIALIZED) audioTrack.release() }.onFailure { it.printStackTrace() }
        decoderWrapper?.release()
        aacStreamPlayer?.stopPlaying()
        opusStreamPlayer?.stopPlaying()
    }

    fun getPlayState() = audioTrack.playState

    fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / audioDecoderInfo.sampleRate

    fun getAudioTimeUs(): Long = runCatching {
        val numFramesPlayed: Int = audioTrack.playbackHeadPosition
        numFramesPlayed * 1_000_000L / audioDecoderInfo.sampleRate
    }.getOrDefault(0L)
}
