@file:Suppress("unused")

package com.leovp.android.restricted.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.leovp.kotlin.utils.SingletonHolder

/**
 * Author: Michael Leo
 * Date: 2026/7/16 14:50
 */

@Suppress("WeakerAccess", "unused", "UNUSED_PARAMETER")
class DeviceUtilRestricted private constructor(context: Context) {
    private val ctx = context.applicationContext ?: context

    companion object : SingletonHolder<DeviceUtilRestricted, Context>(::DeviceUtilRestricted) {
        private const val TAG = "DeviceUtilRestricted"
    }

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
}

@SuppressLint("HardwareIds", "MissingPermission")
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
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { Build.getSerial() }.getOrDefault("NA")
    } else {
        ""
    }
}
