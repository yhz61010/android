@file:Suppress("unused")

package com.leovp.androidbase.utils.device

import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission

/**
 * Author: Michael Leo
 * Date: 20-5-28 下午5:27
 */
object VibrationUtil {
    /**
     * You must add `android.permission.VIBRATE` permission.
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    fun vibrate(context: Context, milliseconds: Long = 500) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.vibrate(
                VibrationEffect.createOneShot(
                    milliseconds,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(milliseconds)
        }
    }
}