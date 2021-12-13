package com.leovp.floatview_sdk.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Author: Michael Leo
 * Date: 2021/8/31 15:15
 */

val Context.canDrawOverlays: Boolean
    get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

fun Context.getRealResolution(): Point {
    val size = Point()
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//        this.display?.getRealSize(size)
        val bounds = windowManager.currentWindowMetrics.bounds
        size.x = bounds.width()
        size.y = bounds.height()
    } else {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val displayMetrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        display.getRealMetrics(displayMetrics)
        size.x = displayMetrics.widthPixels
        size.y = displayMetrics.heightPixels
    }
    return size
}

fun Context.getAvailableResolution(): Point {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val width = wm.currentWindowMetrics.bounds.width()
        val height = wm.currentWindowMetrics.bounds.height()
        Point(width, height)
    } else {
//            val display = wm.defaultDisplay
//            val size = Point()
//            display.getSize(size)
//            size

//            val display = wm.defaultDisplay
//            val displayMetrics = DisplayMetrics()
//            display.getMetrics(displayMetrics)
//            return Point(displayMetrics.widthPixels, displayMetrics.heightPixels)

        val displayMetrics = resources.displayMetrics
        return runCatching { Point(displayMetrics.widthPixels, displayMetrics.heightPixels) }.getOrDefault(Point())
    }
}

val Context.screenRealWidth get() = getRealResolution().x

val Context.screenAvailableHeight get() = getAvailableResolution().y

val Context.statusBarHeight
    get() : Int {
        var result = 0
        val resourceId = this.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }