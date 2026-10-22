package com.leovp.androidbase.framework

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.widget.EditText
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.viewbinding.ViewBinding
import com.leovp.android.exts.hideNavigationBar
import com.leovp.android.exts.requestFullScreenAfterVisible
import com.leovp.android.exts.requestFullScreenBeforeSetContentView
import com.leovp.android.exts.toast
import com.leovp.android.utils.NetworkUtil
import com.leovp.androidbase.exts.android.closeSoftKeyboard
import com.leovp.androidbase.utils.network.InternetUtil
import com.leovp.androidbase.utils.network.NetworkMonitor
import com.leovp.androidbase.utils.ui.BetterActivityResult
import com.leovp.kotlin.exts.humanReadableByteCount
import com.leovp.log.LogContext
import com.leovp.log.base.LogOutType
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Attention: If you use _navigation component_ in your activity,
 * **DO NOT** assign _layoutResId_ in your constructor.
 *
 * Usage:
 * ```
 * class YourActivity : BaseActivity<YourActivityBinding>({
 *     fullscreen = false
 *     hideNavigationBar = false
 *     autoHideSoftKeyboard = true
 * }) {
 *    override fun getTagName(): String = "LogTag"
 *
 *    override fun getViewBinding(savedInstanceState: Bundle?): YourActivityBinding {
 *         return YourActivityBinding.inflate(layoutInflater)
 *    }
 *     // Your class contents here.
 * }
 * ```
 *
 * Author: Michael Leo
 * Date: 2022/6/28 16:35
 */
abstract class BaseActivity<B : ViewBinding>(
    @LayoutRes layoutResId: Int = 0,
    init: (ActivityConfig.() -> Unit)? = null,
) : AppCompatActivity(layoutResId) {
    abstract fun getTagName(): String

    @Suppress("WeakerAccess")
    protected val tag: String by lazy { getTagName() }

    private var defaultConfig = ActivityConfig()

    init {
        init?.let { defaultConfig.it() }
    }

    protected lateinit var binding: B
    abstract fun getViewBinding(savedInstanceState: Bundle?): B

    @Suppress("WeakerAccess")
    protected lateinit var simpleActivityLauncher: BetterActivityResult<Intent, ActivityResult>

    /**
     * Written from the DNS lookup thread and read from the main thread, so the holder itself is a
     * final field: an AtomicReference parked in a plain `var` would publish the reference without
     * publishing the field.
     */
    private val networkMonitor = AtomicReference<NetworkMonitor?>(null)

    /** Invalidates a DNS lookup still in flight when the monitor is torn down. */
    private val trafficMonitorGeneration = AtomicLong(0)

    /** Serializes the final monitor publication/start handshake with teardown. */
    private val trafficMonitorLock = Any()

    open fun onCreateBeginning() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogContext.log.w(tag, "=====> onCreate <=====", outputType = LogOutType.FRAMEWORK)
        if (defaultConfig.fullscreen) requestFullScreenBeforeSetContentView()
        onCreateBeginning()
        super.onCreate(savedInstanceState)
        binding = getViewBinding(savedInstanceState).apply { setContentView(root) }
        simpleActivityLauncher = BetterActivityResult
            .registerForActivityResult(this, ActivityResultContracts.StartActivityForResult())
        // EventBus.getDefault().register(this)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
            //            setDisplayShowTitleEnabled(true)
            //            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (parentActivityIntent == null) finish() else NavUtils.navigateUpFromSameTask(this)
        return true
    }

    /**
     * If you set `android:configChanges="orientation|screenSize"`
     * for activity on `AndroidManifest`, this method will be called.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        LogContext.log.w(
            tag,
            "=====> onConfigurationChanged <=====",
            outputType = LogOutType.FRAMEWORK
        )
        super.onConfigurationChanged(newConfig)
    }

    override fun onStart() {
        LogContext.log.w(tag, "=====> onStart <=====", outputType = LogOutType.FRAMEWORK)
        super.onStart()
    }

    override fun onRestart() {
        LogContext.log.w(tag, "=====> onRestart <=====", outputType = LogOutType.FRAMEWORK)
        super.onRestart()
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        LogContext.log.i(tag, "=====> onNewIntent <=====", outputType = LogOutType.FRAMEWORK)
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        LogContext.log.w(tag, "=====> onResume <=====", outputType = LogOutType.FRAMEWORK)
        super.onResume()
        if (defaultConfig.fullscreen) requestFullScreenAfterVisible()
        if (defaultConfig.hideNavigationBar) hideNavigationBar()
    }

    override fun onPause() {
        LogContext.log.w(tag, "=====> onPause <=====", outputType = LogOutType.FRAMEWORK)
        super.onPause()
    }

    override fun onStop() {
        LogContext.log.w(tag, "=====> onStop <=====", outputType = LogOutType.FRAMEWORK)
        super.onStop()
    }

    override fun onDestroy() {
        LogContext.log.w(tag, "=====> onDestroy <=====", outputType = LogOutType.FRAMEWORK)
        stopTrafficMonitor()
        // EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    // ==============================

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val v = currentFocus
        val ret = super.dispatchTouchEvent(event)
        try {
            if (v is EditText) {
                // Reuse the focus captured before super.dispatchTouchEvent(); reading
                // currentFocus again here could be null and crash on the !! assertion.
                val focusView = v
                val focusViewLocationOnScreen = IntArray(2)
                focusView.getLocationOnScreen(focusViewLocationOnScreen)
                val x = event.rawX + focusView.left - focusViewLocationOnScreen[0]
                val y = event.rawY + focusView.top - focusViewLocationOnScreen[1]
                if (defaultConfig.autoHideSoftKeyboard &&
                    event.action == MotionEvent.ACTION_DOWN &&
                    (
                        x < focusView.left ||
                            x > focusView.right ||
                            y < focusView.top ||
                            y > focusView.bottom
                        )
                ) {
                    closeSoftKeyboard()
                }
            }
        } catch (e: Exception) {
            LogContext.log.e(
                tag,
                "dispatchTouchEvent() exception.",
                e,
                outputType = LogOutType.FRAMEWORK
            )
        }
        return ret
    }

    // ==============================
    //    class LangChangeEvent
    //
    //    @Suppress("unused")
    //    @Subscribe(threadMode = ThreadMode.MAIN)
    //    fun onLangChangedEvent(@Suppress("UNUSED_PARAMETER") event: LangChangeEvent) {
    //        recreate()
    //    }

//    override fun attachBaseContext(base: Context) {
//        super.attachBaseContext(LangUtil.getInstance(base).setAppLanguage(base))
//    }
    // ==============================

    data class ActivityConfig(
        var fullscreen: Boolean = false,
        var hideNavigationBar: Boolean = false,
        var autoHideSoftKeyboard: Boolean = true,
        var trafficConfig: TrafficConfig = TrafficConfig(),
    )

    data class TrafficConfig(
        var allowToOutputDefaultWifiTrafficInfo: Boolean = false,
        var frequencyInSecond: Int = 3,
    )

    // ==============================

    /**
     * Stops the traffic monitor this Activity owns, if any, and invalidates a DNS lookup still in
     * flight so a late one cannot start a monitor nobody would release. Idempotent;
     * [startTrafficNetwork] may be called again afterwards.
     *
     * [onDestroy] calls this unconditionally, so subclasses need not pair it with
     * [startTrafficNetwork] in [onStop]. The monitor is meant to keep polling while the Activity
     * is invisible, which is why [onStop] carries no "are you leaking it?" warning: such a warning
     * would fire precisely when things work as designed.
     *
     * Blocks the caller while a [NetworkMonitor.startMonitor] this Activity triggered is still
     * running on the DNS lookup thread, because publication and start share this method's lock.
     * That start creates a [android.os.HandlerThread], builds a process-wide traffic helper on
     * first use and, on Android 12 (API 31) and above, registers a Wi-Fi network callback, so the
     * wait can span a binder round trip -- [onDestroy] pays it on the main thread.
     *
     * In exchange, once this returns no lookup whose generation snapshot predates this call can
     * start a monitor. A [startTrafficNetwork] that has already entered but not yet taken that
     * snapshot is not fenced, so drive both from one thread -- normally the main thread.
     *
     * A result already in flight may still reach [startTrafficNetwork]'s callback afterwards, see
     * [NetworkMonitor.stopMonitor]. This class drops such a result only once the Activity is
     * destroyed or finishing; after an explicit stop on a live Activity one trailing result can
     * still be delivered.
     *
     * Migration: earlier versions neither locked nor invalidated an in-flight lookup, so a lookup
     * returning after this method could publish and start a monitor nothing ever released. The
     * signature is unchanged; callers gain the fence and the blocking described above.
     */
    fun stopTrafficMonitor() {
        synchronized(trafficMonitorLock) {
            // Invalidate any DNS lookup still in flight before tearing the current monitor down.
            trafficMonitorGeneration.incrementAndGet()
            networkMonitor.getAndSet(null)?.stopMonitor()
        }
    }

    /**
     * Resolves [domain] and starts a monitor reporting device-wide traffic throughput and Wi-Fi
     * signal, plus the ping latency to the first IP it returns. The monitor belongs to this
     * Activity: at most one runs at a time, a second call while one is live is refused with a log
     * message, and [onDestroy] releases it unconditionally. Monitoring deliberately continues
     * while the Activity is invisible, so do not stop it from [onStop] -- see [stopTrafficMonitor].
     *
     * Returns before anything is started: the lookup runs on a plain background thread, so a
     * failure to resolve [domain] surfaces only in the log and a debug toast.
     *
     * [callback] is posted to the main thread, never run inline on the monitor thread, so blocking
     * inside it can neither stall the monitor nor deadlock against [stopTrafficMonitor]'s lock. It
     * does occupy the main thread, where [onDestroy] runs, so still do not block in it. It is
     * skipped once this Activity is destroyed or finishing, so a trailing result is dropped rather
     * than merely delayed.
     *
     * Migration: this no longer requires `CHANGE_NETWORK_STATE`; nothing on the monitor path
     * mutates network state. The "are you leaking networkMonitor?" warning earlier versions
     * logged from [onStop] is gone, because keeping the monitor alive while invisible is the
     * intended contract -- see [stopTrafficMonitor].
     *
     * Needs the following permissions:
     * ```xml
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     * <!-- Below Android 12 (API 31) only: the Wi-Fi signal is read through WifiManager. -->
     * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
     * ```
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun startTrafficNetwork(
        domain: String,
        callback: ((NetworkMonitor.NetworkMonitorResult) -> Unit)? = null
    ) {
        if (networkMonitor.get() != null) {
            LogContext.log.w(tag, "networkMonitor had already existed! Do NOT create it again!")
            return
        }
        val generation = trafficMonitorGeneration.get()
        LogContext.log.i(tag, "Monitor domain=$domain")
        // This callback runs on a plain background thread started by InternetUtil, so everything
        // below races with the main thread's onDestroy().
        InternetUtil.getIpsByHost(domain) { socketIps ->
            if (socketIps.isEmpty()) {
                LogContext.log.e(tag, "Can not get ip by host[$domain].")
                toast("Can't get ip by host[$domain].", debug = true)
                return@getIpsByHost
            }
            val socketIp = socketIps[0]
            LogContext.log.i(tag, "socketIp=$socketIp")

            if (networkMonitor.get() != null) {
                LogContext.log.e(tag, "networkMonitor had already existed! Do NOT create it again!")
                return@getIpsByHost
            }
            // The DNS lookup above is async; the Activity may already be gone by the time it
            // returns. Constructing a NetworkMonitor starts no thread -- startMonitor() is what
            // creates the HandlerThread -- so this is only an early-out, not the leak guard. The
            // critical section below is what actually makes a late lookup safe: it re-checks the
            // generation and publishes under the same lock stopTrafficMonitor() takes.
            if (isDestroyed || isFinishing) {
                LogContext.log.w(tag, "Activity is destroyed/finishing. Skip NetworkMonitor.")
                return@getIpsByHost
            }
            val monitor = NetworkMonitor(this@BaseActivity, socketIp) { info ->
                // stopMonitor() no longer fences a callback already in flight, and runOnUiThread
                // posts rather than runs, so a result can reach the main thread after onDestroy.
                callback?.let { runOnUiThread { if (!isDestroyed && !isFinishing) it(info) } }

                if (!defaultConfig.trafficConfig.allowToOutputDefaultWifiTrafficInfo) {
                    return@NetworkMonitor
                }

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
                    downloadSpeedStr, uploadSpeedStr,
                    if (latencyStatus.isNullOrBlank()) {
                        "${info.ping}ms"
                    } else {
                        "${info.ping}ms($latencyStatus)"
                    },
                    info.linkSpeed,
                    info.rssi, info.wifiScoreIn5, info.wifiScore,
                    if (wifiSignalStatus.isNullOrBlank()) "" else " ($wifiSignalStatus)"
                )
                LogContext.log.i(tag, infoStr)
            }
            synchronized(trafficMonitorLock) {
                val stale = trafficMonitorGeneration.get() != generation
                val activityGone = isDestroyed || isFinishing
                val slotOccupied = networkMonitor.get() != null
                if (stale || activityGone || slotOccupied) {
                    val reason = when {
                        stale -> "a teardown invalidated it"
                        activityGone -> "the Activity is destroyed or finishing"
                        else -> "another start owns it"
                    }
                    LogContext.log.w(tag, "Discard monitor lookup gen=$generation: $reason.")
                    monitor.stopMonitor()
                    return@getIpsByHost
                }

                networkMonitor.set(monitor)
                // Keep publication and start in the same critical section as teardown. If start
                // wins, stopTrafficMonitor() waits and stops the started monitor before returning;
                // if teardown wins, the stale lookup is rejected above and never starts.
                try {
                    monitor.startMonitor(defaultConfig.trafficConfig.frequencyInSecond)
                } catch (error: InterruptedException) {
                    networkMonitor.compareAndSet(monitor, null)
                    monitor.stopMonitor()
                    Thread.currentThread().interrupt()
                    LogContext.log.e(
                        tag,
                        "Failed to start networkMonitor for gen=$generation.",
                        error
                    )
                } catch (error: Exception) {
                    networkMonitor.compareAndSet(monitor, null)
                    monitor.stopMonitor()
                    LogContext.log.e(
                        tag,
                        "Failed to start networkMonitor for gen=$generation.",
                        error
                    )
                }
            }
        }
    }

    // ==============================
}
