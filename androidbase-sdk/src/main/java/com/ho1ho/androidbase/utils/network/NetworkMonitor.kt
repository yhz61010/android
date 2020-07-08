package com.ho1ho.androidbase.utils.network

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.ho1ho.androidbase.exts.humanReadableByteCount
import com.ho1ho.androidbase.utils.LLog
import java.util.concurrent.TimeUnit

/**
 * Author: Michael Leo
 * Date: 20-6-12 下午4:19
 *
 * Monitor the speed, ping and wifi signal.
 *
 * Usage:
 * ```kotlin
 * val networkMonitor = NetworkMonitor(ctx, ip)
 * networkMonitor.startMonitor()
 * networkMonitor.stopMonitor()
 * ```
 */
class NetworkMonitor(private val ctx: Context, private val ip: String) {

    private var freq = 1

    init {
        initMonitorThread()
    }

    /**
     * The data will be sent in every *freq* second(s)
     */
    fun startMonitor(freq: Int = 1) {
        LLog.i(TAG, "startMonitor()")
        val interval: Int = if (freq < 1) 1 else freq
        this.freq = interval
        trafficStat = TrafficStatHelper.getInstance(ctx, monitorHandler!!)
        trafficStat?.startCalculateNetSpeed(interval)
        monitorHandler!!.post(pingRunnable)
    }

    fun stopMonitor() {
        LLog.i(TAG, "stopMonitor()")
        releaseMonitorThread()
    }

    private var trafficStat: TrafficStatHelper? = null
    private var monitorHandler: Handler? = null
    private var monitorThread: HandlerThread? = null
    private val pingRunnable: Runnable = object : Runnable {
        private var pingCountdown: Long = 0
        private var strengthCountdown: Long = 0
        private val MAX_COUNT: Long = 10
        override fun run() {
            try {
                if (--pingCountdown < 0) {
                    pingCountdown = 0
                }
                if (--strengthCountdown < 0) {
                    strengthCountdown = 0
                }
                val ping = NetworkUtil.getLatency(ctx, ip, 1).toInt()
                val networkStats: IntArray? = NetworkUtil.getWifiNetworkStats(ctx)
                if (networkStats == null) {
                    LLog.i(TAG, "P:$ping")
                } else {
                    val strengthIn5Score = networkStats[3]
                    // Ping Link Rssi
                    LLog.i(
                        TAG,
                        "P:$ping\tL:${networkStats[0]}Mbps\tR:${networkStats[1]} ${networkStats[2]} $strengthIn5Score/100"
                    )
                    if (strengthIn5Score != Int.MIN_VALUE) {
                        if (strengthIn5Score <= NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD && strengthCountdown < 1) {
                            LLog.w(
                                TAG,
                                "Strength: [${networkStats[1]}][${networkStats[2]}][$strengthIn5Score/100] Very bad"
                            )
                            strengthCountdown = MAX_COUNT
                        } else if (strengthIn5Score <= NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD && strengthCountdown < 1) {
                            LLog.w(TAG, "Strength: [${networkStats[1]}][${networkStats[2]}][$strengthIn5Score/100] Bad")
                            strengthCountdown = MAX_COUNT
                        }
                    }
                }
                // Sometimes, the ping value will be extremely high(like, 319041664, 385195024). For this abnormal case, we just ignore it.
                if (ping < 60000) {
                    if (ping > NetworkUtil.NETWORK_PING_DELAY_VERY_HIGH && pingCountdown < 1) {
                        LLog.w(TAG, "P:$ping Very High")
                        pingCountdown = MAX_COUNT
                    } else if (ping > NetworkUtil.NETWORK_PING_DELAY_HIGH && pingCountdown < 1) {
                        LLog.w(TAG, "P:$ping High")
                        pingCountdown = MAX_COUNT
                    }
                } else {
                    LLog.w(TAG, "DO NOT show abnormal ping value [$ping]")
                }
                monitorHandler?.postDelayed(this, TimeUnit.SECONDS.toMillis(freq.toLong()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initMonitorThread() {
        LLog.i(TAG, "initMonitorThread()")
        monitorThread = HandlerThread("monitor-thread")
        monitorThread!!.start()
        monitorHandler = object : Handler(monitorThread!!.looper) {
            override fun handleMessage(msg: Message) {
                if (TrafficStatHelper.MESSAGE_TRAFFIC_UPDATE == msg.what) {
                    val speedArray = (msg.obj) as Array<*>
                    val downloadSpeed = (speedArray[0] as Long).humanReadableByteCount()
                    val uploadSpeed = (speedArray[1] as Long).humanReadableByteCount()
                    LLog.i(TAG, "S:$downloadSpeed/$uploadSpeed")
                }
                super.handleMessage(msg)
            }
        }
    }

    private fun releaseMonitorThread() {
        LLog.w(TAG, "releaseMonitorThread()")
        trafficStat?.stopCalculateNetSpeed()
        monitorHandler?.removeCallbacksAndMessages(null)
        monitorHandler = null
        monitorThread?.quitSafely()
    }

    companion object {
        private const val TAG = "NM"
    }
}