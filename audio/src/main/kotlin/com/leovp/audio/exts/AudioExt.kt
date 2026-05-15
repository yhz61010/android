@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio.exts

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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
    mode?.let { setAudioManagerMode(it) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetType = if (on) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        val targetDevice = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
        if (targetDevice != null) {
            audioManager.setCommunicationDevice(targetDevice)
        } else if (!on) {
            audioManager.clearCommunicationDevice()
        }
    } else {
        @Suppress("DEPRECATION")
        run {
            audioManager.isSpeakerphoneOn = on
        }
    }
}

fun Context.resetPlaybackOutputSource() {
    LogContext.log.w(TAG, "resetPlayOutputSource()")
    setAudioManagerMode(AudioManager.MODE_NORMAL)
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        audioManager.clearCommunicationDevice()
    } else {
        @Suppress("DEPRECATION")
        run {
            audioManager.isSpeakerphoneOn = false
        }
    }
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
