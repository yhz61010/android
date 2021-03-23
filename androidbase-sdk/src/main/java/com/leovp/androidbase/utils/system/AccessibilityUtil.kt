package com.leovp.androidbase.utils.system

import android.provider.Settings
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.utils.log.LogContext


/**
 * Author: Michael Leo
 * Date: 21-3-23 上午10:11
 */
object AccessibilityUtil {
    /**
     * Usage:
     * ```
     * if (!AccessibilityUtil.isAccessibilityEnabled(SimulatedClickService::class.java)) {
     *     startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
     * }
     * ```
     */
    fun isAccessibilityEnabled(accessibilityServiceCanonicalName: Class<*>): Boolean {
        var accessibilityEnabled = 0
        runCatching {
            accessibilityEnabled = Settings.Secure.getInt(app.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        }.onFailure { it.printStackTrace() }
        val packageName: String = app.packageName
        val serviceCanonicalName = "$packageName/${accessibilityServiceCanonicalName.canonicalName}"
        LogContext.log.d("serviceCanonicalName: $serviceCanonicalName")
        if (accessibilityEnabled == 1) {
            val settingValue: String? = Settings.Secure.getString(app.applicationContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            settingValue?.split(':', ignoreCase = true)?.forEach { value ->
                if (value.equals(serviceCanonicalName, ignoreCase = true)) return true
            }
        }
        return false
    }
}