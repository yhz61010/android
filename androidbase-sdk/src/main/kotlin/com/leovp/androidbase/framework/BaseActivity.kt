package com.leovp.androidbase.framework

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import com.leovp.android.utils.LangUtil
import com.leovp.android.utils.NetworkUtil
import com.leovp.androidbase.exts.android.closeSoftKeyboard
import com.leovp.androidbase.utils.network.InternetUtil
import com.leovp.androidbase.utils.network.NetworkMonitor
import com.leovp.androidbase.utils.ui.BetterActivityResult
import com.leovp.kotlin.exts.humanReadableByteCount
import com.leovp.log.LogContext
import com.leovp.log.base.ILog.Companion.OUTPUT_TYPE_FRAMEWORK
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * This class has already enabled Custom Language feature.
 *
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
abstract class BaseActivity<B : ViewBinding>(@LayoutRes layoutResId: Int = 0, init: (ActivityConfig.() -> Unit)? = null) :
    AppCompatActivity(layoutResId) {
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

    private var networkMonitor: AtomicReference<NetworkMonitor>? = null

    open fun onCreateBeginning() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogContext.log.w(tag, "=====> onCreate <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
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
            outputType = OUTPUT_TYPE_FRAMEWORK
        )
        super.onConfigurationChanged(newConfig)
    }

    override fun onStart() {
        LogContext.log.w(tag, "=====> onStart <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
        super.onStart()
    }

    override fun onRestart() {
        LogContext.log.w(tag, "=====> onRestart <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
        super.onRestart()
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent?) {
        LogContext.log.i(tag, "=====> onNewIntent <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        LogContext.log.w(tag, "=====> onResume <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
        super.onResume()
        if (defaultConfig.fullscreen) requestFullScreenAfterVisible()
        if (defaultConfig.hideNavigationBar) hideNavigationBar()
    }

    override fun onPause() {
        LogContext.log.w(tag, "=====> onPause <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
        super.onPause()
    }

    override fun onStop() {
        LogContext.log.w(tag, "=====> onStop <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
        super.onStop()
        if (networkMonitor != null) {
            LogContext.log.w(
                tag,
                "Are you leaking networkMonitor? Don't forget to call stopTrafficMonitor() if you don't need it anymore."
            )
        }
    }

    override fun onDestroy() {
        LogContext.log.w(tag, "=====> onDestroy <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
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
                val focusView = currentFocus!!
                val focusViewLocationOnScreen = IntArray(2)
                focusView.getLocationOnScreen(focusViewLocationOnScreen)
                val x = event.rawX + focusView.left - focusViewLocationOnScreen[0]
                val y = event.rawY + focusView.top - focusViewLocationOnScreen[1]
                if (defaultConfig.autoHideSoftKeyboard && event.action == MotionEvent.ACTION_DOWN &&
                    (x < focusView.left || x > focusView.right || y < focusView.top || y > focusView.bottom)
                ) {
                    closeSoftKeyboard()
                }
            }
        } catch (e: Exception) {
            LogContext.log.e(
                tag,
                "dispatchTouchEvent() exception.",
                e,
                outputType = OUTPUT_TYPE_FRAMEWORK
            )
        }
        return ret
    }

    // ==============================
    class LangChangeEvent

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLangChangedEvent(@Suppress("UNUSED_PARAMETER") event: LangChangeEvent) {
        recreate()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LangUtil.getInstance(base).setAppLanguage(base))
    }
    // ==============================

    data class ActivityConfig(
        var fullscreen: Boolean = false,
        var hideNavigationBar: Boolean = false,
        var autoHideSoftKeyboard: Boolean = true,
        var trafficConfig: TrafficConfig = TrafficConfig()
    )

    data class TrafficConfig(
        var allowToOutputDefaultWifiTrafficInfo: Boolean = false,
        var frequencyInSecond: Int = 3
    )

    // ==============================

    fun stopTrafficMonitor() {
        networkMonitor?.get()?.stopMonitor()
        networkMonitor = null
    }

    @RequiresPermission(allOf = [Manifest.permission.CHANGE_NETWORK_STATE, Manifest.permission.ACCESS_NETWORK_STATE])
    fun startTrafficNetwork(
        domain: String,
        callback: ((NetworkMonitor.NetworkMonitorResult) -> Unit)? = null
    ) {
        if (networkMonitor?.get() != null) {
            LogContext.log.w(tag, "networkMonitor had already existed! Do NOT create it again!")
            return
        }
        LogContext.log.i(tag, "Monitor domain=$domain")
        InternetUtil.getIpsByHost(domain) { socketIps ->
            if (socketIps.isEmpty()) {
                LogContext.log.e(tag, "Can not get ip by host[$domain].")
                toast("Can't get ip by host[$domain].", debug = true)
                return@getIpsByHost
            }
            val socketIp = socketIps[0]
            LogContext.log.i(tag, "socketIp=$socketIp")

            if (networkMonitor?.get() != null) {
                LogContext.log.e(tag, "networkMonitor had already existed! Do NOT create it again!")
                return@getIpsByHost
            }
            networkMonitor = AtomicReference(
                NetworkMonitor(this@BaseActivity, socketIp) { info ->
                    callback?.let { runOnUiThread { it(info) } }

                    if (!defaultConfig.trafficConfig.allowToOutputDefaultWifiTrafficInfo) return@NetworkMonitor

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
                        if (latencyStatus.isNullOrBlank()) "${info.ping}ms" else "${info.ping}ms($latencyStatus)",
                        info.linkSpeed,
                        info.rssi, info.wifiScoreIn5, info.wifiScore,
                        if (wifiSignalStatus.isNullOrBlank()) "" else " ($wifiSignalStatus)"
                    )
                    LogContext.log.i(tag, infoStr)
                }
            ).also {
                it.get().startMonitor(defaultConfig.trafficConfig.frequencyInSecond)
            }
        }
    }

    // ==============================
}
