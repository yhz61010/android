package com.leovp.androidbase.utils.device

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Author: Michael Leo
 * Date: 20-5-28 下午5:27
 */
object VibrationUtil {
    /**
     * You must add `android.permission.VIBRATE` permission.
     */
    @SuppressLint("MissingPermission")
    fun vibrate(context: Context, milliseconds: Long = 500) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(
                VibrationEffect.createOneShot(
                    milliseconds,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            (context.getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(milliseconds)
        }
    }
}