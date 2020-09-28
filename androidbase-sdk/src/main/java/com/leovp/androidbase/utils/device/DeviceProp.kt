package com.leovp.androidbase.utils.device

import android.annotation.SuppressLint

/**
 * Author: Michael Leo
 * Date: 20-6-1 下午2:50
 */
@Suppress("unused")
object DeviceProp {
    /**
     * Get system property
     * Example:
     * ```kotlin
     * CommandUtils.getSystemProperty("ro.product.brand");
     * ```
     */
    @SuppressLint("PrivateApi")
    fun getAndroidProperty(key: String): String? {
        return try {
            Class.forName("android.os.SystemProperties").getMethod(
                "get",
                String::class.java
            ).invoke(null, key) as String?
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}