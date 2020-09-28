package com.leovp.androidbase.utils.network

import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.iters.EventCallBack
import com.leovp.androidbase.utils.LLog
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Author: Michael Leo
 * Date: 20-6-18 下午2:51
 */
object InternetUtil {
    fun getIpsByName(host: String?): List<String> {
        return try {
            val ipAddressArr: ArrayList<String> = ArrayList()
            InetAddress.getAllByName(host?.trim())?.forEach { inetAddr -> ipAddressArr.add(inetAddr.hostAddress) }
            ipAddressArr
        } catch (e: Exception) {
            LLog.e(ITAG, "getIpsByName error=${e.message}")
            emptyList()
        }
    }

    @Suppress("unused")
    fun getIpsByName(host: String?, callback: EventCallBack) {
        thread { callback.onCallback(getIpsByName(host)) }
    }
}