package com.ho1ho.androidbase.utils.device

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.ho1ho.androidbase.utils.AppUtil
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.shell.ShellUtil
import java.io.File
import java.util.*
import kotlin.math.max

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午3:23
 */
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

    fun getResolution(ctx: Context): Point {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val size = Point()
        display.getSize(size)
        return size
    }

    fun getResolutionWithVirtualKey(ctx: Context): Point {
        val size = Point()
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val dm = DisplayMetrics()
        val c: Class<*>
        try {
            c = Class.forName("android.view.Display")
            val method = c.getMethod("getRealMetrics", DisplayMetrics::class.java)
            method.invoke(display, dm)
            size.x = dm.widthPixels
            size.y = dm.heightPixels
        } catch (e: Exception) {
            LLog.e(TAG, "getResolutionWithVirtualKey error msg=${e.message}")
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

    /**
     * Action bar or Title bar height
     */
    fun getTitleHeight(activity: Activity) = activity.window.findViewById<View>(Window.ID_ANDROID_CONTENT).top

    //    //获取虚拟按键的高度
    //    public static int getNavigationBarHeight(Context context) {
    //        int result = 0;
    //        if (hasNavBar(context)) {
    //            Resources res = context.getResources();
    //            int resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android");
    //            if (resourceId > 0) {
    //                result = res.getDimensionPixelSize(resourceId);
    //            }
    //        }
    //        return result;
    //    }
    //
    //    /**
    //     * 检查是否存在虚拟按键栏
    //     *
    //     * @param context
    //     * @return
    //     */
    //    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    //    private static boolean hasNavBar(Context context) {
    //        Resources res = context.getResources();
    //        int resourceId = res.getIdentifier("config_showNavigationBar", "bool", "android");
    //        if (resourceId != 0) {
    //            boolean hasNav = res.getBoolean(resourceId);
    //            // check override flag
    //            String sNavBarOverride = getNavBarOverride();
    //            if ("1".equals(sNavBarOverride)) {
    //                hasNav = false;
    //            } else if ("0".equals(sNavBarOverride)) {
    //                hasNav = true;
    //            }
    //            return hasNav;
    //        } else { // fallback
    //            return !ViewConfiguration.get(context).hasPermanentMenuKey();
    //        }
    //    }
    //
    //    /**
    //     * 判断虚拟按键栏是否重写
    //     *
    //     * @return
    //     */
    //    private static String getNavBarOverride() {
    //        String sNavBarOverride = null;
    //        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
    //            try {
    //                Class c = Class.forName("android.os.SystemProperties");
    //                Method m = c.getDeclaredMethod("get", String.class);
    //                m.setAccessible(true);
    //                sNavBarOverride = (String) m.invoke(null, "qemu.hw.mainkeys");
    //            } catch (Throwable e) {
    //            }
    //        }
    //        return sNavBarOverride;
    //    }
    /**
     * 获取虚拟按键的高度
     * 1. 全面屏下
     * 1.1 开启全面屏开关-返回0
     * 1.2 关闭全面屏开关-执行非全面屏下处理方式
     * 2. 非全面屏下
     * 2.1 没有虚拟键-返回0
     * 2.1 虚拟键隐藏-返回0
     * 2.2 虚拟键存在且未隐藏-返回虚拟键实际高度
     */
    fun getNavigationBarHeightIfRoom(context: Context): Int {
        return if (navigationGestureEnabled(context)) {
            0
        } else getCurrentNavigationBarHeight(context as Activity)
    }

    /**
     * 全面屏（是否开启全面屏开关 0 关闭  1 开启）
     *
     * @param context
     * @return
     */
    @SuppressLint("ObsoleteSdkInt")
    fun navigationGestureEnabled(context: Context): Boolean {
        val value = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Settings.System.getInt(context.contentResolver, getNavigationBarName(), 0)
        } else {
            Settings.Global.getInt(context.contentResolver, getNavigationBarName(), 0)
        }
        return value != 0
    }

    /**
     * 获取设备信息（目前支持几大主流的全面屏手机，亲测华为、小米、oppo、魅族、vivo都可以）
     *
     * @return
     */
    private fun getNavigationBarName(): String? {
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
     * 非全面屏下 虚拟键实际高度(隐藏后高度为0)
     *
     * @param activity
     * @return
     */
    fun getCurrentNavigationBarHeight(activity: Activity): Int {
        return if (isNavigationBarShown(activity)) {
            getNavigationBarHeight(activity)
        } else {
            0
        }
    }

    /**
     * 非全面屏下 虚拟按键是否打开
     *
     * @param activity
     * @return
     */
    fun isNavigationBarShown(activity: Activity): Boolean {
        //虚拟键的view,为空或者不可见时是隐藏状态
        val view = activity.findViewById<View>(R.id.navigationBarBackground) ?: return false
        val visible = view.visibility
        return !(visible == View.GONE || visible == View.INVISIBLE)
    }

    /**
     * 非全面屏下 虚拟键高度(无论是否隐藏)
     *
     * @param context
     * @return
     */
    fun getNavigationBarHeight(context: Context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun getScreenRatio(ctx: Context): Float {
        val p = getResolution(ctx)
        return 1.0f * p.x / p.y
    }

    val uuid: String = UUID.randomUUID().toString()

    fun getImei(ctx: Context): String? {
        val imei0 = getImei(ctx, 0)
        val imei1 = getImei(ctx, 1)
        return if (imei0.isNullOrBlank()) imei1 else imei0
    }

    private fun getImei(ctx: Context, slotId: Int): String? {
        return try {
            val manager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val method = manager.javaClass.getMethod("getImei", Int::class.javaPrimitiveType)
            method.invoke(manager, slotId) as String
        } catch (e: Exception) {
            LLog.e(TAG, "getIMEI error. msg=${e.message}")
            return null
        }
    }

    private fun isExtremeLargeScreen(screenSize: Point?): Boolean {
        if (screenSize == null) {
            LLog.e(TAG, "screenSize is null")
            return false
        }
        return max(screenSize.x, screenSize.y) > EXTREME_LARGE_SCREEN_THRESHOLD
    }

    private fun isExtremeLargeScreen(width: Int, height: Int): Boolean {
        return max(width, height) > EXTREME_LARGE_SCREEN_THRESHOLD
    }

    fun isExtremeLargeScreen(ctx: Context): Boolean {
        return isExtremeLargeScreen(
            getResolutionWithVirtualKey(
                ctx
            )
        )
    }

    private fun getSafetyScreenSize(screenSize: Point): Point {
        val extremeLarge = isExtremeLargeScreen(screenSize)
        return if (extremeLarge) {
            getSafetyScreenSize(Point(screenSize.x shr 1, screenSize.y shr 1))
        } else {
            screenSize
        }
    }

    fun getSafetyScreenWidth(ctx: Context) = getSafetyScreenSize(getResolutionWithVirtualKey(ctx)).x
    fun getSafetyScreenHeight(ctx: Context) = getSafetyScreenSize(getResolutionWithVirtualKey(ctx)).y
    fun getSafetyDensity(ctx: Context) = getSafetyDensity(ctx, getDensity(ctx))

    private fun getExtremeLargeScreenMultipleTimes(ctx: Context): Int {
        val maxCurrentDimension =
            Math.max(
                getResolutionWithVirtualKey(ctx).x, getResolutionWithVirtualKey(
                    ctx
                ).y
            )
        val calDimension = getSafetyScreenSize(
            getResolutionWithVirtualKey(ctx)
        )
        return maxCurrentDimension / Math.max(calDimension.x, calDimension.y)
    }

    private fun getSafetyDensity(ctx: Context, dimension: Int): Int {
        EXTREME_LARGE_SCREEN_MULTIPLE_TIMES =
            getExtremeLargeScreenMultipleTimes(ctx)
        return if (EXTREME_LARGE_SCREEN_MULTIPLE_TIMES > 1) dimension / EXTREME_LARGE_SCREEN_MULTIPLE_TIMES else dimension
    }

    val isOsVersionHigherThenGingerbread: Boolean
        get() = !(Build.VERSION.RELEASE.startsWith("1.") || Build.VERSION.RELEASE.startsWith("2.0")
                || Build.VERSION.RELEASE.startsWith("2.1") || Build.VERSION.RELEASE.startsWith("2.2")
                || Build.VERSION.RELEASE.startsWith("2.3"))

    val osVersion = Build.VERSION.RELEASE
    val osVersionSdkInt = Build.VERSION.SDK_INT
    val deviceName = Build.DEVICE
    val brand = Build.BRAND
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    val product = Build.PRODUCT
    val display = Build.DISPLAY
    val others = ""
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
        }?.map { file -> ShellUtil.execCmd("cat ${file.absolutePath}/cpufreq/cpuinfo_min_freq", false).successMsg.toInt() }?.max() ?: -1
    }.getOrDefault(-2)
    val cpuMaxFreq = runCatching {
        File("/sys/devices/system/cpu/").listFiles { file: File? ->
            file?.name?.matches(Regex("cpu[0-9]+")) ?: false
        }?.map { file -> ShellUtil.execCmd("cat ${file.absolutePath}/cpufreq/cpuinfo_max_freq", false).successMsg.toInt() }?.max() ?: -1
    }.getOrDefault(-2)

    fun getNavigationBarName(ctx: Context): String {
        val memInfo = getMemInfoInMegs(ctx)
        val screenSize = getResolutionWithVirtualKey(ctx)
        return """
            Device basic information:
            App version: ${AppUtil.getVersionName(ctx)}
            App code: ${AppUtil.getVersionCode(ctx)}
            Manufacturer: $manufacturer
            Brand: $brand
            OsVersion: $osVersion($osVersionSdkInt)
            DeviceName: $deviceName
            Model: $model
            Product: $product
            Cpu: $cpuQualifiedName($cpuCoreCount cores @ ${cpuMinFreq / 1000}MHz~${"%.2f".format(cpuMaxFreq / 1000_000F)}GHz)
            Supported ABIS: ${supportedCpuArchs.contentToString()}
            Display: $display(${screenSize.x}x${screenSize.y}-${getDensity(ctx)})
            MemoryUsage: ${memInfo[0]}MB/${memInfo[1]}MB  ${memInfo[2]}% Used
            """.trimIndent()
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
}