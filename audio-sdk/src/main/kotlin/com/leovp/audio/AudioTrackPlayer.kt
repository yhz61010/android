@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.bytes.toShortArrayLE
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2023/4/27 15:59
 */
class AudioTrackPlayer(ctx: Context, audioDecoderInfo: AudioDecoderInfo, minPlayBufferSizeRatio: Int = 1) {
    companion object {
        private const val TAG = "AudioTrackPlayer"
    }

    private var audioManager: AudioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack

    init {
        val minBufferSize = AudioTrack.getMinBufferSize(
            audioDecoderInfo.sampleRate,
            audioDecoderInfo.channelConfig,
            audioDecoderInfo.audioFormat
        ) * minPlayBufferSizeRatio
        LogContext.log.w(TAG, "$audioDecoderInfo minPlayBufferSizeRatio=$minPlayBufferSizeRatio minBufferSize=$minBufferSize")
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
            LogContext.log.i(TAG, "AudioTrack start playing...")
            audioTrack.play()
        } else {
            LogContext.log.w(TAG, "AudioTrack state is not STATE_INITIALIZED")
        }
    }

    fun getPlayState() = audioTrack.playState

    fun getPlaybackHeadPosition(): Int = audioTrack.playbackHeadPosition

    fun play(pcmBytes: ByteArray) {
        if (AudioTrack.STATE_UNINITIALIZED == audioTrack.state) {
            return
        }
        if (AudioTrack.PLAYSTATE_PLAYING == audioTrack.playState) {
            // val st = SystemClock.elapsedRealtime()
            // Play decoded audio data in PCM
            val playData = pcmBytes.toShortArrayLE()
            audioTrack.write(playData, 0, playData.size)
            if (BuildConfig.DEBUG) {
                LogContext.log.d(TAG, "Play PCM[${pcmBytes.size}]")
            }
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
    }
}
