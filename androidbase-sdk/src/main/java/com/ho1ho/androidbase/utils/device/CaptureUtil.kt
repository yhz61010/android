package com.ho1ho.androidbase.utils.device

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午5:12
 */
object ScreenUtil {

    // window.decorView.rootView for capture the whole screen
    fun takeScreenshot(view: View): Bitmap? {
        val bitmap: Bitmap?
        try {
            bitmap = Bitmap.createBitmap(
                view.width,
                view.height, Bitmap.Config.ARGB_8888
            )
        } catch (e: Exception) {
            return null
        }
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}