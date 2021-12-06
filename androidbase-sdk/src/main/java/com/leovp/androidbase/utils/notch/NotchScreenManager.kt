package com.leovp.androidbase.utils.notch

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import com.leovp.androidbase.SingletonHolder
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
 * Example1: Allow to render on notch area
 * ```kotlin
 * NotchScreenManager.setDisplayInNotch(activity)
 * ```
 *
 *
 * Example2:
 * Get notch information
 * ```kotlin
 * NotchScreenManager.getInstance(activity).getNotchInfo(object : INotchScreen.NotchScreenCallback {
 *     override fun onResult(notchScreenInfo: INotchScreen.NotchScreenInfo) {
 *         LogContext.log.i(TAG, "notchScreenInfo: ${notchScreenInfo.toJsonString()}")
 *         notchScreenInfo.notchRects?.let {
 *             val halfScreenWidth = getRealResolution().x / 2
 *             if (it[0].left < halfScreenWidth && halfScreenWidth < it[0].right) {
 *                 LogContext.log.i(TAG, "Notch in Middle")
 *             } else if (halfScreenWidth < it[0].left) {
 *                 LogContext.log.i(TAG, "Notch in Right")
 *             } else {
 *                 LogContext.log.i(TAG, "Notch in Left")
 *             }
 *         }
 *     }
 * })
 * ```
 *
 * Author: Michael Leo
 * Date: 20-11-26 下午7:39
 */
@Suppress("unused")
class NotchScreenManager private constructor(private val activity: Activity) {
    companion object : SingletonHolder<NotchScreenManager, Activity>(::NotchScreenManager)

    private val notchScreen: INotchScreen? = getNotchScreen()

    fun setDisplayInNotch() {
        notchScreen?.setDisplayInNotch(activity)
    }

    fun getNotchInfo(notchScreenCallback: NotchScreenCallback) {
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
                activity.isHuaWei -> notchScreen = HuaweiNotchScreen()
                activity.isOppo -> notchScreen = OppoNotchScreen()
                activity.isVivo -> notchScreen = HuaweiNotchScreen()
                activity.isXiaoMi -> notchScreen = MiNotchScreen()
            }
        }
        return notchScreen
    }
}