package com.ho1ho.androidbase.utils.device

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.Window

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午5:12
 */
object CaptureUtil {

    /**
     * Using `window.decorView.rootView` to capture the whole screen
     */
    @Suppress("WeakerAccess")
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

    fun takeScreenshot(act: Activity, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        return Falcon.takeScreenshotBitmap(act)
    }
}