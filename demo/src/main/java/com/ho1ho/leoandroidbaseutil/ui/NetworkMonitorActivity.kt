package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import com.ho1ho.androidbase.utils.network.NetworkMonitor
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity

/**
 * Author: Michael Leo
 * Date: 20-6-16 下午6:03
 *
 * We need following permissions:
 * ```xml
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 * ```
 */
class NetworkMonitorActivity : BaseDemonstrationActivity() {

    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_monitor)

        networkMonitor = NetworkMonitor(this, "220.181.38.148")
        networkMonitor.startMonitor(3)
    }

    override fun onDestroy() {
        networkMonitor.stopMonitor()
        super.onDestroy()
    }
}