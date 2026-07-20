@file:Suppress("unused")

package com.leovp.androidbase.utils.media

import android.content.Context
import android.media.MediaActionSound
import android.media.RingtoneManager
import android.net.Uri
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2020/9/4 下午7:48
 */
object DeviceSound {
    private const val TAG = "DeviceSound"

    // Reuse a single MediaActionSound instance (load once, play many) instead of allocating a
    // new native object per call and leaking it. Call [release] when the sounds are no longer
    // needed (e.g. when the camera screen is torn down).
    private var actionSound: MediaActionSound? = null

    private fun actionSound(): MediaActionSound =
        actionSound ?: MediaActionSound().also { actionSound = it }

    fun playShutterClick() {
        actionSound().play(MediaActionSound.SHUTTER_CLICK)
    }

    fun playFocusComplete() {
        actionSound().play(MediaActionSound.FOCUS_COMPLETE)
    }

    fun playStartVideoRecording() {
        actionSound().play(MediaActionSound.START_VIDEO_RECORDING)
    }

    fun playStopVideoRecording() {
        actionSound().play(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    /** Release the shared [MediaActionSound] and its native resources. */
    fun release() {
        actionSound?.release()
        actionSound = null
    }

    fun playDefaultNotification(ctx: Context) {
        LogContext.log.w(TAG, "playDefaultNotification")
        runCatching {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(ctx, notification).play()
        }.getOrNull()
    }

    fun playDefaultPhoneCall(ctx: Context) {
        runCatching {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            RingtoneManager.getRingtone(ctx, notification).play()
        }.getOrNull()
    }

    fun playDefaultAlarm(ctx: Context) {
        runCatching {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            RingtoneManager.getRingtone(ctx, notification).play()
        }.getOrNull()
    }
}
