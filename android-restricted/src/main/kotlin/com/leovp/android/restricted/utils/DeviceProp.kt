package com.leovp.android.restricted.utils

import android.annotation.SuppressLint
import android.os.Environment
import com.leovp.android.utils.shell.ShellUtil
import com.leovp.log.LogContext
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * Author: Michael Leo
 * Date: 20-6-1 下午2:50
 *
 * Get system property
 * Example:
 * ```kotlin
 * DeviceProp.getSystemProperty("ro.product.brand");
 * ```
 */
@Suppress("unused")
object DeviceProp {

    private const val TAG = "DeviceProp"

    /**
     * Valid Android system property names: dot-separated tokens of letters, digits, `_` and `-`.
     * Anything else is rejected before reaching the shell to prevent command injection (AR-7).
     */
    private val PROP_NAME_REGEX = Regex("^[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*$")

    fun getSystemProperty(name: String): String {
        var prop = getSystemPropertyByReflect(name)
        if (prop.isBlank()) prop = getSystemPropertyByStream(name)
        if (prop.isBlank()) prop = getSystemPropertyByShell(name)
        return prop
    }

    @SuppressLint("PrivateApi")
    private fun getSystemPropertyByReflect(key: String): String = runCatching {
        Class.forName("android.os.SystemProperties").let {
            it.getMethod("get", String::class.java).invoke(it, key) as String
        }
    }.getOrDefault("")

    private fun getSystemPropertyByStream(key: String): String {
        return runCatching {
            val prop = Properties()
            val fis = FileInputStream(File(Environment.getRootDirectory(), "build.prop"))
            prop.load(fis)
            return prop.getProperty(key, "")
        }.getOrDefault("")
    }

    private fun getSystemPropertyByShell(propName: String): String {
        if (!PROP_NAME_REGEX.matches(propName)) {
            LogContext.log.w(TAG, "Rejected illegal system property name")
            return ""
        }
        return ShellUtil.execCmd("getprop $propName", false).successMsg
    }
}
