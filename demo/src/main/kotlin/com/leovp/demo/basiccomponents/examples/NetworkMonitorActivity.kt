package com.leovp.demo.basiccomponents.examples

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresPermission
import com.leovp.android.exts.toast
import com.leovp.android.utils.NetworkUtil
import com.leovp.androidbase.utils.network.InternetUtil
import com.leovp.androidbase.utils.network.NetworkMonitor
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityNetworkMonitorBinding
import com.leovp.json.toJsonString
import com.leovp.kotlin.exts.humanReadableByteCount
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Author: Michael Leo
 * Date: 20-6-16 下午6:03
 *
 * Drives [NetworkMonitor] directly instead of going through `BaseActivity.startTrafficNetwork()`,
 * because the raw monitor is a supported entry point for consumers that have no `BaseActivity`.
 * Driving it by hand means owning the publication handshake too: `InternetUtil` hands the lookup
 * result to a plain background thread, so [onCreate]'s callback races with [onDestroy] on the
 * main thread.
 *
 * What each piece below is for, since copying them without understanding them is worse than not
 * copying them. `monitorLock` is the load-bearing one: publishing the monitor, starting it and
 * tearing it down have to be one critical section, or a lookup that returns late starts a
 * `HandlerThread` and a network callback nobody releases. `monitorGeneration` then lets a late
 * lookup recognise that a teardown already ran without reading `isDestroyed` across threads, and
 * it is what keeps this shape correct once teardown can also happen while the Activity is alive,
 * as it can in `BaseActivity` through its own `stopTrafficMonitor()` -- which, inherited here,
 * operates on `BaseActivity`'s separate monitor and would stop nothing of this screen's. The
 * [AtomicReference] holder is belt and braces here, because every access to it is already under
 * the lock; `BaseActivity` needs it for the unlocked fast-path read at its public entry point.
 *
 * Unlike `BaseActivity`, this screen starts the monitor from [onCreate] only, so it needs no
 * guard against a second concurrent start.
 *
 * The price of that single critical section is that [onDestroy] can block on the main thread
 * while the lookup thread is inside `startMonitor()`, which spans a `HandlerThread` start and a
 * binder round trip -- the same trade `BaseActivity.stopTrafficMonitor()` documents.
 *
 * We need following permissions:
 * ```xml
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 * ```
 */
class NetworkMonitorActivity :
    BaseDemonstrationActivity<ActivityNetworkMonitorBinding>(R.layout.activity_network_monitor) {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityNetworkMonitorBinding =
        ActivityNetworkMonitorBinding.inflate(layoutInflater)

    /** Written on the DNS lookup thread, read on the main thread, always under `monitorLock`. */
    private val networkMonitor = AtomicReference<NetworkMonitor?>(null)

    /** Invalidates a DNS lookup still in flight when the monitor is torn down. */
    private val monitorGeneration = AtomicLong(0)

    /** Serializes the publication/start handshake with the teardown in [onDestroy]. */
    private val monitorLock = Any()

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val generation = monitorGeneration.get()
        InternetUtil.getIpsByHost("leovp.com") { ipList ->
            LogContext.log.i(ITAG, "ipList=${ipList.toJsonString()}")
            if (ipList.isEmpty()) {
                toast("Can't get IP from host.")
                return@getIpsByHost
            }
            // Constructing a NetworkMonitor starts no thread -- startMonitor() creates the
            // HandlerThread -- so building it outside the lock costs nothing to discard.
            val monitor = NetworkMonitor(application, ipList[0], ::render)
            synchronized(monitorLock) {
                val stale = monitorGeneration.get() != generation
                val activityGone = isDestroyed || isFinishing
                if (stale || activityGone) {
                    val reason = if (stale) "a teardown invalidated it" else "the Activity is gone"
                    LogContext.log.w(ITAG, "Discard monitor lookup gen=$generation: $reason.")
                    monitor.stopMonitor()
                    return@getIpsByHost
                }
                networkMonitor.set(monitor)
                // Starting inside the lock is the point: onDestroy() cannot take the monitor away
                // between the publication above and the start below.
                try {
                    monitor.startMonitor(MONITOR_FREQUENCY_IN_SECOND)
                } catch (error: Exception) {
                    // This lambda runs on a bare thread with no handler, so letting a non-Error
                    // failure escape would take the process down over a diagnostics screen. An
                    // Error -- OutOfMemoryError from HandlerThread.start(), say -- still escapes;
                    // startMonitor()'s own finally releases what it built, and onDestroy() clears
                    // the holder. Two deliberate simplifications against BaseActivity, which
                    // splits this catch: set(null) is enough instead of compareAndSet(monitor,
                    // null) because publication and this catch are one unbroken critical section,
                    // and the interrupt bit is not restored because this thread dies right after
                    // the lambda, so no later code on it could observe the flag.
                    networkMonitor.set(null)
                    monitor.stopMonitor()
                    LogContext.log.e(ITAG, "Failed to start networkMonitor.", error)
                }
            }
        }
    }

    /**
     * Runs on the monitor thread. [NetworkMonitor.stopMonitor] no longer fences a callback already
     * in flight, so this can still be reached after [onDestroy]; the view updates are therefore
     * posted and guarded instead of applied blindly.
     */
    private fun render(info: NetworkMonitor.NetworkMonitorResult) {
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
            Locale.ENGLISH,
            "↓%s\t↑%s\t%s\t%dMbps\tR:%d %d %d%s",
            downloadSpeedStr,
            uploadSpeedStr,
            if (latencyStatus.isNullOrBlank()) {
                "${info.ping}ms"
            } else {
                "${info.ping}ms($latencyStatus)"
            },
            info.linkSpeed,
            info.rssi,
            info.wifiScoreIn5,
            info.wifiScore,
            if (wifiSignalStatus.isNullOrBlank()) "" else " ($wifiSignalStatus)"
        )
        LogContext.log.i(ITAG, infoStr)
        runOnUiThread {
            if (!isDestroyed && !isFinishing) {
                binding.txtNetworkStatus.text = infoStr
                binding.scrollView2.post { binding.scrollView2.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    override fun onDestroy() {
        // DO NOT forget to stop monitor. Bumping the generation first invalidates a DNS lookup
        // that is still in flight, so it cannot publish a monitor nobody would release.
        synchronized(monitorLock) {
            monitorGeneration.incrementAndGet()
            networkMonitor.getAndSet(null)?.stopMonitor()
        }
        super.onDestroy()
    }

    private companion object {
        const val MONITOR_FREQUENCY_IN_SECOND = 2
    }
}
