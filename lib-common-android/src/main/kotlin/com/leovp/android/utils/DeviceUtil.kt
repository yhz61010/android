@file:Suppress("MemberVisibilityCanBePrivate")

package com.leovp.android.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import com.leovp.android.exts.activityManager
import com.leovp.android.exts.density
import com.leovp.android.exts.densityDpi
import com.leovp.android.exts.getImei
import com.leovp.android.exts.getRatio
import com.leovp.android.exts.isFullScreenDevice
import com.leovp.android.exts.isNavigationBarShown
import com.leovp.android.exts.isProbablyAnEmulator
import com.leovp.android.exts.isTablet
import com.leovp.android.exts.navigationBarHeight
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.android.exts.screenInch
import com.leovp.android.exts.screenRatio
import com.leovp.android.exts.screenRealResolution
import com.leovp.android.exts.statusBarHeight
import com.leovp.android.exts.toSmartSize
import com.leovp.android.exts.versionCode
import com.leovp.android.exts.versionName
import com.leovp.android.exts.windowManager
import com.leovp.android.exts.xdpi
import com.leovp.android.exts.ydpi
import com.leovp.android.utils.shell.ShellUtil
import com.leovp.kotlin.exts.outputFormatByte
import com.leovp.kotlin.exts.round
import com.leovp.kotlin.utils.SingletonHolder
import java.io.File

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午3:23
 */

@Suppress("WeakerAccess", "unused", "UNUSED_PARAMETER")
class DeviceUtil private constructor(private val ctx: Context) {
    companion object : SingletonHolder<DeviceUtil, Context>(::DeviceUtil) {
        private const val TAG = "DeviceUtil"
    }

    val osVersion: String = Build.VERSION.RELEASE
    val osVersionSdkInt = Build.VERSION.SDK_INT
    val deviceName: String = Build.DEVICE
    val brand: String = Build.BRAND
    val board: String = Build.BOARD
    val host: String = Build.HOST
    val manufacturer: String = Build.MANUFACTURER
    val model: String = Build.MODEL
    val product: String = Build.PRODUCT
    val display: String = Build.DISPLAY
    val hardware: String? = Build.HARDWARE
    val cpuQualifiedName =
        runCatching {
            ShellUtil.execCmd("cat /proc/cpuinfo | grep -i hardware", false).successMsg.replaceFirst(
                Regex("hardware[\\s\\t]*:[\\s\\t]*", RegexOption.IGNORE_CASE),
                ""
            )
        }.getOrDefault(
            ""
        )
    val supportedCpuArchs: Array<String> = Build.SUPPORTED_ABIS
    val cpuArch: String = when {
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "AArch64"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "ARMv7"
        Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
        Build.SUPPORTED_ABIS.contains("x86") -> "x86"
        else -> "NA"
    }
    //        runCatching {
    //        ShellUtil.execCmd("cat /proc/cpuinfo | grep -i Processor", false).successMsg.split('\n')[0].replaceFirst(
    //            Regex("Processor[\\s\\t]*:[\\s\\t]*", RegexOption.IGNORE_CASE),
    //            ""
    //        )
    //    }.getOrDefault(
    //        ""
    //    )

    fun getSerialNumber(): String {
        var serialNo = DeviceProp.getSystemProperty("ro.serialno")
        if (serialNo.isNotBlank()) return serialNo
        serialNo = DeviceProp.getSystemProperty("ro.boot.serialno")
        if (serialNo.isNotBlank()) return serialNo
        serialNo = DeviceProp.getSystemProperty("ril.serialnumber")
        if (serialNo.isNotBlank()) return serialNo
        serialNo = DeviceProp.getSystemProperty("sys.serialnumber")
        if (serialNo.isNotBlank()) return serialNo
        serialNo = DeviceProp.getSystemProperty("gsm.sn1")
        if (serialNo.isNotBlank()) return serialNo
        return ""
    }

    val cpuCoreCount = File("/sys/devices/system/cpu/").listFiles { file: File? ->
        file?.name?.matches(Regex("cpu\\d+")) ?: false
    }?.size ?: 0

    val cpuMinFreq = runCatching {
        File("/sys/devices/system/cpu/").listFiles { file: File? ->
            file?.name?.matches(Regex("cpu\\d+")) ?: false
        }?.maxOfOrNull { file ->
            ShellUtil.execCmd("cat ${file.absolutePath}/cpufreq/cpuinfo_min_freq", false)
                .successMsg.toInt()
        } ?: -1
    }.getOrDefault(-2)
    val cpuMaxFreq = runCatching {
        File("/sys/devices/system/cpu/").listFiles { file: File? ->
            file?.name?.matches(Regex("cpu\\d+")) ?: false
        }?.maxOfOrNull { file ->
            ShellUtil.execCmd("cat ${file.absolutePath}/cpufreq/cpuinfo_max_freq", false).successMsg.toInt()
        } ?: -1
    }.getOrDefault(-2)

    fun getCpuCoreInfoByIndex(index: Int): CpuCoreInfo? = runCatching {
        val online = ShellUtil.execCmd("cat /sys/devices/system/cpu/cpu$index/online", false)
            .successMsg.toInt() != 0
        val minFreq: Int
        val maxFreq: Int
        if (online) {
            minFreq = ShellUtil
                .execCmd("cat /sys/devices/system/cpu/cpu$index/cpufreq/cpuinfo_min_freq", false)
                .successMsg.toInt()
            maxFreq = ShellUtil
                .execCmd("cat /sys/devices/system/cpu/cpu$index/cpufreq/cpuinfo_max_freq", false)
                .successMsg.toInt()
        } else {
            minFreq = 0
            maxFreq = 0
        }
        CpuCoreInfo(online, minFreq, maxFreq)
    }.getOrNull()

    @Keep
    data class CpuCoreInfo(val online: Boolean, val minFreq: Int, val maxFreq: Int)

    /**
     * Unit: mAh
     */
    val batteryCapacity = runCatching {
        val powerProfileClass = "com.android.internal.os.PowerProfile"
        val powerProfile = Class.forName(powerProfileClass)
            .getConstructor(Context::class.java)
            .newInstance(ctx)
        Class.forName(powerProfileClass)
            .getMethod("getBatteryCapacity")
            .invoke(powerProfile) as Double
    }.getOrDefault(0.0)

    /**
     * The result is:
     * [0]: Available memory in bytes
     * [1]: Total memory in bytes
     * [2]: Used percent
     */
    fun getMemInfoInBytes(): Triple<Long, Long, Float> {
        val mi = ActivityManager.MemoryInfo()
        ctx.activityManager.getMemoryInfo(mi)
        val availableBytes = mi.availMem
        val totalBytes = mi.totalMem
        val percentUsed = (mi.totalMem - mi.availMem) * 100.0F / mi.totalMem
        return Triple(availableBytes, totalBytes, percentUsed)
    }

    /**
     * The triple content in array is:
     * [0]: Available size in bytes
     * [1]: Total size in bytes
     * [2]: Used size in percents
     */
    fun getExternalStorageInBytes(): ArrayList<Triple<Long, Long, Float>> {
        val bytesAvailable: ArrayList<Triple<Long, Long, Float>> = ArrayList()
        val externalStorageDirs = ContextCompat.getExternalFilesDirs(ctx, null)
        for (storageDir in externalStorageDirs) {
            val availableSizeInBytes = StatFs(storageDir.path).availableBytes
            val totalSizeInBytes = StatFs(storageDir.path).totalBytes
            val percentUsed = (totalSizeInBytes - availableSizeInBytes) * 100.0F / totalSizeInBytes
            bytesAvailable.add(Triple(availableSizeInBytes, totalSizeInBytes, percentUsed))
        }
        return bytesAvailable
    }

    val externalStorageBytesInReadable: String
        get() {
            val sb = StringBuilder()
            getExternalStorageInBytes().forEachIndexed { index, pair ->
                val used = pair.second - pair.first
                val usedPercent: Float = used * 100F / pair.second
                sb.append(
                    "[$index]=${used.outputFormatByte()}/${pair.second.outputFormatByte()} ${usedPercent.round()}% Used"
                )
                sb.append("\n")
            }
            return sb.deleteAt(sb.length - 1).toString()
        }

    @SuppressLint("MissingPermission")
    fun getDeviceInfo(): String = runCatching {
        @Suppress("DEPRECATION")
        val defaultDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ctx.display
        } else {
            ctx.windowManager.defaultDisplay
        }
        val st = SystemClock.elapsedRealtime()
        val memInfo = getMemInfoInBytes()
        val screenSize = ctx.screenRealResolution
        val availableSize = ctx.screenAvailableResolution
        val statusBarHeight = ctx.statusBarHeight
        val navBarHeight = ctx.navigationBarHeight
        val configInfo: ConfigurationInfo = ctx.activityManager.deviceConfigurationInfo
        val cpuInfo = "$cpuQualifiedName($cpuCoreCount cores @ " +
            "${cpuMinFreq / 1000}MHz~${"%.2f".format(cpuMaxFreq / 1000_000F)}GHz)"
        val memUsage = "${(memInfo.second - memInfo.first).outputFormatByte()}/${memInfo.second.outputFormatByte()}"
        val screenInfo = "${screenSize.width}x${screenSize.height} ${ctx.screenInch} inches " +
            "RefreshRate=${defaultDisplay?.refreshRate?.toInt()}  " +
            "(${getRatio(screenSize.toSmartSize())}=${ctx.screenRatio.round()})  " +
            "(${ctx.densityDpi}:${ctx.density})  (xdpi=${ctx.xdpi} ydpi=${ctx.ydpi})  " +
            "(${availableSize.width}x${availableSize.height}($statusBarHeight)+$navBarHeight)  " +
            "(${availableSize.height}+$navBarHeight=${availableSize.height + navBarHeight})"
        """
            Device basic information:
            App version      : ${ctx.versionName}(${ctx.versionCode})
            Device locale    : ${LangUtil.getInstance(ctx).getDeviceLanguageCountryCode()}
            Default locale   : ${LangUtil.getInstance(ctx).getDefaultLanguageCountryCode()}
            Network Type     : ${NetworkUtil.getNetworkTypeName(ctx)}(${NetworkUtil.getNetworkGeneration(ctx)})
            Manufacturer     : $manufacturer
            Brand            : $brand
            Board            : $board
            OsVersion        : $osVersion($osVersionSdkInt)
            Serial Number    : ${getSerialNumber()}
            DeviceName       : $deviceName
            Model            : $model
            Product          : $product
            Host             : $host
            Hardware         : $hardware
            CPU              : $cpuInfo
            CPU Arch         : $cpuArch
            OpenGL ES Version: ${configInfo.glEsVersion} [0x${Integer.toHexString(configInfo.reqGlEsVersion)}]
            Supported ABIS   : ${supportedCpuArchs.contentToString()}
            Display          : $display
            Screen           : $screenInfo
            MemoryUsage      : $memUsage
            ${memInfo.third.round()}% Used
            External Storage : $externalStorageBytesInReadable
            Fingerprint      : ${Build.FINGERPRINT}
            Tablet           : ${ctx.isTablet()}
            Emulator         : ${isProbablyAnEmulator()}
            Battery Capacity : $batteryCapacity
            MAC              : ${NetworkUtil.getMacAddress(ctx)}
            IMEI:
                    slot0: ${getImei(ctx, 0) ?: "NA"}
                    slot1: ${getImei(ctx, 1) ?: "NA"}
            Device Features:
                    Full screen device        : ${ctx.isFullScreenDevice}
                    Navigation bar is showing : ${ctx.isNavigationBarShown}

            Cost: ${SystemClock.elapsedRealtime() - st}ms
        """.trimIndent()
    }.getOrElse {
        it.printStackTrace()
        ""
    }
}
