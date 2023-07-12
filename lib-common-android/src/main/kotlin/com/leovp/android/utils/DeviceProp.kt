package com.leovp.android.utils

import android.annotation.SuppressLint
import android.os.Environment
import com.leovp.android.utils.shell.ShellUtil
import java.io.File
import java.io.FileInputStream
import java.util.*

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

    fun getSystemProperty(name: String): String {
        var prop = getSystemPropertyByReflect(name)
        if (prop.isBlank()) prop = getSystemPropertyByStream(name)
        if (prop.isBlank()) prop = getSystemPropertyByShell(name)
        return prop
    }

    @SuppressLint("PrivateApi")
    private fun getSystemPropertyByReflect(key: String): String {
        return runCatching {
            Class.forName("android.os.SystemProperties").let {
                it.getMethod("get", String::class.java).invoke(it, key) as String
            }
        }.getOrDefault("")
    }

    private fun getSystemPropertyByStream(key: String): String {
        return runCatching {
            val prop = Properties()
            val fis = FileInputStream(File(Environment.getRootDirectory(), "build.prop"))
            prop.load(fis)
            return prop.getProperty(key, "")
        }.getOrDefault("")
    }

    private fun getSystemPropertyByShell(propName: String) = ShellUtil.execCmd("getprop $propName", false).successMsg
}
