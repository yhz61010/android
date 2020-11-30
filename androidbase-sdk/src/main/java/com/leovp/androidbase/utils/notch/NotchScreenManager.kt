package com.leovp.androidbase.utils.notch

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import com.leovp.androidbase.exts.android.isHuaWei
import com.leovp.androidbase.exts.android.isOppo
import com.leovp.androidbase.exts.android.isVivo
import com.leovp.androidbase.exts.android.isXiaoMi
import com.leovp.androidbase.utils.notch.INotchScreen.*
import com.leovp.androidbase.utils.notch.impl.AndroidPNotchScreen
import com.leovp.androidbase.utils.notch.impl.HuaweiNotchScreen
import com.leovp.androidbase.utils.notch.impl.MiNotchScreen
import com.leovp.androidbase.utils.notch.impl.OppoNotchScreen

/**
 * Usage:
 * ```kotlin
 * // Allow to render on notch area
 * NotchScreenManager.setDisplayInNotch(activity)
 *
 * // Get notch information
 * NotchScreenManager.getNotchInfo(activity, callback)
 * ```
 *
 * Author: Michael Leo
 * Date: 20-11-26 下午7:39
 */
@Suppress("unused")
object NotchScreenManager {

    private val notchScreen: INotchScreen? = getNotchScreen()

    fun setDisplayInNotch(activity: Activity) {
        notchScreen?.setDisplayInNotch(activity)
    }

    fun getNotchInfo(activity: Activity, notchScreenCallback: NotchScreenCallback) {
        val notchScreenInfo = NotchScreenInfo()
        if (notchScreen != null && notchScreen.hasNotch(activity)) {
            notchScreen.getNotchRect(activity, object : NotchSizeCallback {
                override fun onResult(notchRects: List<Rect>?) {
                    if (notchRects != null && notchRects.isNotEmpty()) {
                        notchScreenInfo.hasNotch = true
                        notchScreenInfo.notchRects = notchRects
                    }
                    notchScreenCallback.onResult(notchScreenInfo)
                }
            })
        } else {
            notchScreenCallback.onResult(notchScreenInfo)
        }
    }

    private fun getNotchScreen(): INotchScreen? {
        var notchScreen: INotchScreen? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            notchScreen = AndroidPNotchScreen()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when {
                isHuaWei -> notchScreen = HuaweiNotchScreen()
                isOppo -> notchScreen = OppoNotchScreen()
                isVivo -> notchScreen = HuaweiNotchScreen()
                isXiaoMi -> notchScreen = MiNotchScreen()
            }
        }
        return notchScreen
    }
}