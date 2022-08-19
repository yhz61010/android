package com.leovp.androidbase.utils.network

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.Keep
import androidx.annotation.RequiresPermission
import com.leovp.lib_common_android.utils.NetworkUtil
import com.leovp.log_sdk.LogContext
import java.util.concurrent.TimeUnit

/**
 * Author: Michael Leo
 * Date: 20-6-12 下午4:19
 *
 * Monitor the speed, ping and wifi signal.
 *
 * Usage:
 * ```kotlin
 * val networkMonitor = NetworkMonitor(ctx, ip) { networkMonitorResult -> }
 * networkMonitor.startMonitor()
 * networkMonitor.stopMonitor()
 * ```
 */
class NetworkMonitor(
    private val ctx: Context,
    private val ip: String,
    callback: (NetworkMonitorResult) -> Unit
) {
    companion object {
        private const val TAG = "NM"
    }

    private var freq = 1

    private var globalWifiSignal: NetworkUtil.WifiSignal? = null
    private var trafficStat: TrafficStatHelper? = null
    private val monitorThread = HandlerThread("monitor-thread").apply { start() }
    private val monitorHandler = Handler(monitorThread.looper)

    private val pingRunnable: Runnable = object : Runnable {
        private var pingCountdown: Long = 0
        private var strengthCountdown: Long = 0
        private val MAX_COUNT: Long = 10
        private var showPingLatencyStatus: Int = -1
        private var showWifiSignalStatus: Int = -1

        @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        override fun run() {
            try {
                showPingLatencyStatus = -1
                showWifiSignalStatus = -1

                if (--pingCountdown < 0) {
                    pingCountdown = 0
                }
                if (--strengthCountdown < 0) {
                    strengthCountdown = 0
                }
                var ping = NetworkUtil.getLatency(ctx, ip, 1).toInt()
                // Sometimes, the ping value will be extremely high(like, 319041664, 385195024).
                // For this abnormal case, we just ignore it.
                if (ping < 60000) {
                    if (ping >= NetworkUtil.NETWORK_PING_DELAY_VERY_HIGH && pingCountdown < 1) {
                        showPingLatencyStatus = NetworkUtil.NETWORK_PING_DELAY_VERY_HIGH
                        pingCountdown = MAX_COUNT
                    } else if (ping >= NetworkUtil.NETWORK_PING_DELAY_HIGH && pingCountdown < 1) {
                        showPingLatencyStatus = NetworkUtil.NETWORK_PING_DELAY_HIGH
                        pingCountdown = MAX_COUNT
                    }
                } else {
                    // DO NOT show abnormal ping value when its value is greater equal than 60000
                    ping = -3
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    globalWifiSignal = NetworkUtil.getWifiNetworkStatsBelowAndroidS(ctx)
                }
                val strengthIn100Score = globalWifiSignal?.score ?: -1
                if (strengthIn100Score != Int.MIN_VALUE) {
                    if (strengthIn100Score <= NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD && strengthCountdown < 1) {
                        showWifiSignalStatus = NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD
                        strengthCountdown = MAX_COUNT
                    } else if (strengthIn100Score <= NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD && strengthCountdown < 1) {
                        showWifiSignalStatus = NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD
                        strengthCountdown = MAX_COUNT
                    }
                }
                var downloadSpeed: Long = 0
                var uploadSpeed: Long = 0
                if (ping > -1) { // Network is online.
                    trafficStat?.getSpeed()?.let { (download, upload) ->
                        downloadSpeed = download / freq
                        uploadSpeed = upload / freq
                    }
                }

                callback.invoke(
                    NetworkMonitorResult(
                        downloadSpeed,
                        uploadSpeed,
                        ping,
                        showPingLatencyStatus,
                        globalWifiSignal?.linkSpeed ?: -1,
                        globalWifiSignal?.rssi ?: -1,
                        globalWifiSignal?.scoreIn5 ?: -1,
                        globalWifiSignal?.score ?: -1,
                        showWifiSignalStatus
                    )
                )
                monitorHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(freq.toLong()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * The data will be sent in every *freq* second(s)
     *
     * Need following permission:**
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * <!-- Above Android 12, you also need CHANGE_NETWORK_STATE permission. -->
     * <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(allOf = [Manifest.permission.CHANGE_NETWORK_STATE, Manifest.permission.ACCESS_NETWORK_STATE])
    fun startMonitor(freq: Int = 1) {
        LogContext.log.i(TAG, "startMonitor()")
        val interval: Int = if (freq < 1) 1 else freq
        this.freq = interval
        trafficStat = TrafficStatHelper.getInstance(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            NetworkUtil.getWifiNetworkStatsAboveAndroidS(ctx) { wifiSignal ->
                globalWifiSignal = wifiSignal
            }
        }
        monitorHandler.post(pingRunnable)
    }

    fun stopMonitor() {
        LogContext.log.i(TAG, "stopMonitor()")
        releaseMonitorThread()
        globalWifiSignal = null
    }

    private fun releaseMonitorThread() {
        LogContext.log.w(TAG, "releaseMonitorThread()")
        runCatching {
            monitorHandler.removeCallbacksAndMessages(null)
            monitorThread.interrupt()
        }.getOrNull()
    }

    @Keep
    data class NetworkMonitorResult(
        val downloadSpeed: Long,
        val uploadSpeed: Long,
        val ping: Int,
        val showPingTips: Int,
        val linkSpeed: Int,
        val rssi: Int,
        val wifiScoreIn5: Int,
        val wifiScore: Int,
        val showWifiSig: Int
    )
}
