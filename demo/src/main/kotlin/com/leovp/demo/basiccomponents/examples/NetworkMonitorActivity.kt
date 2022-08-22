package com.leovp.demo.basiccomponents.examples

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresPermission
import com.leovp.android.exts.toast
import com.leovp.androidbase.utils.network.InternetUtil
import com.leovp.androidbase.utils.network.NetworkMonitor
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityNetworkMonitorBinding
import com.leovp.android.utils.NetworkUtil
import com.leovp.kotlin.exts.humanReadableByteCount
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

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
class NetworkMonitorActivity : BaseDemonstrationActivity<ActivityNetworkMonitorBinding>() {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityNetworkMonitorBinding {
        return ActivityNetworkMonitorBinding.inflate(layoutInflater)
    }

    private var networkMonitor: NetworkMonitor? = null

    @RequiresPermission(allOf = [Manifest.permission.CHANGE_NETWORK_STATE, Manifest.permission.ACCESS_NETWORK_STATE])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        InternetUtil.getIpsByHost("leovp.com") { ipList ->
            LogContext.log.i(ITAG, "ipList=${ipList.toJsonString()}")
            if (ipList.isEmpty()) {
                toast("Can't get IP from host.")
            } else {
                networkMonitor = NetworkMonitor(this.application, ipList[0]) { info ->
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
                    val infoStr = String.format(
                        "↓%s\t↑%s\t%s\t%dMbps\tR:%d %d %d%s",
                        downloadSpeedStr,
                        uploadSpeedStr,
                        if (latencyStatus.isNullOrBlank()) "${info.ping}ms" else "${info.ping}ms($latencyStatus)",
                        info.linkSpeed,
                        info.rssi,
                        info.wifiScoreIn5,
                        info.wifiScore,
                        if (wifiSignalStatus.isNullOrBlank()) "" else " ($wifiSignalStatus)"
                    )
                    LogContext.log.i(ITAG, infoStr)
                    runOnUiThread { binding.txtNetworkStatus.text = infoStr }
                    binding.scrollView2.post { binding.scrollView2.fullScroll(View.FOCUS_DOWN) }
                }
                networkMonitor?.startMonitor(2)
            }
        }
    }

    override fun onDestroy() {
        // DO NOT forget to stop monitor.
        networkMonitor?.stopMonitor()
        super.onDestroy()
    }
}
