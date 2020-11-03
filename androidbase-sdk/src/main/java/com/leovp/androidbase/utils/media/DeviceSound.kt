package com.leovp.androidbase.utils.media

import android.content.Context
import android.media.MediaActionSound
import android.media.RingtoneManager
import android.net.Uri
import com.leovp.androidbase.utils.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2020/9/4 下午7:48
 */
object DeviceSound {
    private const val TAG = "DeviceSound"

    fun playShutterClick() {
        MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
    }

    fun playFocusComplete() {
        MediaActionSound().play(MediaActionSound.FOCUS_COMPLETE)
    }

    fun playStartVideoRecording() {
        MediaActionSound().play(MediaActionSound.START_VIDEO_RECORDING)
    }

    fun playStopVideoRecording() {
        MediaActionSound().play(MediaActionSound.STOP_VIDEO_RECORDING)
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