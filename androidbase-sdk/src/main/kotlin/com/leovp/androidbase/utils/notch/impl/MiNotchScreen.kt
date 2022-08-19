package com.leovp.androidbase.utils.notch.impl

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.Window
import androidx.annotation.RequiresApi
import com.leovp.androidbase.utils.notch.INotchScreen
import com.leovp.androidbase.utils.notch.INotchScreen.NotchSizeCallback
import com.leovp.lib_common_android.exts.calculateNotchRect
import com.leovp.lib_common_android.exts.getDimenInPixel
import com.leovp.lib_common_android.utils.DeviceProp

@RequiresApi(Build.VERSION_CODES.O)
class MiNotchScreen : INotchScreen {
    override fun hasNotch(activity: Activity) = isNotch

    override fun setDisplayInNotch(activity: Activity) {
        val flag = 0x00000100 or 0x00000200 or 0x00000400
        runCatching {
            val method = Window::class.java.getMethod("addExtraFlags", Int::class.javaPrimitiveType)
            method.invoke(activity.window, flag)
        }.onFailure { it.printStackTrace() }
    }

    override fun getNotchRect(activity: Activity, callback: NotchSizeCallback) {
        val rect = calculateNotchRect(activity, getNotchWidth(activity), getNotchHeight(activity))
        val rectList = ArrayList<Rect>()
        rectList.add(rect)
        callback.onResult(rectList)
    }

    companion object {
        private val isNotch: Boolean = runCatching { DeviceProp.getSystemProperty("ro.miui.notch").toInt() == 1 }.getOrDefault(false)
        fun getNotchHeight(ctx: Context) = ctx.getDimenInPixel("notch_height")
        fun getNotchWidth(ctx: Context) = ctx.getDimenInPixel("notch_width")
    }
}
