package com.leovp.androidbase.utils.notch

import android.app.Activity
import android.graphics.Rect

@Suppress("unused")
interface INotchScreen {
    fun hasNotch(activity: Activity): Boolean
    fun setDisplayInNotch(activity: Activity)
    fun getNotchRect(activity: Activity, callback: NotchSizeCallback)
    interface NotchSizeCallback {
        fun onResult(notchRects: List<Rect>?)
    }

    interface HasNotchCallback {
        fun onResult(hasNotch: Boolean)
    }

    interface NotchScreenCallback {
        fun onResult(notchScreenInfo: NotchScreenInfo)
    }

    data class NotchScreenInfo(var hasNotch: Boolean = false, var notchRects: List<Rect>? = null)
}