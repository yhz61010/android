package com.leovp.androidbase.utils.network

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.leovp.androidbase.utils.LLog
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
class NetworkMonitor(private val ctx: Context, private val ip: String, f: (NetworkMonitorResult) -> Unit) {
    companion object {
        private const val TAG = "NM"
    }

    private var freq = 1

    private var trafficStat: TrafficStatHelper? = null
    private val monitorThread = HandlerThread("monitor-thread").apply { start() }
    private val monitorHandler = Handler(monitorThread.looper)
    private val pingRunnable: Runnable = object : Runnable {
        private var pingCountdown: Long = 0
        private var strengthCountdown: Long = 0
        private val MAX_COUNT: Long = 10
        private var showPingLatencyStatus: Int = -1
        private var showWifiSignalStatus: Int = -1
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
                val networkStats: IntArray? = NetworkUtil.getWifiNetworkStats(ctx)
                if (networkStats != null) {
                    val strengthIn100Score = networkStats[3]
                    if (strengthIn100Score != Int.MIN_VALUE) {
                        if (strengthIn100Score <= NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD && strengthCountdown < 1) {
                            showWifiSignalStatus = NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD
                            strengthCountdown = MAX_COUNT
                        } else if (strengthIn100Score <= NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD && strengthCountdown < 1) {
                            showWifiSignalStatus = NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD
                            strengthCountdown = MAX_COUNT
                        }
                    }
                }
                // Sometimes, the ping value will be extremely high(like, 319041664, 385195024). For this abnormal case, we just ignore it.
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

                var downloadSpeed: Long = 0
                var uploadSpeed: Long = 0
                trafficStat?.getSpeed()?.let {
                    downloadSpeed = it[0] / freq
                    uploadSpeed = it[1] / freq
                }

                f.invoke(
                    NetworkMonitorResult(
                        downloadSpeed,
                        uploadSpeed,
                        ping,
                        showPingLatencyStatus,
                        networkStats?.get(0) ?: -1,
                        networkStats?.get(1) ?: -1,
                        networkStats?.get(2) ?: -1,
                        networkStats?.get(3) ?: -1,
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
     */
    fun startMonitor(freq: Int = 1) {
        LLog.i(TAG, "startMonitor()")
        val interval: Int = if (freq < 1) 1 else freq
        this.freq = interval
        trafficStat = TrafficStatHelper.getInstance(ctx)
        monitorHandler.post(pingRunnable)
    }

    fun stopMonitor() {
        LLog.i(TAG, "stopMonitor()")
        releaseMonitorThread()
    }

    private fun releaseMonitorThread() {
        LLog.w(TAG, "releaseMonitorThread()")
        runCatching {
            monitorHandler.removeCallbacksAndMessages(null)
            monitorThread.interrupt()
        }.getOrNull()
    }

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