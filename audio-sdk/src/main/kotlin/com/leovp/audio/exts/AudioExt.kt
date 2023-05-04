@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio.exts

import android.content.Context
import android.media.AudioManager
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2023/4/28 11:13
 */

private const val TAG = "AudioExt"

/**
 * @param mode support list:
 * - play by built-in speaker:
 *      - AudioManager.MODE_NORMAL
 *      - AudioManager.MODE_RINGTONE
 * - play by earpiece speaker
 *      - AudioManager.MODE_IN_CALL
 *      - AudioManager.MODE_IN_COMMUNICATION
 */
fun Context.useBuildInSpeaker(on: Boolean, mode: Int? = null) {
    LogContext.log.w(TAG, "useSpeaker=$on")
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    // if (on) {
    //     audioManager.mode = AudioManager.MODE_NORMAL
    //     audioManager.isSpeakerphoneOn = true
    // } else {
    //     audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    //     audioManager.isSpeakerphoneOn = false
    // }
    mode?.let { setAudioManagerMode(it) }
    audioManager.isSpeakerphoneOn = on
}

fun Context.resetPlaybackOutputSource() {
    LogContext.log.w(TAG, "resetPlayOutputSource()")
    setAudioManagerMode(AudioManager.MODE_NORMAL)
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
fun Context.setAudioManagerMode(mode: Int) {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.mode = mode
}

fun Context.getAudioManagerMode(): Int {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audioManager.mode
}
