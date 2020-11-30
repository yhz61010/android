package com.leovp.androidbase.exts.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.DisplayMetrics
import com.leovp.androidbase.utils.device.DeviceUtil
import java.lang.reflect.Method
import java.util.*

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

val isHuaWei: Boolean get() = VENDOR_HUAWEI.equals(DeviceUtil.manufacturer, ignoreCase = true)
val isXiaoMi: Boolean get() = VENDOR_XIAOMI.equals(DeviceUtil.manufacturer, ignoreCase = true)
val isOppo: Boolean get() = VENDOR_OPPO.equals(DeviceUtil.manufacturer, ignoreCase = true)
val isOnePlus: Boolean get() = VENDOR_ONEPLUS.equals(DeviceUtil.manufacturer, ignoreCase = true)
val isVivo: Boolean get() = VENDOR_VIVO.equals(DeviceUtil.manufacturer, ignoreCase = true)
val isSamsung: Boolean get() = VENDOR_SAMSUNG.equals(DeviceUtil.manufacturer, ignoreCase = true)

fun Context.getAvailableResolution(): Point {
    val wm = app.windowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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

        val displayMetrics = this.resources.displayMetrics
        return runCatching { Point(displayMetrics.widthPixels, displayMetrics.heightPixels) }.getOrDefault(Point())
    }
}

fun Context.getRealResolution(): Point {
    val size = Point()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        this.display?.getRealSize(size)
    } else {
        val wm = app.windowManager
        val display = wm.defaultDisplay
        val displayMetrics = DisplayMetrics()
        display.getRealMetrics(displayMetrics)
        size.x = displayMetrics.widthPixels
        size.y = displayMetrics.heightPixels
    }
    return size
}

val Context.screenWidth get() = getRealResolution().x

val Context.screenRealHeight get() = getRealResolution().y

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

internal fun getNavigationBarName(): String {
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

fun calculateNotchRect(ctx: Context, notchWidth: Int, notchHeight: Int): Rect {
    val screenSize = ctx.getRealResolution()
    val screenWidth = screenSize.x
    val screenHeight = screenSize.y
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int
    if (ctx.isPortrait) {
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
        return 1.0f * p.y / p.x
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
        e.printStackTrace()
        return null
    }
}

fun getDimenInPixel(name: String): Int {
    val resourceId = app.resources.getIdentifier(name, "dimen", "android")
    return if (resourceId > 0) app.resources.getDimensionPixelSize(resourceId) else -1
}