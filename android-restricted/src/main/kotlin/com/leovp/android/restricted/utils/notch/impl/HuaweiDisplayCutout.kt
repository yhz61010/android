package com.leovp.android.restricted.utils.notch.impl

import android.app.Activity
import android.graphics.Rect
import android.view.WindowManager
import com.leovp.android.exts.calculateNotchRect
import com.leovp.android.restricted.utils.notch.DisplayCutout
import com.leovp.log.LogContext

@Suppress("unused")
internal class HuaweiDisplayCutout : DisplayCutout {
    override fun supportDisplayCutout(activity: Activity): Boolean = runCatching {
        val cl = activity.classLoader
        val hwNotchSizeUtil = cl.loadClass("com.huawei.android.util.HwNotchSizeUtil")
        val get = hwNotchSizeUtil.getMethod("hasNotchInScreen")
        get.invoke(hwNotchSizeUtil) as Boolean
    }.getOrDefault(false)

    override fun fillDisplayCutout(activity: Activity) {
        runCatching {
            val window = activity.window
            val layoutParams = window.attributes
            val layoutParamsExCls = Class.forName("com.huawei.android.view.LayoutParamsEx")
            val con = layoutParamsExCls.getConstructor(WindowManager.LayoutParams::class.java)
            val layoutParamsExObj = con.newInstance(layoutParams)
            val method = layoutParamsExCls.getMethod("addHwFlags", Int::class.javaPrimitiveType)
            method.invoke(layoutParamsExObj, FLAG_NOTCH_SUPPORT)
            window.windowManager.updateViewLayout(window.decorView, window.decorView.layoutParams)
        }.onFailure { LogContext.log.e(TAG, "Enable Huawei display cutout failed", it) }
    }

    override fun cutoutAreaRect(
        activity: Activity,
        callback: DisplayCutout.CutoutAreaRectCallback
    ) {
        runCatching {
            val cl = activity.classLoader
            val hwNotchSizeUtil = cl.loadClass("com.huawei.android.util.HwNotchSizeUtil")
            val getNotchSize = hwNotchSizeUtil.getMethod("getNotchSize")
            val ret = getNotchSize.invoke(hwNotchSizeUtil) as IntArray
            val rectList = ArrayList<Rect>()
            rectList.add(calculateNotchRect(activity, ret[0], ret[1]))
            callback.onResult(rectList)
        }.onFailure {
            callback.onResult(null)
        }
    }

    companion object {
        private const val TAG = "HuaweiDisplayCutout"

        /** Full screen flag */
        const val FLAG_NOTCH_SUPPORT = 0x00010000

        /** Do not display in notch area */
        fun setNotDisplayInNotch(activity: Activity) {
            runCatching {
                val window = activity.window
                val layoutParams = window.attributes
                val layoutParamsExCls = Class.forName("com.huawei.android.view.LayoutParamsEx")
                val con = layoutParamsExCls.getConstructor(WindowManager.LayoutParams::class.java)
                val layoutParamsExObj = con.newInstance(layoutParams)
                val method = layoutParamsExCls.getMethod(
                    "clearHwFlags",
                    Int::class.javaPrimitiveType
                )
                method.invoke(layoutParamsExObj, FLAG_NOTCH_SUPPORT)
                window.windowManager.updateViewLayout(
                    window.decorView,
                    window.decorView.layoutParams
                )
            }.onFailure { LogContext.log.e(TAG, "Disable Huawei display cutout failed", it) }
        }
    }
}
