package com.leovp.androidbase.utils.device

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiNetworkSpecifier
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.connectivityManager
import com.leovp.androidbase.exts.android.wifiManager


/**
 * Author: Michael Leo
 * Date: 21-3-6 下午6:24
 */
object WifiUtil {
    /**
     *
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
     * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
     * ```
     *
     * @param enc Only available below API 29(API < 29)(Android Q/Android 10)
     */
    @SuppressLint("MissingPermission")
    fun connectWifi(wifiSsid: String, wifiPwd: String, enc: WifiEncType? = WifiEncType.WEP) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
                .setSsid(wifiSsid)
                .setWpa2Passphrase(wifiPwd)
                .build()
            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build()
            app.connectivityManager.requestNetwork(networkRequest, ConnectivityManager.NetworkCallback())
        } else {
            // 1. Hotspot and password need to be in quotes!!!
            val ssid = "\"" + wifiSsid + "\""
            val psd = "\"" + wifiPwd + "\""

            // 2. Configure WIFI
            // Deprecated in API 29(Android Q/Android 10)
            val conf = WifiConfiguration()
            conf.SSID = ssid
            when (enc) {
                WifiEncType.WEP -> {
                    conf.wepKeys[0] = psd
                    conf.wepTxKeyIndex = 0
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                }
                WifiEncType.WPA ->
                    conf.preSharedKey = psd
                WifiEncType.OPEN ->
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
            // 3. Connect to WIFI
            val wifiManager = app.wifiManager
            wifiManager.addNetwork(conf)
            val configuredNetworks = app.wifiManager.configuredNetworks
            for (network in configuredNetworks) {
                if (network.SSID != null && network.SSID == ssid) {
                    wifiManager.disconnect()
                    wifiManager.enableNetwork(network.networkId, true)
                    wifiManager.reconnect()
                    break
                }
            }
        }
    }
}

enum class WifiEncType {
    WEP, WPA, OPEN
}