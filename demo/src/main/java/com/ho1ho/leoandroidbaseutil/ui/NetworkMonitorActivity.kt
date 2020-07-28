package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import android.view.View
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.network.NetworkMonitor
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import kotlinx.android.synthetic.main.activity_network_monitor.*

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

        networkMonitor = NetworkMonitor(this.application, "220.181.38.148")
        networkMonitor.monitorCallback = object : NetworkMonitor.Callback {
            override fun onSpeedChanged(downloadSpeed: Long, uploadSpeed: Long) {
                LLog.i(ITAG, "Download: $downloadSpeed Upload: $uploadSpeed")
                txtNetworkStatus.text = "${txtNetworkStatus.text}\nDownload: $downloadSpeed Upload: $uploadSpeed\n"
                scrollView2.post {
                    scrollView2.fullScroll(View.FOCUS_DOWN)
                }
            }

            override fun onPingWifiSignalChanged(ping: Int, linkSpeed: Int, rssi: Int, wifiScoreIn5: Int, wifiScore: Int) {
                LLog.i(ITAG, "Ping: $ping LinkSpeed: $linkSpeed wifiScoreIn100=$wifiScore wifiScoreIn5=$wifiScoreIn5 rssi=$rssi")
                txtNetworkStatus.text =
                    "${txtNetworkStatus.text}\nPing: $ping LinkSpeed: $linkSpeed wifiScoreIn100=$wifiScore wifiScoreIn5=$wifiScoreIn5 rssi=$rssi\n"
                scrollView2.post {
                    scrollView2.fullScroll(View.FOCUS_DOWN)
                }
            }

        }
        networkMonitor.startMonitor(3)
    }

    override fun onDestroy() {
        networkMonitor.stopMonitor()
        super.onDestroy()
    }
}