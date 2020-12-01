package com.leovp.androidbase.utils.device

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.SystemClock
import com.leovp.androidbase.exts.android.*
import com.leovp.androidbase.utils.shell.ShellUtil
import java.io.File

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午3:23
 */

@Suppress("WeakerAccess", "unused", "UNUSED_PARAMETER")
object DeviceUtil {
    private const val TAG = "DeviceUtil"

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

    /**
     * The result is:
     * [0]: Used memory in megs
     * [1]: Total memory in megs
     * [2]: Used memory in percents
     */
    fun getMemInfoInMegs(): Array<Any> {
        val mi = ActivityManager.MemoryInfo()
        app.activityManager.getMemoryInfo(mi)
        val availableMegs = mi.availMem / 0x100000L
        val totalMegs = mi.totalMem / 0x100000L
        val percentUsed = (mi.totalMem - mi.availMem) * 100.0F / mi.totalMem
        return arrayOf(totalMegs - availableMegs, totalMegs, percentUsed)
    }

    /**
     * As of API 30(Android 11), you must use Activity context to retrieve screen real size
     */
    fun getDeviceInfo(act: Activity): String {
        return runCatching {
            val st = SystemClock.elapsedRealtimeNanos()
            val memInfo = getMemInfoInMegs()
            val screenSize = act.getRealResolution()
            val availableSize = app.getAvailableResolution()
            val statusBarHeight = app.statusBarHeight
            val navBarHeight = app.navigationBarHeight
            """
            Device basic information:
            App version: ${app.versionName}(${app.versionCode})
            Manufacturer: $manufacturer
            Brand: $brand
            OsVersion: $osVersion($osVersionSdkInt)
            DeviceName: $deviceName
            Model: $model
            Product: $product
            CPU: $cpuQualifiedName($cpuCoreCount cores @ ${cpuMinFreq / 1000}MHz~${"%.2f".format(cpuMaxFreq / 1000_000F)}GHz)
            Supported ABIS: ${supportedCpuArchs.contentToString()}
            Display: $display
            Screen: ${screenSize.x}x${screenSize.y}(${app.densityDpi}:${app.density})(${act.screenRatio})  (${availableSize.x}x${availableSize.y})  (${availableSize.y}+$statusBarHeight+$navBarHeight=${availableSize.y + statusBarHeight + navBarHeight})
            MemoryUsage: ${memInfo[0]}MB/${memInfo[1]}MB  ${memInfo[2]}% Used
            IMEI:
                    slot0: ${getImei(app, 0) ?: "NA"}
                    slot1: ${getImei(app, 1) ?: "NA"}
            Device Features:
                    Full screen device        : ${act.isFullScreenDevice}
                    Device has navigation bar : ${doesDeviceHasNavigationBar()}
                    Navigation bar is showing : ${app.isNavigationBarShown} (PS: In full screen(AKA all screen) device, this value is always 'true'.)
                    Navigation gesture enable : ${app.isNavigationGestureEnabled}

            Cost: ${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us 
            """.trimIndent()
        }.getOrDefault("")
    }
}