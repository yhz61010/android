@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.lib_common_android.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import com.leovp.lib_common_android.exts.connectivityManager
import com.leovp.lib_common_android.exts.telephonyManager
import com.leovp.lib_common_android.exts.wifiManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketAddress
import java.util.*


/**
 * Author: Michael Leo
 * Date: 20-6-12 上午11:37
 *
 */
object NetworkUtil {
    private const val TAG = "NetworkUtil"

    const val TYPE_WIFI = "WIFI"
    const val TYPE_CELLULAR = "Cellular"
    const val TYPE_ETHERNET = "Ethernet"
    const val TYPE_VPN = "VPN"

    const val NETWORK_PING_DELAY_NORMAL = 80
    const val NETWORK_PING_DELAY_HIGH = 130
    const val NETWORK_PING_DELAY_VERY_HIGH = 200

    const val NETWORK_SIGNAL_STRENGTH_BAD = 2
    const val NETWORK_SIGNAL_STRENGTH_VERY_BAD = 1

    private const val MIN_RSSI = -100
    private const val MAX_RSSI = -55

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isOnline(ctx: Context): Boolean = isWifiActive(ctx) || isCellularActive(ctx) || isEthernetActive(ctx) || isVpnActive(ctx)

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isOffline(ctx: Context): Boolean = !isOnline(ctx)

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isWifiActive(ctx: Context): Boolean {
        val cm = ctx.connectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = cm.activeNetwork ?: return false
            val nc = cm.getNetworkCapabilities(nw) ?: return false
            nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isEthernetActive(ctx: Context): Boolean {
        val cm = ctx.connectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = cm.activeNetwork ?: return false
            val nc = cm.getNetworkCapabilities(nw) ?: return false
            nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_ETHERNET
        }
    }

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isCellularActive(ctx: Context): Boolean {
        val cm = ctx.connectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = cm.activeNetwork ?: return false
            val nc = cm.getNetworkCapabilities(nw) ?: return false
            nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
        }
    }

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isVpnActive(ctx: Context): Boolean {
        val cm = ctx.connectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = cm.activeNetwork ?: return false
            val nc = cm.getNetworkCapabilities(nw) ?: return false
            nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_VPN
        }
    }

    /**
     * Returns the latency to a given server in milliseconds by issuing a ping command.
     * System will issue NUMBER_OF_PACKTETS ICMP Echo Request packet each having size of 56 bytes
     * every second, and returns the avg latency of them.
     *
     * @return Return the latency which getting from ping command. Return -2 which indicates network is offline.
     * Return -1 indicates get ping error.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getLatency(ctx: Context, ipAddress: String, numberOfPackages: Int): Double {
        if (isOffline(ctx)) {
            return (-2).toDouble()
        }
        var inputLine: String?
        try {
            val pingCommand = String.format(Locale.getDefault(), "/system/bin/ping -c %d %s", numberOfPackages, ipAddress)
            // Execute the command on the environment interface
            val process = Runtime.getRuntime().exec(pingCommand)
            // Gets the input stream to get the output of the executed command
            BufferedReader(InputStreamReader(process.inputStream)).use {
                inputLine = it.readLine()
                while (inputLine != null) {
                    if (inputLine!!.isNotEmpty() && inputLine!!.contains("avg")) {
                        // when we get to the last line of executed ping command
                        break
                    }
                    inputLine = it.readLine()
                }
            }
        } catch (e: IOException) {
            return (-1).toDouble()
        }

        // Extracting the average round trip time from the inputLine string
        return try {
            val afterEqual = inputLine!!.substring(inputLine!!.indexOf("=")).trim { it <= ' ' }
            val afterFirstSlash = afterEqual.substring(afterEqual.indexOf('/') + 1).trim { it <= ' ' }
            val strAvgRtt = afterFirstSlash.substring(0, afterFirstSlash.indexOf('/'))
            strAvgRtt.toDouble()
        } catch (e: Exception) {
            (-1).toDouble()
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getNetworkTypeName(ctx: Context): String? {
        if (isWifiActive(ctx)) return TYPE_WIFI
        if (isCellularActive(ctx)) return TYPE_CELLULAR
        if (isEthernetActive(ctx)) return TYPE_ETHERNET
        if (isVpnActive(ctx)) return TYPE_VPN
        return null
    }

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * <uses-permission android:name="android.permission.READ_PHONE_STATE" />
     * ```
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.READ_PHONE_STATE])
    fun getNetworkGeneration(ctx: Context): String? {
        return if (TYPE_CELLULAR == getNetworkTypeName(ctx)) {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> getNetworkGeneration(ctx.telephonyManager.dataNetworkType)
//                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> TODO("Adopt me")
                else -> {
                    @Suppress("DEPRECATION")
                    (getNetworkGeneration(ctx.connectivityManager.activeNetworkInfo?.subtype))
                }
            }
        } else {
            null
        }
    }

    /**
     * **Need following permission:**
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getWifiNetworkStats(ctx: Context): IntArray? {
        val linkSpeed: Int
        val wifiScore: Int
        val wifiScoreIn5: Int
        val rssi: Int
        if (isWifiActive(ctx)) {
            val wi = ctx.wifiManager.connectionInfo
            linkSpeed = wi.linkSpeed
            /* Rssi
             * 0 —— (-55)dBm        满格(4格)信号
             * (-55) —— (-70)dBm    3格信号
             * (-70) —— (-85)dBm    2格信号
             * (-85) —— (-100)dBm   1格信号
             */
            rssi = wi.rssi
            wifiScore = calculateSignalLevel(rssi, 100)
            wifiScoreIn5 = calculateSignalLevel(rssi, 5)
        } else {
            return null
        }
        return intArrayOf(linkSpeed, rssi, wifiScoreIn5, wifiScore)
    }

    fun isHostReachable(hostname: String?, port: Int, timeoutInMillis: Int): Boolean {
        var connected = false
        runCatching {
            val socket = Socket()
            val socketAddress: SocketAddress = InetSocketAddress(hostname, port)
            socket.connect(socketAddress, timeoutInMillis)
            if (socket.isConnected) {
                connected = true
                socket.close()
            }
        }.onFailure {
            it.printStackTrace()
        }
        return connected
    }

    /**
     * Need following permission
     * ```xml
     * <uses-permission name="android.permission.ACCESS_WIFI_STATE" />
     * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
     * <uses-permission android:name="android.permission.LOCAL_MAC_ADDRESS" />
     * ```
     */
    @SuppressLint("HardwareIds", "MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION])
    private fun getMacAddressBeforeAndroidM(ctx: Context): String {
        val wifiInf = ctx.wifiManager.connectionInfo
        // DEFAULT_MAC_ADDRESS = "02:00:00:00:00:00"
        // Please check WifiInfo#DEFAULT_MAC_ADDRESS
        return if ("02:00:00:00:00:00".equals(wifiInf.macAddress, ignoreCase = true)) "" else wifiInf.macAddress
    }

    /**
     * Need following permission
     * ```xml
     * <uses-permission name="android.permission.ACCESS_WIFI_STATE" />
     * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
     * ```
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION])
    fun getMacAddress(ctx: Context): String {
        return runCatching {
            var address = getMacAddressBeforeAndroidM(ctx)
            if (address.isNotBlank()) {
                return address
            }
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                val macAddr = network.hardwareAddress
                if (macAddr == null || macAddr.isEmpty()) {
                    continue
                }
                val builder = StringBuilder()
                for (b in macAddr) {
                    builder.append(String.format("%02X:", b))
                }
                if (builder.isNotEmpty()) {
                    builder.deleteCharAt(builder.length - 1)
                }
                val mac = builder.toString()
                if (network.name == "wlan0") {
                    address = mac
                }
            }
            address
        }.getOrDefault("")
    }

    /**
     * Need following permissions:
     * <uses-permission android:name="android.permission.INTERNET" />
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     */
    fun getIp(): ArrayList<String> {
        val ifconfig = ArrayList<String>()
        runCatching {
            for (ni: NetworkInterface in NetworkInterface.getNetworkInterfaces()) {
                for (addr in ni.inetAddresses) {
                    if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress && addr.isSiteLocalAddress) {
                        addr.hostAddress?.let { address -> ifconfig.add(address) }
                    }
                }
            }
        }
        return ifconfig
    }

    private fun getNetworkGeneration(networkType: Int?): String? {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM -> "2G"

            TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> null
        }
    }

    private fun calculateSignalLevel(rssi: Int, numLevels: Int): Int {
        return when {
            rssi <= MIN_RSSI -> 0
            rssi >= MAX_RSSI -> numLevels - 1
            else -> {
                val inputRange = (MAX_RSSI - MIN_RSSI).toFloat()
                val outputRange = (numLevels - 1).toFloat()
                ((rssi - MIN_RSSI).toFloat() * outputRange / inputRange).toInt()
            }
        }
    }
}