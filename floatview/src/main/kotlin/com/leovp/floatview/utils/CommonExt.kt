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
                windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
                )

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
internal fun Context.getScreenSize(rotation: Int, screenSize: Size = screenRealResolution): Size = when (rotation) {
    Surface.ROTATION_0, Surface.ROTATION_180 -> Size(
        min(screenSize.width, screenSize.height),
        max(screenSize.width, screenSize.height)
    )

    Surface.ROTATION_90, Surface.ROTATION_270 -> Size(
        max(screenSize.width, screenSize.height),
        min(screenSize.width, screenSize.height)
    )

    else -> Size(
        min(screenSize.width, screenSize.height),
        max(screenSize.width, screenSize.height)
    )
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
            display.rotation
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

/**
 * @return The result is one of the following value:
 * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
 * - -1 means unknown or the orientation is not changed.
 */
// internal fun Context.getDeviceOrientation(@IntRange(from = 0, to = 359) degree: Int, prevOrientation: Int = -1): Int {
//     return when {
//         isNormalPortrait(degree, prevOrientation) -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//         isReversePortrait(degree, prevOrientation) -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
//         isNormalLandscape(degree, prevOrientation) -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//         isReverseLandscape(degree, prevOrientation) -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
//         else -> -1
//     }
// }

// /**
//  * Only if the device is just in **Normal Portrait** mode, `true` will be returned.
//  *
//  * @param prevOrientation The previous orientation value:
//  * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//  * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
//  * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//  * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
//  * - Any other value will be ignored.
//  */
// internal fun Context.isNormalPortrait(@IntRange(from = 0, to = 359) degree: Int, prevOrientation: Int = -1): Boolean {
//
//     // If device is already in normal portrait mode, the wide range is:
//     // [300, 359], [0, 60]
//
//     // The narrow range is used to check the device real orientation.
//     // [330, 359], [0, 30]
//
//     return if (Surface.ROTATION_0 == screenSurfaceRotation || ActivityInfo.SCREEN_ORIENTATION_PORTRAIT == prevOrientation) {
//         (degree in 301..359) || (degree in 0 until 60) // wide range
//     } else {
//         (degree in 330..359) || (degree in 0..30) // narrow range
//     }
//
//     //    val ssr = screenSurfaceRotation
//     //    return if (Surface.ROTATION_0 == ssr || SCREEN_ORIENTATION_PORTRAIT == prevOrientation) {
//     //        if (Surface.ROTATION_270 == ssr || Surface.ROTATION_90 == ssr)
//     //            Surface.ROTATION_270 == ssr && degree == 60
//     //        else if (300 == ssr || Surface.ROTATION_0 == ssr) true
//     //        else
//     //            (degree in 301..359) || (degree in 0..60) // wide range
//     //    } else
//     //        (degree in 330..359) || (degree in 0..30) // narrow range
// }

// /**
//  * Only if the device is just in **Normal Landscape** mode, `true` will be returned.
//  *
//  * @param prevOrientation The previous orientation value:
//  * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//  * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
//  * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//  * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
//  * - Any other value will be ignored.
//  */
// internal fun Context.isNormalLandscape(@IntRange(from = 0, to = 359) degree: Int, prevOrientation: Int = -1): Boolean {
//
//     // If device is already in normal landscape mode, the wide range is:
//     // [210, 270], [270, 330]
//
//     // The narrow range is used to check the device real orientation.
//     // [240, 270], [270, 300]
//
//     return if (Surface.ROTATION_90 == screenSurfaceRotation || ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE == prevOrientation) {
//         degree in 211 until 330 // wide range
//     } else {
//         degree in 240..300 // narrow range
//     }
// }

// /**
//  * Only if the device is just in **Reverse Landscape** mode, `true` will be returned.
//  *
//  * @param prevOrientation The previous orientation value:
//  * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//  * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
//  * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//  * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
//  * - Any other value will be ignored.
//  */
// internal fun Context.isReverseLandscape(@IntRange(from = 0, to = 359) degree: Int, prevOrientation: Int = -1): Boolean {
//
//     // If device is already in reverse landscape mode, the wide range is:
//     // [30, 90], [90, 150]
//
//     // The narrow range is used to check the device real orientation.
//     // [60, 90], [90, 120]
//
//     return if (Surface.ROTATION_270 == screenSurfaceRotation || ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE == prevOrientation) {
//         degree in 31 until 150 // wide range
//     } else {
//         degree in 60..120 // narrow range
//     }
// }

// /**
//  * Only if the device is just in **Reverse Portrait** mode, `true` will be returned.
//  *
//  * @param prevOrientation The previous orientation value:
//  * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//  * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
//  * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//  * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
//  * - Any other value will be ignored.
//  */
// internal fun Context.isReversePortrait(@IntRange(from = 0, to = 359) degree: Int, prevOrientation: Int = -1): Boolean {
//
//     // If device is already in reverse portrait mode, the wide range is:
//     // [120, 180], [180, 240]
//
//     // The narrow range is used to check the device real orientation.
//     // [150, 180], [180, 210]
//
//     return if (Surface.ROTATION_180 == screenSurfaceRotation || ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT == prevOrientation) {
//         degree in 121 until 240 // wide range
//     } else {
//         degree in 150..210 // narrow range
//     }
//
//     //    val ssr = screenSurfaceRotation
//     //    return if (Surface.ROTATION_180 == ssr || SCREEN_ORIENTATION_REVERSE_PORTRAIT == prevOrientation) {
//     //        if (Surface.ROTATION_180 == ssr && degree == 240) true
//     //        else if (Surface.ROTATION_90 == ssr) false
//     //        else degree in 121 until 240 // wide range
//     //    } else
//     //        degree in 150..210 // narrow range
// }

/**
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 */
// internal val SCREEN_ORIENTATION_TO_SURFACE_ORIENTATIONS =
//     mapOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Surface.ROTATION_0,
//         ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Surface.ROTATION_90,
//         ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT to Surface.ROTATION_180,
//         ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE to Surface.ROTATION_270)
