@file:Suppress("unused")

package com.leovp.androidbase.utils.notch

import android.app.Activity
import android.os.Build
import com.leovp.android.exts.getScreenWidth
import com.leovp.android.exts.isHuaWei
import com.leovp.android.exts.isOppo
import com.leovp.android.exts.isVivo
import com.leovp.android.exts.isXiaoMi
import com.leovp.androidbase.utils.notch.impl.AndroidPDisplayCutout
import com.leovp.androidbase.utils.notch.impl.HuaweiDisplayCutout
import com.leovp.androidbase.utils.notch.impl.OppoDisplayCutout
import com.leovp.androidbase.utils.notch.impl.XiaoMiDisplayCutout
import com.leovp.kotlin.utils.SingletonHolder

/**
 * Example1: Allow to render on display cutout area.
 * ```kotlin
 * NotchScreenManager.getInstance(activity).fillDisplayCutout()
 * ```
 *
 * Example2: Get display cutout information.
 * ```kotlin
 * DisplayCutoutManager.getInstance(this).getDisplayCutoutInfo { info ->
 *     LogContext.log.i(TAG, "notchScreenInfo: ${info.toJsonString()}")
 *     binding.tv2.text = "notchScreenInfo: ${info.toJsonString()}"
 *
 *     LogContext.log.i(TAG, "Notch in ${info.pos}")
 *     binding.tv2.text = "Notch in ${info.pos}"
 * }
 * ```
 *
 * Author: Michael Leo
 * Date: 20-11-26 下午7:39
 */
class DisplayCutoutManager private constructor(private val activity: Activity) {
    companion object : SingletonHolder<DisplayCutoutManager, Activity>(::DisplayCutoutManager)

    private val displayCutout: DisplayCutout? by lazy { getConcreteDisplayCutout() }

    fun fillDisplayCutout() {
        displayCutout?.fillDisplayCutout(activity)
    }

    fun getDisplayCutoutInfo(infoCallback: DisplayCutout.DisplayCutoutInfoCallback) {
        val cutoutInfo = DisplayCutout.DisplayCutoutInfo()
        if (displayCutout?.supportDisplayCutout(activity) == true) {
            displayCutout?.cutoutAreaRect(activity) { rect ->
                if (!rect.isNullOrEmpty()) {
                    cutoutInfo.support = true
                    cutoutInfo.rect = rect

                    val halfScreenWidth = activity.getScreenWidth() / 2
                    if (rect[0].left < halfScreenWidth && halfScreenWidth < rect[0].right) {
                        cutoutInfo.pos = DisplayCutout.CutoutPosition.MIDDLE
                    } else if (halfScreenWidth < rect[0].left) {
                        cutoutInfo.pos = DisplayCutout.CutoutPosition.RIGHT
                    } else {
                        cutoutInfo.pos = DisplayCutout.CutoutPosition.LEFT
                    }
                }
                infoCallback.onResult(cutoutInfo)
            }
        } else {
            infoCallback.onResult(cutoutInfo)
        }
    }

    private fun getConcreteDisplayCutout(): DisplayCutout? {
        var displayCutout: DisplayCutout? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            displayCutout = AndroidPDisplayCutout()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when {
                activity.isHuaWei -> displayCutout = HuaweiDisplayCutout()
                activity.isOppo -> displayCutout = OppoDisplayCutout()
                activity.isVivo -> displayCutout = HuaweiDisplayCutout()
                activity.isXiaoMi -> displayCutout = XiaoMiDisplayCutout()
            }
        }
        return displayCutout
    }
}
