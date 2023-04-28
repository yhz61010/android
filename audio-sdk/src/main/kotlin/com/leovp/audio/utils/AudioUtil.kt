@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio.utils

import android.content.Context
import android.media.AudioManager
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2023/4/28 11:13
 */
object AudioUtil {
    private const val TAG = "AudioUtil"

    /**
     * @param mode support list:
     * - play by built-in speaker:
     *      - AudioManager.MODE_NORMAL
     *      - AudioManager.MODE_RINGTONE
     * - play by earpiece speaker
     *      - AudioManager.MODE_IN_CALL
     *      - AudioManager.MODE_IN_COMMUNICATION
     */
    fun useBuildInSpeaker(ctx: Context, on: Boolean, mode: Int? = null) {
        LogContext.log.w(TAG, "useSpeaker=$on")
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // if (on) {
        //     audioManager.mode = AudioManager.MODE_NORMAL
        //     audioManager.isSpeakerphoneOn = true
        // } else {
        //     audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        //     audioManager.isSpeakerphoneOn = false
        // }
        mode?.let { setAudioManagerMode(ctx, it) }
        audioManager.isSpeakerphoneOn = on
    }

    fun resetPlaybackOutputSource(ctx: Context) {
        LogContext.log.w(TAG, "resetPlayOutputSource()")
        setAudioManagerMode(ctx, AudioManager.MODE_NORMAL)
    }

    /**
     * @param mode support list:
     * - play by built-in speaker:
     *      - AudioManager.MODE_NORMAL
     *      - AudioManager.MODE_RINGTONE
     * - play by earpiece speaker
     *      - AudioManager.MODE_IN_CALL
     *      - AudioManager.MODE_IN_COMMUNICATION
     */
    fun setAudioManagerMode(ctx: Context, mode: Int) {
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = mode
    }

    fun getAudioManagerMode(ctx: Context): Int {
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.mode
    }
}
