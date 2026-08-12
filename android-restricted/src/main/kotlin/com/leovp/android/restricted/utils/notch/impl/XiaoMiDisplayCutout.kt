package com.leovp.android.restricted.utils.notch.impl

import android.app.Activity
import android.content.Context
import android.view.Window
import com.leovp.android.exts.calculateNotchRect
import com.leovp.android.exts.getDimenInPixel
import com.leovp.android.restricted.utils.DeviceProp
import com.leovp.android.restricted.utils.notch.DisplayCutout
import com.leovp.log.LogContext

internal class XiaoMiDisplayCutout : DisplayCutout {
    companion object {
        private const val TAG = "XiaoMiDisplayCutout"
    }

    override fun supportDisplayCutout(activity: Activity): Boolean = runCatching {
        DeviceProp.getSystemProperty("ro.miui.notch").toInt() == 1
    }.getOrDefault(false)

    override fun fillDisplayCutout(activity: Activity) {
        val flag = 0x00000100 or 0x00000200 or 0x00000400
        runCatching {
            val method = Window::class.java.getMethod("addExtraFlags", Int::class.javaPrimitiveType)
            method.invoke(activity.window, flag)
        }.onFailure { LogContext.log.e(TAG, "Enable Xiaomi display cutout failed", it) }
    }

    override fun cutoutAreaRect(
        activity: Activity,
        callback: DisplayCutout.CutoutAreaRectCallback,
    ) {
        val rect = calculateNotchRect(activity, getNotchWidth(activity), getNotchHeight(activity))
        callback.onResult(arrayListOf(rect))
    }

    private fun getNotchHeight(ctx: Context) = ctx.getDimenInPixel("notch_height")
    private fun getNotchWidth(ctx: Context) = ctx.getDimenInPixel("notch_width")
}
