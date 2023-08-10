package com.leovp.floatview.utils

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 2022/6/14 16:46
 */
internal val Context.canDrawOverlays: Boolean
    get() = Settings.canDrawOverlays(this)

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
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(displayMetrics)
            Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

// /**
// * This height includes the height of status bar but excludes the height of navigation bar.
// */
// internal val Context.screenAvailableHeight: Int get() = screenAvailableResolution.height

/**
 * @param rotation The value may be:
 *
 * Surface.ROTATION_0 (no rotation),
 * Surface.ROTATION_90,
 * Surface.ROTATION_180,
 * or Surface.ROTATION_270.
 *
 * @return The screen size in current screen orientation. If parameter `surfaceRotation`
 *         is not a valid value, return available height according to the context.
 */
internal fun Context.getScreenSize(rotation: Int, screenSize: Size = screenRealResolution): Size {
    return when (rotation) {
        Surface.ROTATION_0,
        Surface.ROTATION_180 -> Size(
            min(screenSize.width, screenSize.height),
            max(screenSize.width, screenSize.height)
        )
        Surface.ROTATION_90,
        Surface.ROTATION_270 -> Size(
            max(screenSize.width, screenSize.height),
            min(screenSize.width, screenSize.height)
        )
        else -> Size(
            min(screenSize.width, screenSize.height),
            max(screenSize.width, screenSize.height)
        )
    }
}

/**
 * @return Return the screen rotation(**NOT** device rotation).
 *         The result is one of the following value:
 *
 * - Surface.ROTATION_0
 * - Surface.ROTATION_90
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270
 */
internal val Context.screenSurfaceRotation: Int
    @Suppress("DEPRECATION")
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (this is Service) {
            // On Android 11+, we can't get `display` directly from Service, it will cause
            // the following exception:
            // Tried to obtain display from a Context not associated with one.
            // Only visual Contexts (such as Activity or one created with Context#createWindowContext)
            // or ones created with Context#createDisplayContext are associated with displays.
            // Other types of Contexts are typically related to background entities
            // and may return an arbitrary display.
            //
            // So we need to get screen rotation from `DisplayManager`.
            (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY).rotation
        } else {
            display!!.rotation
        }
    } else {
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }

internal val Context.statusBarHeight
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get(): Int {
        var result = 0
        val resourceId = this.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

internal val isGoogle: Boolean get() = "Google".equals(Build.MANUFACTURER, ignoreCase = true)
