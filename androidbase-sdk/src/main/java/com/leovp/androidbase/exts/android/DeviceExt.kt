package com.leovp.androidbase.exts.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.IntRange
import com.leovp.androidbase.utils.device.DeviceProp
import com.leovp.androidbase.utils.device.DeviceUtil
import java.lang.reflect.Method
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

fun Context.getAvailableResolution(): Point {
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

/**
 * As of API 30(Android 11), you must use Activity context to retrieve screen real size
 */
fun Context.getRealResolution(): Point {
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

fun Context.getScreenRealWidth() = getRealResolution().x

fun Context.getScreenRealHeight() = getRealResolution().y

fun Context.getScreenAvailableHeight() = getAvailableResolution().y

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

val Context.isNavigationGestureEnabled
    @SuppressLint("ObsoleteSdkInt")
    get(): Boolean {
        val value = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Settings.System.getInt(contentResolver, getNavigationBarName(), 0)
        } else {
            Settings.Global.getInt(contentResolver, getNavigationBarName(), 0)
        }
        return value != 0
    }

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
 * In full screen(AKA all screen) device, this method will return `true`.
 */
val Context.isNavigationBarShown
    get() : Boolean {
//        val view = activity.findViewById<View>(android.R.id.navigationBarBackground) ?: return false
//        val visible = view.visibility
//        return !(visible == View.GONE || visible == View.INVISIBLE)
        val resourceId = resources.getIdentifier("config_showNavigationBar", "bool", "android")
        return if (resourceId > 0) {
            resources.getBoolean(resourceId)
        } else false
    }

@SuppressLint("PrivateApi")
fun doesDeviceHasNavigationBar(): Boolean {
    return runCatching {
        // IWindowManager windowManagerService = WindowManagerGlobal.getWindowManagerService();
        val windowManagerGlobalClass = Class.forName("android.view.WindowManagerGlobal")
        val getWmServiceMethod: Method = windowManagerGlobalClass.getDeclaredMethod("getWindowManagerService")
        getWmServiceMethod.isAccessible = true
        // getWindowManagerService is a static method, so invoke with null
        val iWindowManager: Any = getWmServiceMethod.invoke(null)!!

        val iWindowManagerClass: Class<*> = iWindowManager.javaClass
        val hasNavBarMethod: Method = iWindowManagerClass.getDeclaredMethod("hasNavigationBar")
        hasNavBarMethod.isAccessible = true
        hasNavBarMethod.invoke(iWindowManager) as Boolean
    }.getOrDefault(false)
}

val Context.navigationBarHeight
    get() : Int {
        var result = 0
        val resourceId = this.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

fun calculateNotchRect(act: Activity, notchWidth: Int, notchHeight: Int): Rect {
    val screenSize = act.getRealResolution()
    val screenWidth = screenSize.x
    val screenHeight = screenSize.y
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
        return 1.0f * max(p.x, p.y) / min(p.x, p.y)
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