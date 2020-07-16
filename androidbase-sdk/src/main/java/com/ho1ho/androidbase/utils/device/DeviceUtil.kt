package com.ho1ho.androidbase.utils.device

import android.app.ActivityManager
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
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
    private var EXTREME_LARGE_SCREEN_MULTIPLE_TIMES = 0

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

    fun getRatio(ctx: Context): Float {
        val p = getResolution(ctx)
        return 1.0f * p.x / p.y
    }

    val uuid: String = UUID.randomUUID().toString()

    private fun getImei(ctx: Context, slotId: Int): String {
        return try {
            val manager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val method = manager.javaClass.getMethod("getImei", Int::class.javaPrimitiveType)
            method.invoke(manager, slotId) as String
        } catch (e: Exception) {
            LLog.e(TAG, "getIMEI error. msg=${e.message}")
            ""
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

    fun getDeviceInfo(ctx: Context): String {
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