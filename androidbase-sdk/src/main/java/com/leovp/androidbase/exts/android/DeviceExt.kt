package com.leovp.androidbase.exts.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
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
import com.leovp.androidbase.utils.device.DeviceProp
import com.leovp.androidbase.utils.device.DeviceUtil
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
const val VENDOR_ONEPLUS = "OnePlus"
const val VENDOR_SAMSUNG = "samsung"
const val VENDOR_OTHER = "other"

val Context.isHuaWei: Boolean get() = VENDOR_HUAWEI.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isXiaoMi: Boolean get() = VENDOR_XIAOMI.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isOppo: Boolean get() = VENDOR_OPPO.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isOnePlus: Boolean get() = VENDOR_ONEPLUS.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isVivo: Boolean get() = VENDOR_VIVO.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isSamsung: Boolean get() = VENDOR_SAMSUNG.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)

val Context.densityDpi get(): Int = this.resources.displayMetrics.densityDpi
val Context.density get(): Float = this.resources.displayMetrics.density

/**
 * @return The returned height value includes the height of status bar but excludes the height of navigation bar.
 */
fun Context.getAvailableResolution(): Size {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = windowManager.currentWindowMetrics
        // Gets all excluding insets
        val windowInsets = metrics.windowInsets
        val insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())

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

fun Context.getRealResolution(): Size {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//        this.display?.getRealSize(size)
        val bounds = windowManager.currentWindowMetrics.bounds
        Size(bounds.width(), bounds.height())
    } else {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
}

fun Context.getScreenWidth() = getRealResolution().width

fun Context.getScreenRealHeight() = getRealResolution().height

/**
 * This height includes the height of status bar but excludes the height of navigation bar.
 */
fun Context.getScreenAvailableHeight() = getAvailableResolution().height

val Context.statusBarHeight
    get() : Int {
        var result = 0
        val resourceId = this.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

val Context.isFullScreenDevice
    @SuppressLint("ObsoleteSdkInt")
    get(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        } else {
            if (screenRatio >= 1.97f) {
                return true
            }
            return false
        }
    }

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
        isVivo -> "navigation_gesture_on"
        isOppo -> "navigation_gesture_on"
        else -> "navigationbar_is_min"
    }
}

/**
 * So far, the only valid way to check whether the navigation bar is shown is using the difference between screen real height
 * and available height.
 * This is the only way that I've known works.
 */
val Context.isNavigationBarShown
    get() : Boolean {
        return getScreenRealHeight() - getScreenAvailableHeight() > 0
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

        return getScreenRealHeight() - getScreenAvailableHeight()
    }

fun calculateNotchRect(act: Activity, notchWidth: Int, notchHeight: Int): Rect {
    val screenSize = act.getRealResolution()
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
        val p = getRealResolution()
        return 1.0f * max(p.width, p.height) / min(p.width, p.height)
    }

val uuid: String = UUID.randomUUID().toString()

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

fun Context.getDimenInPixel(name: String): Int {
    val resourceId = resources.getIdentifier(name, "dimen", "android")
    return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else -1
}

fun isProbablyAnEmulator(): Boolean {
    return (Build.FINGERPRINT.startsWith("google/sdk_gphone_")
            && Build.FINGERPRINT.endsWith(":user/release-keys")
            && Build.MANUFACTURER == "Google" && Build.PRODUCT.startsWith("sdk_gphone_") && Build.BRAND == "google"
            && Build.MODEL.startsWith("sdk_gphone_")) // Android SDK emulator
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || "QC_Reference_Phone" == Build.BOARD  //bluestacks
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.HOST.startsWith("Build") //MSI App Player
            || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
            || Build.PRODUCT == "google_sdk"
            || DeviceProp.getSystemProperty("ro.kernel.qemu") == "1"// another Android SDK emulator check
}

fun Context.isTablet(): Boolean {
    return (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
}

// ================================

fun isPortraitByDegree(@IntRange(from = 0, to = 359) orientationInDegree: Int, @IntRange(from = 0, to = 45) thresholdInDegree: Int = 45): Boolean {
    return isNormalPortraitByDegree(orientationInDegree, thresholdInDegree) || isReversePortraitByDegree(orientationInDegree, thresholdInDegree)
}

fun isLandscapeByDegree(@IntRange(from = 0, to = 359) orientationInDegree: Int, @IntRange(from = 0, to = 45) thresholdInDegree: Int = 45): Boolean {
    return isNormalLandscapeByDegree(orientationInDegree, thresholdInDegree) || isReverseLandscapeByDegree(orientationInDegree, thresholdInDegree)
}

/**
 * @param rotation The value may be Surface.ROTATION_0 (no rotation), Surface.ROTATION_90, Surface.ROTATION_180, or Surface.ROTATION_270.
 */
fun isPortrait(rotation: Int): Boolean = Surface.ROTATION_0 == rotation || Surface.ROTATION_180 == rotation

/**
 * @param rotation The value may be Surface.ROTATION_0 (no rotation), Surface.ROTATION_90, Surface.ROTATION_180, or Surface.ROTATION_270.
 */
fun isLandscape(rotation: Int): Boolean = Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation

// ---------------

fun isNormalPortraitByDegree(@IntRange(from = 0, to = 359) orientationInDegree: Int, @IntRange(from = 0, to = 45) thresholdInDegree: Int = 45): Boolean {
    return (orientationInDegree in 0..thresholdInDegree) || (orientationInDegree in (360 - thresholdInDegree)..359)
}

fun isReversePortraitByDegree(@IntRange(from = 0, to = 359) orientationInDegree: Int, @IntRange(from = 0, to = 45) thresholdInDegree: Int = 45): Boolean {
    return orientationInDegree >= (180 - thresholdInDegree) && orientationInDegree <= (180 + thresholdInDegree)
}

fun isNormalLandscapeByDegree(@IntRange(from = 0, to = 359) orientationInDegree: Int, @IntRange(from = 0, to = 45) thresholdInDegree: Int = 45): Boolean {
    return orientationInDegree > (270 - thresholdInDegree) && orientationInDegree < (270 + thresholdInDegree)
}

fun isReverseLandscapeByDegree(@IntRange(from = 0, to = 359) orientationInDegree: Int, @IntRange(from = 0, to = 45) thresholdInDegree: Int = 45): Boolean {
    return orientationInDegree > (90 - thresholdInDegree) && orientationInDegree < (90 + thresholdInDegree)
}

fun getOrientationByDegree(@IntRange(from = 0, to = 359) orientationInDegree: Int, @IntRange(from = 0, to = 45) thresholdInDegree: Int = 45): Int {
    return when {
        isNormalPortraitByDegree(orientationInDegree, thresholdInDegree) -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        isReversePortraitByDegree(orientationInDegree, thresholdInDegree) -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        isNormalLandscapeByDegree(orientationInDegree, thresholdInDegree) -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        isReverseLandscapeByDegree(orientationInDegree, thresholdInDegree) -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> OrientationEventListener.ORIENTATION_UNKNOWN
    }
}

// =================