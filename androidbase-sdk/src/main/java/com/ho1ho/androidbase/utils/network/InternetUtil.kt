package com.ho1ho.androidbase.utils.network

import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import java.net.InetAddress

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
}