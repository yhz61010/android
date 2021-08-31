package com.leovp.floatview_sdk.util

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics

/**
 * Author: Michael Leo
 * Date: 2021/8/31 15:15
 */

val Context.canDrawOverlays: Boolean
    get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

fun fail(message: String): Nothing {
    throw IllegalArgumentException(message)
}

/**
 * As of API 30(Android 11), you must use Activity context to retrieve screen real size
 */
fun Activity.getRealResolution(): Point {
    val size = Point()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        this.display?.getRealSize(size)
    } else {
        val wm = windowManager
        val display = wm.defaultDisplay
        val displayMetrics = DisplayMetrics()
        display.getRealMetrics(displayMetrics)
        size.x = displayMetrics.widthPixels
        size.y = displayMetrics.heightPixels
    }
    return size
}

fun Activity.getAvailableResolution(): Point {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val wm = windowManager
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

val Activity.screenRealWidth get() = getRealResolution().x

val Activity.screenRealHeight get() = getRealResolution().y

val Activity.screenAvailableHeight get() = getAvailableResolution().y

val Context.statusBarHeight
    get() : Int {
        var result = 0
        val resourceId = this.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }