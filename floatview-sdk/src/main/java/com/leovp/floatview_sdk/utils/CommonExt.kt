package com.leovp.floatview_sdk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Size
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Author: Michael Leo
 * Date: 2022/6/14 16:46
 */
internal val Context.canDrawOverlays: Boolean
    get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

/**
 * @return The returned height value includes the height of status bar but excludes the height of navigation bar.
 */
internal val Context.screenAvailableResolution: Size
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics =
                    (getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
            // Gets all excluding insets
            val windowInsets = metrics.windowInsets
            val insets =
                    windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())

            val insetsWidth = insets.right + insets.left
            val insetsHeight = insets.top + insets.bottom

            // Legacy size that Display#getSize reports
            val bounds = metrics.bounds
            Size(bounds.width() - insetsWidth, bounds.height() - insetsHeight)
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
            return Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

internal val Context.screenRealResolution: Size
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //        this.display?.getRealSize(size)
            val bounds =
                    (getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds
            Size(bounds.width(), bounds.height())
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION") (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(
                displayMetrics)
            Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

internal val Context.screenWidth: Int get() = screenRealResolution.width

/**
 * This height includes the height of status bar but excludes the height of navigation bar.
 */
internal val Context.screenAvailableHeight: Int get() = screenAvailableResolution.height

internal val Context.statusBarHeight
    @SuppressLint("DiscouragedApi")
    get() : Int {
        var result = 0
        val resourceId = this.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }