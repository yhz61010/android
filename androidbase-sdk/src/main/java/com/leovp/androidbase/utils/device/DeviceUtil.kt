package com.leovp.androidbase.utils.device

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.WindowManager
import com.leovp.androidbase.utils.AppUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.shell.ShellUtil
import java.io.File
import java.lang.reflect.Method
import java.util.*
import kotlin.math.max

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午3:23
 */

@Suppress("WeakerAccess", "unused", "UNUSED_PARAMETER")
object DeviceUtil {
    private val TAG = DeviceUtil::class.java.simpleName
    const val VENDOR_HUAWEI = "HUAWEI"
    const val VENDOR_XIAOMI = "xiaomi"
    const val VENDOR_OPPO = "OPPO"
    const val VENDOR_VIVO = "vivo"
    const val VENDOR_ONEPLUS = "OnePlus"
    const val VENDOR_SAMSUNG = "samsung"
    const val VENDOR_OTHER = "other"
    private const val EXTREME_LARGE_SCREEN_THRESHOLD = 2560
    var EXTREME_LARGE_SCREEN_MULTIPLE_TIMES = 0

    fun isPortrait(ctx: Context) = ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    fun isLandscape(ctx: Context) = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun getDensity(ctx: Context) = ctx.resources.displayMetrics.densityDpi

    val isHuaWei: Boolean
        get() = VENDOR_HUAWEI.equals(manufacturer, ignoreCase = true)

    val isXiaoMi: Boolean
        get() = VENDOR_XIAOMI.equals(manufacturer, ignoreCase = true)

    val isOppo: Boolean
        get() = VENDOR_OPPO.equals(manufacturer, ignoreCase = true)

    val isOnePlus: Boolean
        get() = VENDOR_ONEPLUS.equals(manufacturer, ignoreCase = true)

    val isVivo: Boolean
        get() = VENDOR_VIVO.equals(manufacturer, ignoreCase = true)

    val isSamsung: Boolean
        get() = VENDOR_SAMSUNG.equals(manufacturer, ignoreCase = true)

    fun getAvailableResolution(ctx: Context): Point {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
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

            val displayMetrics = ctx.resources.displayMetrics
            return runCatching { Point(displayMetrics.widthPixels, displayMetrics.heightPixels) }.getOrDefault(Point())
        }
    }

    fun getRealResolution(ctx: Context): Point {
        val size = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ctx.display?.getRealSize(size)
        } else {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            val displayMetrics = DisplayMetrics()
            display.getRealMetrics(displayMetrics)
            size.x = displayMetrics.widthPixels
            size.y = displayMetrics.heightPixels
        }
        return size
    }

    fun getStatusBarHeight(context: Context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }


    @SuppressLint("ObsoleteSdkInt")
    fun isFullScreenDevice(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        } else {
            if (getScreenRatio(ctx) >= 1.97f) {
                return true
            }
            return false
        }
    }

    /**
     * Need to investigate Window.ID_ANDROID_CONTENT
     */
//    fun getTitleHeight(activity: Activity) = activity.window.findViewById<View>(Window.ID_ANDROID_CONTENT).top

    @SuppressLint("ObsoleteSdkInt")
    fun navigationGestureEnabled(context: Context): Boolean {
        val value = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Settings.System.getInt(context.contentResolver, getNavigationBarName(), 0)
        } else {
            Settings.Global.getInt(context.contentResolver, getNavigationBarName(), 0)
        }
        return value != 0
    }

    private fun getNavigationBarName(): String {
        val brand = Build.BRAND
        if (TextUtils.isEmpty(brand)) return "navigationbar_is_min"
        return if (brand.equals("HUAWEI", ignoreCase = true) || brand.equals("HONOR", ignoreCase = true)) {
            "navigationbar_is_min"
        } else if (brand.equals("XIAOMI", ignoreCase = true)) {
            "force_fsg_nav_bar"
        } else if (brand.equals("VIVO", ignoreCase = true)) {
            "navigation_gesture_on"
        } else if (brand.equals("OPPO", ignoreCase = true)) {
            "navigation_gesture_on"
        } else {
            "navigationbar_is_min"
        }
    }

    /**
     * In full screen(AKA all screen) device, this method will return `true`.
     */
    fun isNavigationBarShown(ctx: Context): Boolean {
//        val view = activity.findViewById<View>(android.R.id.navigationBarBackground) ?: return false
//        val visible = view.visibility
//        return !(visible == View.GONE || visible == View.INVISIBLE)

        val resourceId = ctx.resources.getIdentifier("config_showNavigationBar", "bool", "android")
        return if (resourceId > 0) {
            ctx.resources.getBoolean(resourceId)
        } else false
    }

    fun hasNavigationBar(): Boolean {
        return runCatching {
            // IWindowManager windowManagerService = WindowManagerGlobal.getWindowManagerService();
            val windowManagerGlobalClass = Class.forName("android.view.WindowManagerGlobal")
            val getWmServiceMethod: Method = windowManagerGlobalClass.getDeclaredMethod("getWindowManagerService")
            getWmServiceMethod.isAccessible = true
            // getWindowManagerService is a static method, so invoke with null
            val iWindowManager: Any = getWmServiceMethod.invoke(null)

            val iWindowManagerClass: Class<*> = iWindowManager.javaClass
            val hasNavBarMethod: Method = iWindowManagerClass.getDeclaredMethod("hasNavigationBar")
            hasNavBarMethod.isAccessible = true
            hasNavBarMethod.invoke(iWindowManager) as Boolean
        }.getOrDefault(false)
    }

    fun getNavigationBarHeight(ctx: Context): Int {
        var result = 0
        val resourceId = ctx.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = ctx.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun calculateNotchRect(ctx: Context, notchWidth: Int, notchHeight: Int): Rect {
        val screenSize = getRealResolution(ctx)
        val screenWidth = screenSize.x
        val screenHeight = screenSize.y
        val left: Int
        val top: Int
        val right: Int
        val bottom: Int
        if (isPortrait(ctx)) {
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

    // XiaoMi
    fun getNotchHeight(ctx: Context): Int {
        val resourceId = ctx.resources.getIdentifier("notch_height", "dimen", "android")
        return if (resourceId > 0) {
            ctx.resources.getDimensionPixelSize(resourceId)
        } else 0
    }

    // XiaoMi
    fun getNotchWidth(ctx: Context): Int {
        val resourceId = ctx.resources.getIdentifier("notch_width", "dimen", "android")
        return if (resourceId > 0) {
            ctx.resources.getDimensionPixelSize(resourceId)
        } else 0
    }

    fun hasNotch(ctx: Context): Boolean {
        if (isOppo)
            return runCatching { ctx.packageManager.hasSystemFeature("com.oppo.feature.screen.heteromorphism") }.getOrDefault(false)
        else return false
    }


    /**
     * Get notch position
     *
     * @return The result is like "0,0:104,72" which means the top left position and bottom right position
     */
    fun getNotchPosition(): String? {
        if (isOppo) {
            return DeviceProp.getSystemProperty("ro.oppo.screen.heteromorphism")
        } else {
            return null
        }
    }

    fun getScreenRatio(ctx: Context): Float {
        val p = getRealResolution(ctx)
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
            LogContext.log.e(TAG, "getIMEI error. msg=${e.message}")
            return null
        }
    }

    private fun isExtremeLargeScreen(screenSize: Point?): Boolean {
        if (screenSize == null) {
            LogContext.log.e(TAG, "screenSize is null")
            return false
        }
        return max(screenSize.x, screenSize.y) > EXTREME_LARGE_SCREEN_THRESHOLD
    }

    private fun isExtremeLargeScreen(width: Int, height: Int): Boolean {
        return max(width, height) > EXTREME_LARGE_SCREEN_THRESHOLD
    }

    fun isExtremeLargeScreen(ctx: Context) = isExtremeLargeScreen(getRealResolution(ctx))

    private fun getSafetyScreenSize(screenSize: Point): Point {
        val extremeLarge = isExtremeLargeScreen(screenSize)
        return if (extremeLarge) {
            getSafetyScreenSize(Point(screenSize.x shr 1, screenSize.y shr 1))
        } else {
            screenSize
        }
    }

    fun getSafetyScreenWidth(ctx: Context) = getSafetyScreenSize(getRealResolution(ctx)).x
    fun getSafetyScreenHeight(ctx: Context) = getSafetyScreenSize(getRealResolution(ctx)).y
    fun getSafetyDensity(ctx: Context) = getSafetyDensity(ctx, getDensity(ctx))

    private fun getExtremeLargeScreenMultipleTimes(ctx: Context): Int {
        val maxCurrentDimension = max(getRealResolution(ctx).x, getRealResolution(ctx).y)
        val calDimension = getSafetyScreenSize(getRealResolution(ctx))
        return maxCurrentDimension / Math.max(calDimension.x, calDimension.y)
    }

    private fun getSafetyDensity(ctx: Context, dimension: Int): Int {
        EXTREME_LARGE_SCREEN_MULTIPLE_TIMES =
            getExtremeLargeScreenMultipleTimes(ctx)
        return if (EXTREME_LARGE_SCREEN_MULTIPLE_TIMES > 1) dimension / EXTREME_LARGE_SCREEN_MULTIPLE_TIMES else dimension
    }

    /**
     * The result is:
     * [0]: Used memory in megs
     * [1]: Total memory in megs
     * [2]: Used memory in percents
     */
    fun getMemInfoInMegs(ctx: Context): Array<Any> {
        val mi = ActivityManager.MemoryInfo()
        val activityManager: ActivityManager =
            ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(mi)
        val availableMegs = mi.availMem / 0x100000L
        val totalMegs = mi.totalMem / 0x100000L
        val percentUsed = (mi.totalMem - mi.availMem) * 100.0F / mi.totalMem
        return arrayOf(totalMegs - availableMegs, totalMegs, percentUsed)
    }

    val isOsVersionHigherThenGingerbread: Boolean
        get() = !(Build.VERSION.RELEASE.startsWith("1.") || Build.VERSION.RELEASE.startsWith("2.0")
                || Build.VERSION.RELEASE.startsWith("2.1") || Build.VERSION.RELEASE.startsWith("2.2")
                || Build.VERSION.RELEASE.startsWith("2.3"))

    val osVersion: String? = Build.VERSION.RELEASE
    val osVersionSdkInt = Build.VERSION.SDK_INT
    val deviceName: String? = Build.DEVICE
    val brand: String? = Build.BRAND
    val manufacturer: String? = Build.MANUFACTURER
    val model: String? = Build.MODEL
    val product: String? = Build.PRODUCT
    val display: String? = Build.DISPLAY
    val cpuName = Build.HARDWARE
    val cpuQualifiedName =
        runCatching {
            ShellUtil.execCmd("cat /proc/cpuinfo | grep -i hardware", false).successMsg.replaceFirst(
                Regex("hardware[\\s\\t]*:", RegexOption.IGNORE_CASE),
                ""
            )
        }.getOrDefault(
            ""
        )
    val supportedCpuArchs: Array<String> = Build.SUPPORTED_ABIS

    val cpuCoreCount = File("/sys/devices/system/cpu/").listFiles { file: File? ->
        file?.name?.matches(Regex("cpu[0-9]+")) ?: false
    }?.size ?: 0

    val cpuMinFreq = runCatching {
        File("/sys/devices/system/cpu/").listFiles { file: File? ->
            file?.name?.matches(Regex("cpu[0-9]+")) ?: false
        }?.map { file -> ShellUtil.execCmd("cat ${file.absolutePath}/cpufreq/cpuinfo_min_freq", false).successMsg.toInt() }?.maxOrNull() ?: -1
    }.getOrDefault(-2)
    val cpuMaxFreq = runCatching {
        File("/sys/devices/system/cpu/").listFiles { file: File? ->
            file?.name?.matches(Regex("cpu[0-9]+")) ?: false
        }?.map { file -> ShellUtil.execCmd("cat ${file.absolutePath}/cpufreq/cpuinfo_max_freq", false).successMsg.toInt() }?.maxOrNull() ?: -1
    }.getOrDefault(-2)

    fun getDeviceInfo(ctx: Context): String {
        return runCatching {
            val st = SystemClock.elapsedRealtimeNanos()
            val memInfo = getMemInfoInMegs(ctx)
            val screenSize = getRealResolution(ctx)
            val availableSize = getAvailableResolution(ctx)
            val statusBarHeight = getStatusBarHeight(ctx)
            val navBarHeight = getNavigationBarHeight(ctx)
            """
            Device basic information:
            App version: ${AppUtil.getVersionName(ctx)}(${AppUtil.getVersionCode(ctx)})
            Manufacturer: $manufacturer
            Brand: $brand
            OsVersion: $osVersion($osVersionSdkInt)
            DeviceName: $deviceName
            Model: $model
            Product: $product
            CPU: $cpuQualifiedName($cpuCoreCount cores @ ${cpuMinFreq / 1000}MHz~${"%.2f".format(cpuMaxFreq / 1000_000F)}GHz)
            Supported ABIS: ${supportedCpuArchs.contentToString()}
            Display: $display
            Screen: ${screenSize.x}x${screenSize.y}(${getDensity(ctx)})(${getScreenRatio(ctx)})  (${availableSize.x}x${availableSize.y})  (${availableSize.y}+$statusBarHeight+$navBarHeight=${availableSize.y + statusBarHeight + navBarHeight})
            Has notch: ${hasNotch(ctx)}
            Notch: (${getNotchPosition()})  ${getNotchWidth(ctx)}x${getNotchHeight(ctx)}
            MemoryUsage: ${memInfo[0]}MB/${memInfo[1]}MB  ${memInfo[2]}% Used
            IMEI:
                    slot0: ${getImei(ctx, 0) ?: "NA"}
                    slot1: ${getImei(ctx, 1) ?: "NA"}
            Device Features:
                    Full screen device        : ${isFullScreenDevice(ctx)}
                    Has navigation bar        : ${hasNavigationBar()}
                    Navigation bar is showing : ${isNavigationBarShown(ctx)} (PS: In full screen(AKA all screen) device, this value is always 'true'.)
                    Navigation gesture enable : ${navigationGestureEnabled(ctx)}

            Cost: ${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us 
            """.trimIndent()
        }.getOrDefault("")
    }
}