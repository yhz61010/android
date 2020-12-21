package com.leovp.androidbase.utils.device

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.leovp.androidbase.exts.android.*
import com.leovp.androidbase.exts.kotlin.outputFormatByte
import com.leovp.androidbase.exts.kotlin.round
import com.leovp.androidbase.utils.shell.ShellUtil
import java.io.File


/**
 * Author: Michael Leo
 * Date: 20-5-15 下午3:23
 */

@Suppress("WeakerAccess", "unused", "UNUSED_PARAMETER")
object DeviceUtil {
    private const val TAG = "DeviceUtil"

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
     * [0]: Available memory in bytes
     * [1]: Total memory in bytes
     * [2]: Used memory in bytes
     */
    fun getMemInfoInBytes(): Triple<Long, Long, Float> {
        val mi = ActivityManager.MemoryInfo()
        app.activityManager.getMemoryInfo(mi)
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
        val externalStorageDirs = ContextCompat.getExternalFilesDirs(app, null)
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
                sb.append("[$index]=${used.outputFormatByte()}/${pair.second.outputFormatByte()}  ${usedPercent.round()}% Used")
                sb.append("\n")
            }
            return sb.deleteAt(sb.length - 1).toString()
        }

    /**
     * As of API 30(Android 11), you must use Activity context to retrieve screen real size
     */
    fun getDeviceInfo(act: Activity): String {
        return runCatching {
            val st = SystemClock.elapsedRealtimeNanos()
            val memInfo = getMemInfoInBytes()
            val screenSize = act.getRealResolution()
            val availableSize = app.getAvailableResolution()
            val statusBarHeight = app.statusBarHeight
            val navBarHeight = app.navigationBarHeight
            """
            Device basic information:
            App version     : ${app.versionName}(${app.versionCode})
            Manufacturer    : $manufacturer
            Brand           : $brand
            Board           : $board
            OsVersion       : $osVersion($osVersionSdkInt)
            DeviceName      : $deviceName
            Model           : $model
            Product         : $product
            Host            : $host
            CPU             : $cpuQualifiedName($cpuCoreCount cores @ ${cpuMinFreq / 1000}MHz~${"%.2f".format(cpuMaxFreq / 1000_000F)}GHz)
            Supported ABIS  : ${supportedCpuArchs.contentToString()}
            Display         : $display
            Screen          : ${screenSize.x}x${screenSize.y}(${app.densityDpi}:${app.density})(${act.screenRatio.round()})  (${availableSize.x}x${availableSize.y})  (${availableSize.y}+$statusBarHeight+$navBarHeight=${availableSize.y + statusBarHeight + navBarHeight})
            MemoryUsage     : ${(memInfo.second - memInfo.first).outputFormatByte()}/${memInfo.second.outputFormatByte()}  ${memInfo.third.round()}% Used
            External Storage: $externalStorageBytesInReadable
            Fingerprint     : ${Build.FINGERPRINT}
            Emulator        : ${isProbablyAnEmulator()}
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
        }.getOrElse {
            it.printStackTrace()
            ""
        }
    }
}