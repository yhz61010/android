@file:Suppress("unused")

package com.leovp.androidbase.utils.notch.impl

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi
import com.leovp.android.exts.calculateNotchRect
import com.leovp.android.exts.densityDpi
import com.leovp.androidbase.utils.notch.DisplayCutout

/** vivo will not render on notch */
@RequiresApi(Build.VERSION_CODES.O)
internal class VivoDisplayCutout : DisplayCutout {

    @SuppressLint("PrivateApi")
    override fun supportDisplayCutout(activity: Activity): Boolean {
        val mask = 0x00000020
        return runCatching {
            val cls = Class.forName("android.util.FtFeature")
            val hideMethod = cls.getMethod("isFtFeatureSupport", Int::class.javaPrimitiveType)
            val obj = cls.newInstance()
            hideMethod.invoke(obj, mask) as Boolean
        }.getOrDefault(false)
    }

    /** vivo will not render on notch */
    override fun fillDisplayCutout(activity: Activity) = Unit

    override fun cutoutAreaRect(activity: Activity, callback: DisplayCutout.CutoutAreaRectCallback) {
        val rectList = ArrayList<Rect>()
        val rect = calculateNotchRect(activity, getNotchWidth(activity), getNotchHeight(activity))
        rectList.add(rect)
        callback.onResult(rectList)
    }

    /** According to the vivo official document, we use fixed value 27dp. */
    private fun getNotchHeight(ctx: Context) = 27 * ctx.densityDpi

    /** According to the vivo official document, we use fixed value 100dp. */
    private fun getNotchWidth(ctx: Context) = 100 * ctx.densityDpi
}
