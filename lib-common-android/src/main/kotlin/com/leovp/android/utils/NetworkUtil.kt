@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.android.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import com.leovp.android.exts.connectivityManager
import com.leovp.android.exts.telephonyManager
import com.leovp.android.exts.wifiManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.util.*

/**
 * Author: Michael Leo
 * Date: 20-6-12 上午11:37
 *
 */
object NetworkUtil {
    private const val TAG = "LEO-NetworkUtil"

    const val TYPE_WIFI = "WIFI"
    const val TYPE_CELLULAR = "Cellular"
    const val TYPE_ETHERNET = "Ethernet"
    const val TYPE_VPN = "VPN"
    const val TYPE_BLUETOOTH = "Bluetooth"
    const val TYPE_OTHER = "Other"
    const val TYPE_OFFLINE = "Offline"

    const val NETWORK_PING_DELAY_NORMAL = 80
    const val NETWORK_PING_DELAY_HIGH = 130
    const val NETWORK_PING_DELAY_VERY_HIGH = 200

    const val NETWORK_SIGNAL_STRENGTH_BAD = 2
    const val NETWORK_SIGNAL_STRENGTH_VERY_BAD = 1

    private const val MIN_RSSI = -100
    private const val MAX_RSSI = -55

    data class ProxyInfo(val type: Proxy.Type, val url: String, val port: Int)

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isOnline(ctx: Context): Boolean = isWifiActive(ctx) || isCellularActive(ctx) || isEthernetActive(ctx) ||
            isVpnActive(ctx) || isBluetoothActive(ctx)

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isOffline(ctx: Context): Boolean = !isOnline(ctx)

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isWifiActive(ctx: Context): Boolean = isTypeActive(
        ctx,
        NetworkCapabilities.TRANSPORT_WIFI,
        @Suppress("DEPRECATION") ConnectivityManager.TYPE_WIFI
    )

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isEthernetActive(ctx: Context): Boolean = isTypeActive(
        ctx,
        NetworkCapabilities.TRANSPORT_ETHERNET,
        @Suppress("DEPRECATION") ConnectivityManager.TYPE_ETHERNET
    )

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isCellularActive(ctx: Context): Boolean = isTypeActive(
        ctx,
        NetworkCapabilities.TRANSPORT_CELLULAR,
        @Suppress("DEPRECATION") ConnectivityManager.TYPE_MOBILE
    )

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isVpnActive(ctx: Context): Boolean = isTypeActive(
        ctx,
        NetworkCapabilities.TRANSPORT_VPN,
        @Suppress("DEPRECATION") ConnectivityManager.TYPE_VPN
    )

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isBluetoothActive(ctx: Context): Boolean = isTypeActive(
        ctx,
        NetworkCapabilities.TRANSPORT_BLUETOOTH,
        @Suppress("DEPRECATION") ConnectivityManager.TYPE_BLUETOOTH
    )

    /**
     * Need following permission:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun isTypeActive(ctx: Context, transportType: Int, connType: Int): Boolean {
        val cm = ctx.connectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = cm.activeNetwork ?: return false
            val nc = cm.getNetworkCapabilities(nw) ?: return false
            nc.hasTransport(transportType)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == connType
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
        var inputLine: String? = null
        runCatching {
            val pingCommand =
                String.format(
                    Locale.getDefault(),
                    "/system/bin/ping -c %d %s",
                    numberOfPackages,
                    ipAddress
                )
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
        }.onFailure {
            return (-1).toDouble()
        }

        // Extracting the average round trip time from the inputLine string
        return runCatching {
            val afterEqual = inputLine!!.substring(inputLine!!.indexOf("=")).trim { it <= ' ' }
            val afterFirstSlash =
                afterEqual.substring(afterEqual.indexOf('/') + 1).trim { it <= ' ' }
            val strAvgRtt = afterFirstSlash.substring(0, afterFirstSlash.indexOf('/'))
            strAvgRtt.toDouble()
        }.getOrDefault((-1).toDouble())
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
                // Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> Unit
                else -> {
                    @Suppress("DEPRECATION")
                    getNetworkGeneration(ctx.connectivityManager.activeNetworkInfo?.subtype)
                }
            }
        } else {
            null
        }
    }

    data class WifiSignal(val linkSpeed: Int, val rssi: Int, val scoreIn5: Int, val score: Int)

    /**
     * **Need following permission:**
     * ```xml
     * <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(allOf = [Manifest.permission.CHANGE_NETWORK_STATE, Manifest.permission.ACCESS_NETWORK_STATE])
    fun getWifiNetworkStatsAboveAndroidS(ctx: Context, callback: (WifiSignal) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val connectivityManager = ctx.connectivityManager
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val wifiInfo: WifiInfo =
                        networkCapabilities.transportInfo as? WifiInfo ?: return
                    if (isWifiActive(ctx)) {
                        callback(generateWifiSignal(wifiInfo))
                    }
                }

                override fun onLost(network: Network) {
                    callback(WifiSignal(-1, -1, -1, -1))
                }
            }
            // For request
            connectivityManager.requestNetwork(request, networkCallback)
            // For listen
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    /**
     * **Need following permission:**
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getWifiNetworkStatsBelowAndroidS(ctx: Context): WifiSignal {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (isWifiActive(ctx)) {
                @Suppress("DEPRECATION")
                return generateWifiSignal(ctx.wifiManager.connectionInfo)
            }
        }
        return WifiSignal(-1, -1, -1, -1)
    }

    private fun generateWifiSignal(wifiInfo: WifiInfo): WifiSignal {
        val linkSpeed: Int = wifiInfo.linkSpeed
        /* Rssi
         * 0 —— (-55)dBm        满格(4格)信号
         * (-55) —— (-70)dBm    3格信号
         * (-70) —— (-85)dBm    2格信号
         * (-85) —— (-100)dBm   1格信号
         */
        val rssi: Int = wifiInfo.rssi
        val wifiScoreIn5: Int = calculateSignalLevel(rssi, 5)
        val wifiScore: Int = calculateSignalLevel(rssi, 100)
        return WifiSignal(linkSpeed, rssi, wifiScoreIn5, wifiScore)
    }

    @WorkerThread
    fun isHostReachable(hostname: String?, port: Int, timeoutInMillis: Int, proxyInfo: ProxyInfo? = null): Boolean {
        var connected = false
        runCatching {
            val proxy = if (proxyInfo == null) {
                Proxy.NO_PROXY
            } else {
                val proxyAddr: SocketAddress = InetSocketAddress(proxyInfo.url, proxyInfo.port)
                Proxy(proxyInfo.type, proxyAddr)
            }
            Socket(proxy).use { socket ->
                val socketAddress: SocketAddress = InetSocketAddress(hostname, port)
                socket.connect(socketAddress, timeoutInMillis)
                if (socket.isConnected) {
                    connected = true
                }
            }
        }.onFailure {
            connected = false
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
    @Suppress("DEPRECATION")
    private fun getMacAddressBeforeAndroidM(ctx: Context): String {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            val wifiInf = ctx.wifiManager.connectionInfo
            // DEFAULT_MAC_ADDRESS = "02:00:00:00:00:00"
            // Please check WifiInfo#DEFAULT_MAC_ADDRESS
            if ("02:00:00:00:00:00".equals(wifiInf.macAddress, ignoreCase = true)) "" else wifiInf.macAddress
        } else {
            ""
        }
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
                    builder.append(String.format(Locale.ENGLISH, "%02X:", b))
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
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            // TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM -> "2G"

            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"

            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"

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
