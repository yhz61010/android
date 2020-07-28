package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import android.view.View
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.humanReadableByteCount
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.network.NetworkMonitor
import com.ho1ho.androidbase.utils.network.NetworkUtil
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
                LLog.i(ITAG, "S:${downloadSpeed.humanReadableByteCount()}/${uploadSpeed.humanReadableByteCount()}")
                txtNetworkStatus.text =
                    "${txtNetworkStatus.text}\nDownload: ${downloadSpeed.humanReadableByteCount()}    Upload: ${uploadSpeed.humanReadableByteCount()}\n"
                scrollView2.post {
                    scrollView2.fullScroll(View.FOCUS_DOWN)
                }
            }

            override fun onPingWifiSignalChanged(
                ping: Int,
                showPingLatencyToast: Int,
                linkSpeed: Int,
                rssi: Int,
                wifiScoreIn5: Int,
                wifiScore: Int,
                showWifiSignalStatus: Int
            ) {
                val latencyStatus =
                    when (showPingLatencyToast) {
                        NetworkUtil.NETWORK_PING_DELAY_HIGH -> "Latency High"
                        NetworkUtil.NETWORK_PING_DELAY_VERY_HIGH -> "Latency Very High"
                        else -> null
                    }

                val wifiSignalStatus =
                    when (showWifiSignalStatus) {
                        NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD -> "Signal Bad"
                        NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD -> "Signal Very Bad"
                        else -> null
                    }
                LLog.i(
                    ITAG,
                    "P:$ping${if (latencyStatus.isNullOrBlank()) "" else "($latencyStatus)"}\tL:${linkSpeed}Mbps\tR:${rssi} $wifiScoreIn5 $wifiScore/100 ${if (wifiSignalStatus.isNullOrBlank()) "" else "($wifiSignalStatus)"}"
                )
                txtNetworkStatus.text =
                    "${txtNetworkStatus.text}\nP:$ping${if (latencyStatus.isNullOrBlank()) "" else "($latencyStatus)"}   L:${linkSpeed}Mbps   R:${rssi}   $wifiScoreIn5   $wifiScore/100   ${if (wifiSignalStatus.isNullOrBlank()) "" else "($wifiSignalStatus)"}"
                scrollView2.post {
                    scrollView2.fullScroll(View.FOCUS_DOWN)
                }
            }

        }
        networkMonitor.startMonitor(3)
    }

    override fun onDestroy() {
        // DO NOT forget to stop monitor.
        networkMonitor.stopMonitor()
        super.onDestroy()
    }
}