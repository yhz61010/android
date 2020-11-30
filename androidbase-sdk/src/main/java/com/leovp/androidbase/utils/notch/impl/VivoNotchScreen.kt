package com.leovp.androidbase.utils.notch.impl

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import com.leovp.androidbase.exts.android.calculateNotchRect
import com.leovp.androidbase.exts.android.densityDpi
import com.leovp.androidbase.utils.notch.INotchScreen
import com.leovp.androidbase.utils.notch.INotchScreen.NotchSizeCallback
import java.util.*

/** vivo will not render on notch */
@TargetApi(Build.VERSION_CODES.O)
class VivoNotchScreen : INotchScreen {

    override fun hasNotch(activity: Activity) = isNotch

    /** vivo will not render on notch */
    override fun setDisplayInNotch(activity: Activity) = Unit

    override fun getNotchRect(activity: Activity, callback: NotchSizeCallback) {
        val rects = ArrayList<Rect>()
        val rect = calculateNotchRect(activity, getNotchWidth(activity), getNotchHeight(activity))
        rects.add(rect)
        callback.onResult(rects)
    }

    companion object {
        val isNotch: Boolean
            @SuppressLint("PrivateApi")
            get() {
                val mask = 0x00000020
                return runCatching {
                    val cls = Class.forName("android.util.FtFeature")
                    val hideMethod = cls.getMethod("isFtFeatureSupport", Int::class.javaPrimitiveType)
                    val `object` = cls.newInstance()
                    hideMethod.invoke(`object`, mask) as Boolean
                }.getOrDefault(false)
            }

        /** According to the vivo official document, we use fixed value 27dp. */
        fun getNotchHeight(ctx: Context) = 27 * ctx.densityDpi

        /** According to the vivo official document, we use fixed value 100dp. */
        fun getNotchWidth(ctx: Context) = 100 * ctx.densityDpi
    }
}