@file:Suppress("unused")

package com.leovp.androidbase.utils.notch

import android.app.Activity
import android.graphics.Rect
import androidx.annotation.Keep

interface DisplayCutout {
    fun supportDisplayCutout(activity: Activity): Boolean
    fun fillDisplayCutout(activity: Activity)
    fun cutoutAreaRect(activity: Activity, callback: CutoutAreaRectCallback)

    fun interface CutoutAreaRectCallback {
        fun onResult(rect: List<Rect>?)
    }

    fun interface DisplayCutoutInfoCallback {
        fun onResult(info: DisplayCutoutInfo)
    }

    enum class CutoutPosition {
        LEFT, MIDDLE, RIGHT
    }

    @Keep
    data class DisplayCutoutInfo(var support: Boolean = false, var pos: CutoutPosition? = null, var rect: List<Rect>? = null)
}
