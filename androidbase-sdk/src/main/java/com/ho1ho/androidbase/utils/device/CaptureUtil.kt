package com.ho1ho.androidbase.utils.device

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.Window
import java.lang.ref.WeakReference

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午5:12
 */
object CaptureUtil {

    /**
     * **Limitation**
     *
     * You can **ONLY** record the specified view or activity window .
     *
     * For activity window, the known components that can not be recorded are list here:(including but not limited to these components)
     *
     * - Toast
     * - Soft keyboard
     *
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

    /**
     * **Limitation**
     *
     * You can **ONLY** record the activity window.
     *
     * The known components that can not be recorded are list here:(including but not limited to these components)
     *
     * - Toast
     * - Soft keyboard
     */
    fun takeScreenshot(win: Window, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        return takeScreenshot(win.decorView.rootView, config)
    }

    fun takeScreenshot(act: WeakReference<Activity>, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        return Falcon.takeScreenshotBitmap(act, config)
    }
}