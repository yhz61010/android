@file:Suppress("unused")

package com.leovp.androidbase.utils.network

import com.leovp.log_sdk.LogContext
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Author: Michael Leo
 * Date: 20-6-18 下午2:51
 */
object InternetUtil {
    private const val TAG = "InternetUtil"

    fun getIpsByHost(host: String): List<String> {
        return try {
            val ipAddressArr: ArrayList<String> = ArrayList()
            InetAddress.getAllByName(host.trim())
                ?.forEach { inetAddr -> inetAddr.hostAddress?.let { addr -> ipAddressArr.add(addr) } }
            ipAddressArr
        } catch (e: Exception) {
            LogContext.log.e(TAG, "getIpsByName error host=$host. Exception: $e")
            emptyList()
        }
    }

    fun getIpsByHost(host: String, callback: (List<String>) -> Unit) {
        thread { callback(getIpsByHost(host)) }
    }
}