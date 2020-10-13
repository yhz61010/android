package com.leovp.audio.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.utils.LLog
import com.leovp.audio.base.AudioCodecInfo

/**
 * Author: Michael Leo
 * Date: 2020/9/16 下午5:03
 */
class PcmPlayer(ctx: Context, audioData: AudioCodecInfo) {
    private var audioManager: AudioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack

    init {
        val minBufferSize = AudioTrack.getMinBufferSize(audioData.sampleRate, audioData.channelConfig, audioData.audioFormat)
        val sessionId = audioManager.generateAudioSessionId()
        val audioAttributesBuilder = AudioAttributes.Builder().apply {
            setUsage(AudioAttributes.USAGE_MEDIA) // AudioAttributes.USAGE_MEDIA          AudioAttributes.USAGE_VOICE_COMMUNICATION
            setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // AudioAttributes.CONTENT_TYPE_MUSIC   AudioAttributes.CONTENT_TYPE_SPEECH
        }
        val audioFormat = AudioFormat.Builder().setSampleRate(audioData.sampleRate)
            .setEncoding(audioData.audioFormat)
            .setChannelMask(audioData.channelConfig)
            .build()
        // If buffer size is not insufficient, it will crash when you release it.
        // Please check [AudioReceiver#stopServer]
        audioTrack = AudioTrack(audioAttributesBuilder.build(), audioFormat, minBufferSize * 2, AudioTrack.MODE_STREAM, sessionId)

        if (AudioTrack.STATE_INITIALIZED == audioTrack.state) {
            LLog.w(ITAG, "Start playing audio...")
            audioTrack.play()
        } else {
            LLog.w(ITAG, "AudioTrack state is not STATE_INITIALIZED")
        }
    }

    fun play(chunkPcm: ByteArray) {
        runCatching {
            if (AudioTrack.STATE_UNINITIALIZED == audioTrack.state) return
            if (AudioTrack.PLAYSTATE_PLAYING == audioTrack.playState) {
//                val st = SystemClock.elapsedRealtime()
                // Play decoded audio data in PCM
                audioTrack.write(chunkPcm, 0, chunkPcm.size)
//                LLog.i(ITAG, "Play pcm cost=${SystemClock.elapsedRealtime() - st} ms.")
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
    fun resume() {
        LLog.w(ITAG, "resume()")
        runCatching {
            audioTrack.play()
        }.onFailure { it.printStackTrace() }
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
        LLog.w(ITAG, "pause()")
        runCatching {
            audioTrack.pause()
            audioTrack.flush()
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
        LLog.w(ITAG, "stop()")
        pause()
        runCatching { audioTrack.stop() }.onFailure { it.printStackTrace() }
    }

    fun release() {
        LLog.w(ITAG, "release()")
        stop()
        runCatching { audioTrack.release() }.onFailure { it.printStackTrace() }
    }
}