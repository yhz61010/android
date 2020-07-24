package com.ho1ho.androidbase.utils.device

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.Window

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午5:12
 */
object ScreenUtil {

    /**
     * Using [window.decorView.rootView] to capture the whole screen
     */
    fun takeScreenshot(view: View, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        return runCatching {
            Bitmap.createBitmap(view.width, view.height, config).also {
                view.draw(Canvas(it))
            }
        }.getOrNull()
    }

    fun takeScreenshot(win: Window, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        return takeScreenshot(win.decorView.rootView, config)
    }
}