@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioTrack.MODE_STREAM
import android.media.AudioTrack.PLAYSTATE_PAUSED
import android.media.AudioTrack.PLAYSTATE_PLAYING
import android.media.AudioTrack.PLAYSTATE_STOPPED
import android.media.AudioTrack.STATE_INITIALIZED
import android.media.AudioTrack.STATE_UNINITIALIZED
import android.media.AudioTrack.getMinBufferSize
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.bytes.toShortArrayLE
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2023/4/27 15:59
 */
class AudioTrackPlayer(
    ctx: Context,
    audioDecoderInfo: AudioDecoderInfo,
    mode: Int = MODE_STREAM,
    usage: Int = AudioAttributes.USAGE_MEDIA, // AudioAttributes.USAGE_VOICE_COMMUNICATION  AudioAttributes.USAGE_MEDIA
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC, // AudioAttributes.CONTENT_TYPE_SPEECH  AudioAttributes.CONTENT_TYPE_MUSIC
    minPlayBufferSizeRatio: Int = 1
) {
    companion object {
        private const val TAG = "AudioTrackPlayer"
    }

    private val audioManager: AudioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioTrack: AudioTrack

    init {
        val minBufferSize = getMinBufferSize(
            audioDecoderInfo.sampleRate,
            audioDecoderInfo.channelConfig,
            audioDecoderInfo.audioFormat
        ) * minPlayBufferSizeRatio
        LogContext.log.w(TAG, "$audioDecoderInfo minPlayBufferSizeRatio=$minPlayBufferSizeRatio minBufferSize=$minBufferSize")
        // val sessionId = audioManager.generateAudioSessionId()
        val audioAttributesBuilder = AudioAttributes.Builder()
            // AudioAttributes.USAGE_MEDIA
            // AudioAttributes.USAGE_VOICE_COMMUNICATION
            .setUsage(usage)
            // AudioAttributes.CONTENT_TYPE_MUSIC
            // AudioAttributes.CONTENT_TYPE_SPEECH
            .setContentType(contentType)
        // .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(audioDecoderInfo.sampleRate)
            .setEncoding(audioDecoderInfo.audioFormat)
            .setChannelMask(audioDecoderInfo.channelConfig)
            .build()
        // If buffer size is not insufficient, it will crash when you release it.
        // Please check [AudioReceiver#stopServer]
        // audioTrack = AudioTrack(audioAttributesBuilder.build(), audioFormat, minBufferSize, mode, sessionId)
        audioTrack = AudioTrack(audioAttributesBuilder.build(), audioFormat, minBufferSize, mode, AudioManager.AUDIO_SESSION_ID_GENERATE)
        // ctx.useBuildInSpeaker(true)
    }

    val playState: Int get() = audioTrack.playState

    val state: Int get() = audioTrack.state

    val playbackHeadPosition: Int get() = audioTrack.playbackHeadPosition

    fun play() {
        if (STATE_INITIALIZED == audioTrack.state) {
            LogContext.log.i(TAG, "AudioTrack start playing...")
            audioTrack.play()
        } else {
            LogContext.log.w(TAG, "AudioTrack state is not STATE_INITIALIZED")
        }
    }

    /**
     * @return zero or the positive number of bytes that were written, or error codes will be returned.
     * See [AudioTrack.write] for more details.
     */
    fun write(pcmBytes: ByteArray): Int {
        if (pcmBytes.isEmpty()) {
            return 0
        }
        if (STATE_UNINITIALIZED == audioTrack.state) {
            return 0
        }
        var wroteSize = 0
        if (PLAYSTATE_PLAYING == audioTrack.playState) {
            // val st = SystemClock.elapsedRealtime()
            // Play decoded audio data in PCM
            val playData = pcmBytes.toShortArrayLE()
            wroteSize = audioTrack.write(playData, 0, playData.size)
            if (BuildConfig.DEBUG) {
                LogContext.log.d(TAG, "PCM[${pcmBytes.size}] Play[${wroteSize * 2}]")
            }
        }
        // wroteSize is the length of short array. So we need to convert it to byte length.
        return wroteSize * 2
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
        if (audioTrack.playState == PLAYSTATE_PLAYING) {
            return
        }
        LogContext.log.w(TAG, "resume()")
        runCatching { audioTrack.play() }.onFailure { it.printStackTrace() }
    }

    /**
     * After [pause] audio, if you want to play audio again, [resume] method **MUST** be called.
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
        if (audioTrack.playState == PLAYSTATE_PAUSED) {
            return
        }
        LogContext.log.w(TAG, "pause()")
        runCatching {
            if (audioTrack.state == STATE_INITIALIZED) {
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
        if (audioTrack.playState == PLAYSTATE_STOPPED) {
            return
        }
        pause()
        LogContext.log.w(TAG, "stop()")
        runCatching { if (audioTrack.state == STATE_INITIALIZED) audioTrack.stop() }.onFailure { it.printStackTrace() }
    }

    fun release() {
        if (audioTrack.state == STATE_UNINITIALIZED) {
            return
        }
        stop()
        LogContext.log.w(TAG, "release()")
        runCatching { if (audioTrack.state == STATE_INITIALIZED) audioTrack.release() }.onFailure { it.printStackTrace() }
    }
}
