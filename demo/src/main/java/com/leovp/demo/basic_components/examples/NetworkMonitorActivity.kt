package com.leovp.demo.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.network.InternetUtil
import com.leovp.androidbase.utils.network.NetworkMonitor
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityNetworkMonitorBinding
import com.leovp.lib_common_android.utils.NetworkUtil
import com.leovp.lib_common_kotlin.exts.humanReadableByteCount
import com.leovp.lib_json.toJsonString
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

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

    override fun getTagName(): String = ITAG

    private lateinit var binding: ActivityNetworkMonitorBinding

    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkMonitorBinding.inflate(layoutInflater).apply { setContentView(root) }

        InternetUtil.getIpsByHost("leovp.com") { ipList ->
            LogContext.log.i(ITAG, "ipList=${ipList.toJsonString()}")
            if (ipList.isEmpty()) {
                toast("Can't get IP from host.")
            } else {
                networkMonitor = NetworkMonitor(this.application, ipList[0]) { info ->
                    val downloadSpeedStr = info.downloadSpeed.humanReadableByteCount()
                    val uploadSpeedStr = info.uploadSpeed.humanReadableByteCount()

                    val latencyStatus = when (info.showPingTips) {
                        NetworkUtil.NETWORK_PING_DELAY_HIGH      -> "Latency High"
                        NetworkUtil.NETWORK_PING_DELAY_VERY_HIGH -> "Latency Very High"
                        else                                     -> null
                    }

                    val wifiSignalStatus = when (info.showWifiSig) {
                        NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD      -> "Signal Bad"
                        NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD -> "Signal Very Bad"
                        else                                         -> null
                    }
                    val infoStr = String.format("↓%s\t↑%s\t%sms\t%dMbps\tR:%d %d %d%s",
                        downloadSpeedStr,
                        uploadSpeedStr,
                        if (latencyStatus.isNullOrBlank()) "${info.ping}" else "${info.ping}($latencyStatus)",
                        info.linkSpeed,
                        info.rssi,
                        info.wifiScoreIn5,
                        info.wifiScore,
                        if (wifiSignalStatus.isNullOrBlank()) "" else " ($wifiSignalStatus)")
                    LogContext.log.i(ITAG, infoStr)
                    runOnUiThread { binding.txtNetworkStatus.text = infoStr }
                    binding.scrollView2.post { binding.scrollView2.fullScroll(View.FOCUS_DOWN) }
                }
                networkMonitor.startMonitor(2)
            }
        }
    }

    override fun onDestroy() {
        // DO NOT forget to stop monitor.
        networkMonitor.stopMonitor()
        super.onDestroy()
    }
}