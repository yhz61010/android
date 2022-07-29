@file:Suppress("unused")

package com.leovp.lib_common_android.exts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo.*
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowInsets
import androidx.annotation.IntRange
import com.leovp.lib_common_android.utils.DeviceProp
import com.leovp.lib_common_android.utils.DeviceUtil
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 20-11-30 下午3:43
 */

const val VENDOR_HUAWEI = "HUAWEI"
const val VENDOR_XIAOMI = "xiaomi"
const val VENDOR_OPPO = "OPPO"
const val VENDOR_VIVO = "vivo"
const val VENDOR_ONE_PLUS = "OnePlus"
const val VENDOR_SAMSUNG = "samsung"
const val VENDOR_GOOGLE = "Google"
const val VENDOR_OTHER = "other"

val Context.isHuaWei: Boolean
    get() = VENDOR_HUAWEI.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isXiaoMi: Boolean
    get() = VENDOR_XIAOMI.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isOppo: Boolean
    get() = VENDOR_OPPO.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isOnePlus: Boolean
    get() = VENDOR_ONE_PLUS.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isVivo: Boolean
    get() = VENDOR_VIVO.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isSamsung: Boolean
    get() = VENDOR_SAMSUNG.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isGoogle: Boolean
    get() = VENDOR_GOOGLE.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)

val Context.densityDpi get(): Int = this.resources.displayMetrics.densityDpi
val Context.density get(): Float = this.resources.displayMetrics.density

/**
 * @return The returned height value includes the height of status bar but excludes the height of navigation bar.
 */
val Context.screenAvailableResolution: Size
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
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

val Context.screenRealResolution: Size
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //        this.display?.getRealSize(size)
            val bounds = windowManager.currentWindowMetrics.bounds
            Size(bounds.width(), bounds.height())
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

val Context.screenWidth: Int get() = screenRealResolution.width

val Context.screenRealHeight: Int get() = screenRealResolution.height

/**
 * This height includes the height of status bar but excludes the height of navigation bar.
 */
val Context.screenAvailableHeight: Int get() = screenAvailableResolution.height

/**
 * @param surfaceRotation The value may be:
 *
 * Surface.ROTATION_0 (no rotation),
 * Surface.ROTATION_90,
 * Surface.ROTATION_180,
 * or Surface.ROTATION_270.
 *
 * @return The screen width in current screen orientation. If parameter `surfaceRotation`
 *         is not a valid value, `-1` will be returned.
 */
fun Context.getScreenWidth(surfaceRotation: Int): Int {
    return when (surfaceRotation) {
        Surface.ROTATION_0,
        Surface.ROTATION_180 -> min(screenWidth, screenRealHeight)
        Surface.ROTATION_90,
        Surface.ROTATION_270 -> max(screenWidth, screenRealHeight)
        else                 -> -1
    }
}

/**
 * @param surfaceRotation The value may be:
 *
 * Surface.ROTATION_0 (no rotation),
 * Surface.ROTATION_90,
 * Surface.ROTATION_180,
 * or Surface.ROTATION_270.
 *
 * @return The screen height in current screen orientation. If parameter `surfaceRotation`
 *         is not a valid value, `-1` will be returned.
 */
fun Context.getScreenHeight(surfaceRotation: Int): Int {
    return when (surfaceRotation) {
        Surface.ROTATION_0,
        Surface.ROTATION_180 -> max(screenWidth, screenRealHeight)
        Surface.ROTATION_90,
        Surface.ROTATION_270 -> min(screenWidth, screenRealHeight)
        else                 -> -1
    }
}

/**
 * @param surfaceRotation The value may be:
 *
 * Surface.ROTATION_0 (no rotation),
 * Surface.ROTATION_90,
 * Surface.ROTATION_180,
 * or Surface.ROTATION_270.
 *
 * @return The available screen height in current screen orientation. If parameter `surfaceRotation`
 *         is not a valid value, `-1` will be returned.
 */
fun Context.getScreenAvailableHeight(surfaceRotation: Int): Int {
    return when (surfaceRotation) {
        Surface.ROTATION_0,
        Surface.ROTATION_180 -> max(screenWidth, screenAvailableHeight)
        Surface.ROTATION_90,
        Surface.ROTATION_270 -> min(screenWidth, screenAvailableHeight)
        else                 -> -1
    }
}

val Context.statusBarHeight
    @SuppressLint("DiscouragedApi") get() : Int {
        var result = 0
        val resourceId = this.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

val Context.isFullScreenDevice get(): Boolean = screenRatio >= 1.97f

/**
 * Need to investigate Window.ID_ANDROID_CONTENT
 */
//    fun getTitleHeight(activity: Activity) = activity.window.findViewById<View>(Window.ID_ANDROID_CONTENT).top

//val Context.isNavigationGestureEnabled
//    @SuppressLint("ObsoleteSdkInt")
//    get(): Boolean {
//        val value = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
//            Settings.System.getInt(contentResolver, getNavigationBarName(), 0)
//        } else {
//            Settings.Global.getInt(contentResolver, getNavigationBarName(), 0)
//        }
//        return value != 0
//    }

private fun Context.getNavigationBarName(): String {
    val brand = Build.BRAND
    if (TextUtils.isEmpty(brand)) return "navigationbar_is_min"
    return when {
        isHuaWei -> "navigationbar_is_min"
        isXiaoMi -> "force_fsg_nav_bar"
        isVivo   -> "navigation_gesture_on"
        isOppo   -> "navigation_gesture_on"
        else     -> "navigationbar_is_min"
    }
}

/**
 * So far, the only valid way to check whether the navigation bar is shown is using the difference between screen real height
 * and available height.
 * This is the only way that I've known works.
 */
val Context.isNavigationBarShown
    get() : Boolean {
        return screenRealHeight - screenAvailableHeight > 0
        ////        val view = activity.findViewById<View>(android.R.id.navigationBarBackground) ?: return false
        ////        val visible = view.visibility
        ////        return !(visible == View.GONE || visible == View.INVISIBLE)
        //
        //        // In full screen(AKA all screen) device, this method will return `true`.
        //        val resourceId = resources.getIdentifier("config_showNavigationBar", "bool", "android")
        //        return if (resourceId > 0) {
        //            resources.getBoolean(resourceId)
        //        } else false
    }

//@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
//fun doesDeviceHasNavigationBar(): Boolean {
//    return runCatching {
//        // IWindowManager windowManagerService = WindowManagerGlobal.getWindowManagerService();
//        val windowManagerGlobalClass = Class.forName("android.view.WindowManagerGlobal")
//        val getWmServiceMethod: Method = windowManagerGlobalClass.getDeclaredMethod("getWindowManagerService")
//        getWmServiceMethod.isAccessible = true
//        // getWindowManagerService is a static method, so invoke with null
//        val iWindowManager: Any = getWmServiceMethod.invoke(null)!!
//
//        val iWindowManagerClass: Class<*> = iWindowManager.javaClass
//        val hasNavBarMethod: Method = iWindowManagerClass.getDeclaredMethod("hasNavigationBar")
//        hasNavBarMethod.isAccessible = true
//        hasNavBarMethod.invoke(iWindowManager) as Boolean
//    }.getOrDefault(false)
//}

/**
 * In some devices(Like Google Pixel), although I've selected [Gesture navigation],
 * the real height of navigation bar is still the same as the height of [2/3-button navigation].
 * In order to get the exactly height of navigation bar, I can not use the value which get from `navigation_bar_height`.
 *
 * ```
 * ⎸             ⎸
 * ⎸             ⎸ ⎽⎽
 * ⎸     ⎻       ⎸ ⎽⎽   The height of navigation bar in [Gesture navigation] mode in some devices.
 *
 *
 * ⎸             ⎸ ⎺↓⎺
 * ⎸             ⎸        The height of navigation bar in [Gesture navigation] mode in some devices.
 * ⎸     ⎻       ⎸ ⎽↑⎽
 *
 *
 * ⎸             ⎸ ⎺↓⎺
 * ⎸ ◀︎  ●   ◼   ⎸        The height of navigation bar in [2/3-button navigation] mode in some devices.
 * ⎸             ⎸ ⎽↑⎽
 * ```
 */
val Context.navigationBarHeight
    get() : Int {
        //        var result = 0
        //        val resourceId = this.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        //        if (resourceId > 0) {
        //            result = this.resources.getDimensionPixelSize(resourceId)
        //        }
        //        return result

        return screenRealHeight - screenAvailableHeight
    }

fun calculateNotchRect(act: Activity, notchWidth: Int, notchHeight: Int): Rect {
    val screenSize = act.screenRealResolution
    val screenWidth = screenSize.width
    val screenHeight = screenSize.height
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int
    if (act.isPortrait) {
        left = (screenWidth - notchWidth) / 2
        top = 0
        right = left + notchWidth
        bottom = notchHeight
    } else {
        left = 0
        top = (screenHeight - notchWidth) / 2
        right = notchHeight
        bottom = top + notchWidth
    }
    return Rect(left, top, right, bottom)
}

val Context.screenRatio
    get(): Float {
        val p = screenRealResolution
        return 1.0f * max(p.width, p.height) / min(p.width, p.height)
    }

fun getUuid(): String = UUID.randomUUID().toString()

fun getImei(ctx: Context): String? {
    val imei0 = getImei(ctx, 0)
    val imei1 = getImei(ctx, 1)
    return if (imei0.isNullOrBlank()) imei1 else imei0
}

fun getImei(ctx: Context, slotId: Int): String? {
    return try {
        val manager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val method = manager.javaClass.getMethod("getImei", Int::class.javaPrimitiveType)
        method.invoke(manager, slotId) as String
    } catch (e: Exception) {
        //        e.printStackTrace()
        return null
    }
}

@SuppressLint("DiscouragedApi")
fun Context.getDimenInPixel(name: String): Int {
    val resourceId = resources.getIdentifier(name, "dimen", "android")
    return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else -1
}

fun isProbablyAnEmulator(): Boolean {
    return (Build.FINGERPRINT.startsWith("google/sdk_gphone_")
            && Build.FINGERPRINT.endsWith(":user/release-keys")
            && Build.MANUFACTURER == "Google"
            && Build.PRODUCT.startsWith("sdk_gphone_")
            && Build.BRAND == "google"
            && Build.MODEL.startsWith("sdk_gphone_"))

            // Android SDK emulator
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || "QC_Reference_Phone" == Build.BOARD

            // bluestacks
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.HOST.startsWith("Build") // MSI App Player
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || Build.PRODUCT == "google_sdk"
            // another Android SDK emulator check
            || DeviceProp.getSystemProperty("ro.kernel.qemu") == "1"
}

fun Context.isTablet(): Boolean {
    return (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
}

// ================================

fun isPortrait(@IntRange(from = 0, to = 359) degree: Int,
    @IntRange(from = 0, to = 45) thresholdInDegree: Int = 30): Boolean {
    return isNormalPortrait(degree,
        thresholdInDegree) || isReversePortrait(degree, thresholdInDegree)
}

fun isLandscape(@IntRange(from = 0, to = 359) degree: Int,
    @IntRange(from = 0, to = 45) thresholdInDegree: Int = 30): Boolean {
    return isNormalLandscape(degree,
        thresholdInDegree) || isReverseLandscape(degree, thresholdInDegree)
}

// ---------------

/**
 * **Attention:**
 * Only if the device is in portrait mode regardless of **Normal Portrait** or **Reverse Portrait**,
 * `true` will be returned.
 *
 * @param surfaceRotation The value may be
 * Surface.ROTATION_0 (no rotation),
 * Surface.ROTATION_90,
 * Surface.ROTATION_180,
 * or Surface.ROTATION_270.
 */
fun isPortrait(surfaceRotation: Int): Boolean =
        Surface.ROTATION_0 == surfaceRotation || Surface.ROTATION_180 == surfaceRotation

/**
 * **Attention:**
 * Only if the device is in landscape mode regardless of **Normal Landscape** or **Reverse Landscape**,
 * `true` will be returned.
 *
 * @param surfaceRotation The value may be
 * Surface.ROTATION_0 (no rotation),
 * Surface.ROTATION_90,
 * Surface.ROTATION_180,
 * or Surface.ROTATION_270.
 */
fun isLandscape(surfaceRotation: Int): Boolean =
        Surface.ROTATION_90 == surfaceRotation || Surface.ROTATION_270 == surfaceRotation

// ---------------

/** Only if the device is just in **Normal Portrait** mode, `true` will be returned. */
fun isNormalPortrait(@IntRange(from = 0, to = 359) degree: Int,
    @IntRange(from = 0, to = 45) threshold: Int = 30): Boolean {
    return (degree in 0..threshold) || (degree in (360 - threshold)..359)
}

/** Only if the device is just in **Reverse Portrait** mode, `true` will be returned. */
fun isReversePortrait(@IntRange(from = 0, to = 359) degree: Int,
    @IntRange(from = 0, to = 45) threshold: Int = 30): Boolean {
    return degree in (180 - threshold)..(180 + threshold)
}

/** Only if the device is just in **Normal Landscape** mode, `true` will be returned. */
fun isNormalLandscape(@IntRange(from = 0, to = 359) degree: Int,
    @IntRange(from = 0, to = 45) threshold: Int = 30): Boolean {
    return degree in (270 - threshold)..(270 + threshold)
}

/** Only if the device is just in **Reverse Landscape** mode, `true` will be returned. */
fun isReverseLandscape(@IntRange(from = 0, to = 359) degree: Int,
    @IntRange(from = 0, to = 45) threshold: Int = 30): Boolean {
    return degree in (90 - threshold)..(90 + threshold)
}

// ---------------

/**
 * @return The result is one of the following value:
 *
 * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
 * - OrientationEventListener.ORIENTATION_UNKNOWN
 */
fun getScreenOrientation(@IntRange(from = 0, to = 359) degree: Int,
    @IntRange(from = 0, to = 45) threshold: Int = 30): Int {
    return when {
        isNormalPortrait(degree, threshold)   -> SCREEN_ORIENTATION_PORTRAIT
        isReversePortrait(degree, threshold)  -> SCREEN_ORIENTATION_REVERSE_PORTRAIT
        isNormalLandscape(degree, threshold)  -> SCREEN_ORIENTATION_LANDSCAPE
        isReverseLandscape(degree, threshold) -> SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else                                  -> OrientationEventListener.ORIENTATION_UNKNOWN
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
val Context.screenSurfaceRotation: Int
    @Suppress("DEPRECATION")
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display!!.rotation else windowManager.defaultDisplay.rotation
// =================

val SURFACE_ROTATION_TO_DEGREE = mapOf(Surface.ROTATION_0 to 0,
    Surface.ROTATION_90 to 90,
    Surface.ROTATION_180 to 180,
    Surface.ROTATION_270 to 270)

val DEGREE_TO_SURFACE_ROTATION = mapOf(0 to Surface.ROTATION_0,
    90 to Surface.ROTATION_90,
    180 to Surface.ROTATION_180,
    270 to Surface.ROTATION_270)

val SCREEN_ORIENTATION_TO_SURFACE_ORIENTATIONS = mapOf(
    SCREEN_ORIENTATION_PORTRAIT to Surface.ROTATION_0,
    SCREEN_ORIENTATION_LANDSCAPE to Surface.ROTATION_90,
    SCREEN_ORIENTATION_REVERSE_PORTRAIT to Surface.ROTATION_180,
    SCREEN_ORIENTATION_REVERSE_LANDSCAPE to Surface.ROTATION_270
)

val Int.screenOrientationName: String
    get() = when (this) {
        SCREEN_ORIENTATION_PORTRAIT          -> "Portrait"
        SCREEN_ORIENTATION_LANDSCAPE         -> "Landscape"
        SCREEN_ORIENTATION_REVERSE_PORTRAIT  -> "Reverse Portrait"
        SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> "Reverse Landscape"
        else                                 -> "Unknown"
    }

val Int.surfaceRotationLiteralName: String
    get() = when (this) {
        Surface.ROTATION_0   -> "ROTATION_0"
        Surface.ROTATION_90  -> "ROTATION_90"
        Surface.ROTATION_180 -> "ROTATION_180"
        Surface.ROTATION_270 -> "ROTATION_270"
        else                 -> "Unknown"
    }

val Int.surfaceRotationName: String
    get() = when (this) {
        Surface.ROTATION_0   -> "Portrait"
        Surface.ROTATION_90  -> "Landscape"
        Surface.ROTATION_180 -> "Reverse Portrait"
        Surface.ROTATION_270 -> "Reverse Landscape"
        else                 -> "Unknown"
    }