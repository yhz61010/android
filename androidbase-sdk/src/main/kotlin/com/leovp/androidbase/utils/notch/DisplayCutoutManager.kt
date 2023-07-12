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
 *     LogContext.log.i(TAG, "Display cutout information: ${info.toJsonString()}")
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
            displayCutout?.cutoutAreaRect(activity) { rects ->
                if (!rects.isNullOrEmpty()) {
                    cutoutInfo.support = true
                    cutoutInfo.rects = rects
                    val positions: MutableList<DisplayCutout.CutoutPosition> = ArrayList()
                    cutoutInfo.positions = positions
                    val halfScreenWidth = activity.getScreenWidth() / 2
                    for( rect in rects) {
                        if (rect.left < halfScreenWidth && halfScreenWidth < rect.right) {
                            positions.add(DisplayCutout.CutoutPosition.MIDDLE)
                        } else if (halfScreenWidth < rect.left) {
                            positions.add(DisplayCutout.CutoutPosition.RIGHT)
                        } else {
                            positions.add(DisplayCutout.CutoutPosition.LEFT)
                        }
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
