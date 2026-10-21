package com.leovp.androidbase.utils.network

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.Keep
import androidx.annotation.RequiresPermission
import com.leovp.android.utils.NetworkUtil
import com.leovp.log.LogContext
import java.util.concurrent.TimeUnit

/**
 * Author: Michael Leo
 * Date: 20-6-12 下午4:19
 *
 * Monitor the speed, ping and Wi-Fi signal.
 *
 * Need following permission:
 * ```xml
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 * ```
 *
 * Usage:
 * ```kotlin
 * val networkMonitor = NetworkMonitor(ctx, ip) { networkMonitorResult -> }
 * networkMonitor.startMonitor()
 * networkMonitor.stopMonitor()
 * ```
 */
class NetworkMonitor internal constructor(
    ctx: Context,
    private val ip: String,
    private val callback: (NetworkMonitorResult) -> Unit,
    private val schedulerFactory: () -> NetworkMonitorScheduler,
    private val beforeResultDispatch: () -> Unit = {},
) {
    private val ctx: Context = ctx.applicationContext ?: ctx
    constructor(
        ctx: Context,
        ip: String,
        callback: (NetworkMonitorResult) -> Unit,
    ) : this(ctx, ip, callback, { HandlerThreadNetworkMonitorScheduler() }, {})

    companion object {
        private const val TAG = "NM"
    }

    @Volatile
    private var globalWifiSignal: NetworkUtil.WifiSignal? = null
    private val monitorLifecycleLock = Any()
    @Volatile
    private var monitorScheduler: NetworkMonitorScheduler? = null

    @Volatile
    private var monitorStopped = false

    @Volatile
    private var monitorGeneration = 0L

    private var wifiSignalCallback: ((NetworkUtil.WifiSignal) -> Unit)? = null

    private fun createPingRunnable(
        generation: Long,
        scheduler: NetworkMonitorScheduler,
        interval: Int,
        trafficStat: TrafficStatHelper,
    ): Runnable = object : Runnable {
        private var pingCountdown: Long = 0
        private var strengthCountdown: Long = 0
        private val MAX_COUNT: Long = 10
        private var showPingLatencyStatus: Int = -1
        private var showWifiSignalStatus: Int = -1

        @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        override fun run() {
            if (!isCurrentMonitor(generation, scheduler)) return
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
                if (!isCurrentMonitor(generation, scheduler)) return
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
                val wifiSignal = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    val signal = NetworkUtil.getWifiNetworkStatsBelowAndroidS(ctx)
                    if (!isCurrentMonitor(generation, scheduler)) return
                    signal
                } else {
                    globalWifiSignal
                }
                val strengthIn100Score = wifiSignal?.score ?: -1
                if (strengthIn100Score != Int.MIN_VALUE) {
                    if (strengthIn100Score <= NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD &&
                        strengthCountdown < 1
                    ) {
                        showWifiSignalStatus = NetworkUtil.NETWORK_SIGNAL_STRENGTH_VERY_BAD
                        strengthCountdown = MAX_COUNT
                    } else if (strengthIn100Score <= NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD &&
                        strengthCountdown < 1
                    ) {
                        showWifiSignalStatus = NetworkUtil.NETWORK_SIGNAL_STRENGTH_BAD
                        strengthCountdown = MAX_COUNT
                    }
                }
                beforeResultDispatch()
                synchronized(monitorLifecycleLock) {
                    if (!isCurrentMonitor(generation, scheduler)) return
                    var downloadSpeed: Long = 0
                    var uploadSpeed: Long = 0
                    if (ping > -1) { // Network is online.
                        trafficStat.getSpeed().let { (download, upload) ->
                            downloadSpeed = download / interval
                            uploadSpeed = upload / interval
                        }
                    }
                    callback.invoke(
                        NetworkMonitorResult(
                            downloadSpeed,
                            uploadSpeed,
                            ping,
                            showPingLatencyStatus,
                            wifiSignal?.linkSpeed ?: -1,
                            wifiSignal?.rssi ?: -1,
                            wifiSignal?.scoreIn5 ?: -1,
                            wifiSignal?.score ?: -1,
                            showWifiSignalStatus
                        )
                    )
                }
            } catch (error: Exception) {
                LogContext.log.e(TAG, "Network monitor iteration failed", error)
            }
            if (isCurrentMonitor(generation, scheduler)) {
                scheduler.postDelayed(this, TimeUnit.SECONDS.toMillis(interval.toLong()))
            }
        }
    }

    private fun isCurrentMonitor(generation: Long, scheduler: NetworkMonitorScheduler): Boolean =
        !monitorStopped &&
            generation == monitorGeneration &&
            scheduler === monitorScheduler

    /**
     * The data will be sent in every *freq* second(s)
     *
     * Repeated calls replace the current session. Callbacks run on the monitor thread and must
     * remain short: lifecycle changes wait for a running callback to finish. Do not wait inside
     * a callback for another thread that calls [startMonitor] or [stopMonitor].
     *
     * Need following permission:**
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun startMonitor(freq: Int = 1) {
        synchronized(monitorLifecycleLock) {
            LogContext.log.i(TAG, "startMonitor()")
            monitorGeneration++
            val generation = monitorGeneration
            monitorStopped = true
            unregisterWifiSignalCallback()
            releaseMonitorScheduler()
            globalWifiSignal = null
            val interval: Int = if (freq < 1) 1 else freq
            var started = false
            try {
                val trafficStat = TrafficStatHelper.getInstance(ctx)
                val scheduler = schedulerFactory()
                monitorScheduler = scheduler
                monitorStopped = false
                registerWifiSignalCallback(generation, scheduler)
                scheduler.post(createPingRunnable(generation, scheduler, interval, trafficStat))
                started = true
            } finally {
                if (!started) stopMonitor()
            }
        }
    }

    /**
     * Stops future results and releases monitoring resources. An already running callback finishes
     * before this method returns. This instance can subsequently be started again.
     */
    fun stopMonitor() {
        synchronized(monitorLifecycleLock) {
            LogContext.log.i(TAG, "stopMonitor()")
            monitorStopped = true
            monitorGeneration++
            try {
                unregisterWifiSignalCallback()
            } finally {
                releaseMonitorScheduler()
                globalWifiSignal = null
            }
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun registerWifiSignalCallback(generation: Long, scheduler: NetworkMonitorScheduler) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val sessionCallback: (NetworkUtil.WifiSignal) -> Unit = { signal ->
            synchronized(monitorLifecycleLock) {
                if (isCurrentMonitor(generation, scheduler)) globalWifiSignal = signal
            }
        }
        if (NetworkUtil.getWifiNetworkStatsAboveAndroidS(ctx, sessionCallback)) {
            wifiSignalCallback = sessionCallback
        } else {
            LogContext.log.w(TAG, "Wi-Fi signal callback registration failed")
        }
    }

    private fun unregisterWifiSignalCallback() {
        val callback = wifiSignalCallback ?: return
        wifiSignalCallback = null
        if (!NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(ctx, callback)) {
            LogContext.log.w(TAG, "Wi-Fi signal callback unregistration failed")
        }
    }

    private fun releaseMonitorScheduler() {
        val scheduler = monitorScheduler ?: return
        monitorScheduler = null
        LogContext.log.w(TAG, "releaseMonitorScheduler()")
        try {
            try {
                scheduler.cancelAll()
            } finally {
                scheduler.close()
            }
        } catch (error: Exception) {
            LogContext.log.e(TAG, "Failed to release monitor scheduler", error)
        }
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
        val showWifiSig: Int,
    )
}

internal interface NetworkMonitorScheduler {
    fun post(task: Runnable)

    fun postDelayed(task: Runnable, delayMillis: Long)

    fun cancelAll()

    fun close()
}

private class HandlerThreadNetworkMonitorScheduler : NetworkMonitorScheduler {
    private val thread = HandlerThread("monitor-thread").apply { start() }
    private val handler = Handler(thread.looper)

    override fun post(task: Runnable) {
        handler.post(task)
    }

    override fun postDelayed(task: Runnable, delayMillis: Long) {
        handler.postDelayed(task, delayMillis)
    }

    override fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun close() {
        // quitSafely() stops the Looper after pending work drains; interrupt() would leak it.
        thread.quitSafely()
    }
}
