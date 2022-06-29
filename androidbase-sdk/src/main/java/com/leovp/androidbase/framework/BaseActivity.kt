package com.leovp.androidbase.framework

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.widget.EditText
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.viewbinding.ViewBinding
import com.leovp.androidbase.exts.android.closeSoftKeyboard
import com.leovp.androidbase.utils.ui.BetterActivityResult
import com.leovp.lib_common_android.exts.hideNavigationBar
import com.leovp.lib_common_android.exts.requestFullScreenAfterVisible
import com.leovp.lib_common_android.exts.requestFullScreenBeforeSetContentView
import com.leovp.lib_common_android.utils.LangUtil
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog.Companion.OUTPUT_TYPE_FRAMEWORK
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * This class has already enabled Custom Language feature.
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
abstract class BaseActivity<B : ViewBinding>(init: (ActivityConfig.() -> Unit)? = null) :
    AppCompatActivity() {
    abstract fun getTagName(): String

    @Suppress("WeakerAccess")
    protected val tag: String by lazy { getTagName() }

    var defaultConfig = ActivityConfig()

    init {
        init?.let { defaultConfig.it() }
    }

    protected lateinit var binding: B
    abstract fun getViewBinding(savedInstanceState: Bundle?): B

    @Suppress("WeakerAccess")
    protected lateinit var simpleActivityLauncher: BetterActivityResult<Intent, ActivityResult>

    open fun onCreateBeginning() {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogContext.log.w(tag, "=====> onCreate <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
        if (defaultConfig.fullscreen) requestFullScreenBeforeSetContentView()
        onCreateBeginning()
        super.onCreate(savedInstanceState)
        binding = getViewBinding(savedInstanceState).apply { setContentView(root) }
        simpleActivityLauncher =
                BetterActivityResult.registerForActivityResult(this,
                    ActivityResultContracts.StartActivityForResult())
        EventBus.getDefault().register(this)

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
        LogContext.log.w(tag,
            "=====> onConfigurationChanged <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
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
    }

    override fun onDestroy() {
        LogContext.log.w(tag, "=====> onDestroy <=====", outputType = OUTPUT_TYPE_FRAMEWORK)
        EventBus.getDefault().unregister(this)
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
                if (defaultConfig.autoHideSoftKeyboard && event.action == MotionEvent.ACTION_DOWN && (x < focusView.left || x > focusView.right || y < focusView.top || y > focusView.bottom)) {
                    closeSoftKeyboard()
                }
            }
        } catch (e: Exception) {
            LogContext.log.e(tag,
                "dispatchTouchEvent() exception.", e, outputType = OUTPUT_TYPE_FRAMEWORK)
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
        var autoHideSoftKeyboard: Boolean = true
    )

    // ==============================
}