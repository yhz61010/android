package com.leovp.androidbase.utils.notch.impl

import android.app.Activity
import android.content.Context
import android.view.Window
import com.leovp.android.exts.calculateNotchRect
import com.leovp.android.exts.getDimenInPixel
import com.leovp.android.utils.DeviceProp
import com.leovp.androidbase.utils.notch.DisplayCutout

internal class XiaoMiDisplayCutout : DisplayCutout {
    override fun supportDisplayCutout(activity: Activity): Boolean = runCatching {
        DeviceProp.getSystemProperty("ro.miui.notch").toInt() == 1
    }.getOrDefault(false)

    override fun fillDisplayCutout(activity: Activity) {
        val flag = 0x00000100 or 0x00000200 or 0x00000400
        runCatching {
            val method = Window::class.java.getMethod("addExtraFlags", Int::class.javaPrimitiveType)
            method.invoke(activity.window, flag)
        }.onFailure { it.printStackTrace() }
    }

    override fun cutoutAreaRect(activity: Activity, callback: DisplayCutout.CutoutAreaRectCallback) {
        val rect = calculateNotchRect(activity, getNotchWidth(activity), getNotchHeight(activity))
        callback.onResult(arrayListOf(rect))
    }

    private fun getNotchHeight(ctx: Context) = ctx.getDimenInPixel("notch_height")
    private fun getNotchWidth(ctx: Context) = ctx.getDimenInPixel("notch_width")
}
