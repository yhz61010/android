package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.exts.kotlin.humanReadableByteCount
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.network.NetworkMonitor
import com.leovp.androidbase.utils.network.NetworkUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
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

        networkMonitor = NetworkMonitor(this.application, "220.181.38.148") { info ->
            val downloadSpeedStr = info.downloadSpeed.humanReadableByteCount()
            val uploadSpeedStr = info.uploadSpeed.humanReadableByteCount()

            val latencyStatus = when (info.showPingTips) {
                NetworkUtil.NETWORK_PING_DELAY_HIGH -> "Latency High"
                NetworkUtil.NETWORK_PING_DELAY_VERY_HIGH -> "Latency Very High"
                else -> null
            }

            val wifiSignalStatus = when (info.showWifiSig) {
                NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD -> "Signal Bad"
                NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD -> "Signal Very Bad"
                else -> null
            }
            val infoStr =
                "S:$downloadSpeedStr/$uploadSpeedStr\t\tP:${info.ping}${if (latencyStatus.isNullOrBlank()) "" else "($latencyStatus)"}\t\tL:${info.linkSpeed}Mbps\tR:${info.rssi} ${info.wifiScoreIn5} ${info.wifiScore} ${if (wifiSignalStatus.isNullOrBlank()) "" else "($wifiSignalStatus)"}"
            LogContext.log.i(ITAG, infoStr)
            runOnUiThread { txtNetworkStatus.text = infoStr }
            scrollView2.post { scrollView2.fullScroll(View.FOCUS_DOWN) }
        }
        networkMonitor.startMonitor(2)
    }

    override fun onDestroy() {
        // DO NOT forget to stop monitor.
        networkMonitor.stopMonitor()
        super.onDestroy()
    }
}