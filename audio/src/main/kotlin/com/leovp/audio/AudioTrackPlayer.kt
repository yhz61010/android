@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio

import com.leovp.audio.base.runCatchingPreservingCancellation

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
import android.os.Build
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
    // AudioAttributes.USAGE_VOICE_COMMUNICATION  AudioAttributes.USAGE_MEDIA
    usage: Int = AudioAttributes.USAGE_MEDIA,
    // AudioAttributes.CONTENT_TYPE_SPEECH  AudioAttributes.CONTENT_TYPE_MUSIC
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
    minPlayBufferSizeRatio: Int = 1,
) {
    companion object {
        private const val TAG = "AudioTrackPlayer"
    }

    private val audioManager: AudioManager =
        ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioTrack: AudioTrack

    init {
        require(minPlayBufferSizeRatio > 0) { "minPlayBufferSizeRatio must be positive" }
        val platformMinBufferSize = getMinBufferSize(
            audioDecoderInfo.sampleRate,
            audioDecoderInfo.channelConfig,
            audioDecoderInfo.audioFormat
        )
        require(platformMinBufferSize > 0) {
            "Invalid AudioTrack parameters: $audioDecoderInfo (code=$platformMinBufferSize)"
        }
        val computedBufferSize = platformMinBufferSize.toLong() * minPlayBufferSizeRatio
        require(computedBufferSize <= Int.MAX_VALUE) { "AudioTrack buffer size overflow" }
        val minBufferSize = computedBufferSize.toInt()
        LogContext.log.w(
            TAG,
            "$audioDecoderInfo minPlayBufferSizeRatio=$minPlayBufferSizeRatio " +
                "minBufferSize=$minBufferSize"
        )
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
        // audioTrack = AudioTrack(audioAttributesBuilder.build(), audioFormat, minBufferSize, mode,
        // sessionId)
        audioTrack = AudioTrack(
            audioAttributesBuilder.build(),
            audioFormat,
            minBufferSize,
            mode,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        // ctx.useBuildInSpeaker(true)
    }

    /** Guards the post-write route snapshot so it is taken once per player instance. */
    private var routeLogged = false

    /**
     * Debug-only snapshot of what the platform actually did with this track: the output device
     * it chose, the AudioManager mode and the media volume step.
     *
     * Routing is decided by the audio policy, not by us, so none of it can be inferred from the
     * app side. Without this, a device playing through the earpiece, a device left in
     * MODE_IN_COMMUNICATION by another app, a media volume sitting at 20%, and a genuine routing
     * bug all look identical from here.
     *
     * `AudioTrack.getRoutedDevice()` returns null until data actually flows, so this is called
     * both after play() and after the first successful write.
     *
     * ### Reading the output
     *
     * ```
     * ROUTE[first-write] device=2 name=Mi 10 mode=0 musicVol=150/150 outputs=[1,2,18]
     * ```
     *
     * `device` is the `AudioDeviceInfo` type the policy actually chose; `outputs` lists every
     * output the platform currently knows about, in the same encoding. A null `device` means
     * routing is not established yet. Types seen on phones:
     *
     * | Value | AudioDeviceInfo | Meaning |
     * |---|---|---|
     * | `1` | `TYPE_BUILTIN_EARPIECE` | Earpiece. For media playback this is a real bug. |
     * | `2` | `TYPE_BUILTIN_SPEAKER` | Loudspeaker. Covers both transducers on stereo phones. |
     * | `3` / `4` | `TYPE_WIRED_HEADSET` / `_HEADPHONES` | With and without a microphone. |
     * | `7` | `TYPE_BLUETOOTH_SCO` | Bluetooth call path: mono, narrow band. |
     * | `8` | `TYPE_BLUETOOTH_A2DP` | Bluetooth media path. |
     * | `9` | `TYPE_HDMI` | External display or AV receiver. |
     * | `11` / `22` | `TYPE_USB_DEVICE` / `TYPE_USB_HEADSET` | USB audio. |
     * | `18` | `TYPE_TELEPHONY` | Modem voice path. Normal to see listed, never as `device`. |
     * | `24` | `TYPE_BUILTIN_SPEAKER_SAFE` | Level-limited speaker (API 30+). |
     *
     * `mode` is [AudioManager.getMode]. Anything other than `0` means some app has put the
     * device into a call state, which changes routing for everyone:
     *
     * | Value | AudioManager | Meaning |
     * |---|---|---|
     * | `0` | `MODE_NORMAL` | Normal. The only expected value for media playback. |
     * | `1` | `MODE_RINGTONE` | Incoming call ringing. |
     * | `2` | `MODE_IN_CALL` | Telephony call in progress. |
     * | `3` | `MODE_IN_COMMUNICATION` | VoIP call. A leaked one sends media to the earpiece. |
     *
     * `musicVol` is `current/max` for `STREAM_MUSIC`. **The scale is device specific**: AOSP
     * usually reports 15 steps, MIUI 150. Compare the two numbers, never the left one alone -
     * `30/150` is 20% and quiet, not a healthy level.
     */
    private fun logRouting(where: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val routed = audioTrack.routedDevice
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .joinToString(",") { it.type.toString() }
        LogContext.log.w(
            TAG,
            "ROUTE[$where] device=${routed?.type} name=${routed?.productName} " +
                "mode=${audioManager.mode} " +
                "musicVol=${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}/" +
                "${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)} " +
                "outputs=[$outputs]"
        )
    }

    val playState: Int get() = audioTrack.playState

    val state: Int get() = audioTrack.state

    val playbackHeadPosition: Int get() = audioTrack.playbackHeadPosition

    fun play() {
        if (STATE_INITIALIZED == audioTrack.state) {
            LogContext.log.i(TAG, "AudioTrack start playing...")
            audioTrack.play()
            if (BuildConfig.DEBUG) {
                logRouting("play")
            }
        } else {
            LogContext.log.w(TAG, "AudioTrack state is not STATE_INITIALIZED")
        }
    }

    /**
     * @return zero or the positive number of bytes that were written, or error codes will be
     * returned.
     * See [AudioTrack.write] for more details.
     */
    fun write(pcmBytes: ByteArray): Int {
        if (pcmBytes.isEmpty()) {
            return 0
        }
        require(pcmBytes.size % 2 == 0) { "PCM16 byte count must be even" }
        return runCatchingPreservingCancellation {
            if (STATE_UNINITIALIZED == audioTrack.state) return@runCatchingPreservingCancellation 0
            var wroteSize = 0
            if (PLAYSTATE_PLAYING == audioTrack.playState) {
                // val st = SystemClock.elapsedRealtime()
                // Play decoded audio data in PCM
                val playData = pcmBytes.toShortArrayLE()
                wroteSize = audioTrack.write(playData, 0, playData.size)
                if (wroteSize < 0) {
                    LogContext.log.e(TAG, "AudioTrack.write error=$wroteSize")
                    return@runCatchingPreservingCancellation wroteSize
                }
                if (BuildConfig.DEBUG && !routeLogged) {
                    routeLogged = true
                    logRouting("first-write")
                }
                if (BuildConfig.DEBUG) {
                    LogContext.log.d(TAG, "PCM[${pcmBytes.size}] Play[${wroteSize * 2}]")
                }
            }
            // wroteSize is the length of short array. So we need to convert it to byte length.
            wroteSize * 2
        }.getOrElse {
            LogContext.log.e(TAG, "AudioTrack.write failed", it)
            0
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
        if (audioTrack.playState == PLAYSTATE_PLAYING) {
            return
        }
        LogContext.log.w(TAG, "resume()")
        runCatchingPreservingCancellation { audioTrack.play() }
            .onFailure { LogContext.log.e(TAG, "AudioTrack resume failed", it) }
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
        runCatchingPreservingCancellation {
            if (audioTrack.state == STATE_INITIALIZED) {
                audioTrack.pause()
                audioTrack.flush()
            }
        }.onFailure { LogContext.log.e(TAG, "AudioTrack pause failed", it) }
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
        runCatchingPreservingCancellation {
            if (
                audioTrack.state == STATE_INITIALIZED
            ) {
                audioTrack.stop()
            }
        }.onFailure { LogContext.log.e(TAG, "AudioTrack stop failed", it) }
    }

    fun release() {
        if (audioTrack.state == STATE_UNINITIALIZED) {
            return
        }
        stop()
        LogContext.log.w(TAG, "release()")
        runCatchingPreservingCancellation {
            if (audioTrack.state == STATE_INITIALIZED) {
                audioTrack.release()
            }
        }.onFailure { LogContext.log.e(TAG, "AudioTrack release failed", it) }
    }
}
