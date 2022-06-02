package com.leovp.demo.base

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.widget.EditText
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.leovp.androidbase.exts.android.closeSoftKeyboard
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.ui.BetterActivityResult
import com.leovp.lib_common_android.utils.LangUtil
import com.leovp.log_sdk.LogContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Author: Michael Leo
 * Date: 20-6-17 上午11:14
 */
abstract class BaseDemonstrationActivity : AppCompatActivity() {
    abstract fun getTagName(): String

    val tag by lazy { getTagName() }

    class LangChangeEvent

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLangChangedEvent(@Suppress("UNUSED_PARAMETER") event: LangChangeEvent) {
        recreate()
    }

    lateinit var simpleActivityLauncher: BetterActivityResult<Intent, ActivityResult>

    override fun attachBaseContext(base: Context) {
        LogContext.log.i(tag, "=====> attachBaseContext setLocale()")
        super.attachBaseContext(LangUtil.getInstance(base).setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogContext.log.i(tag, "onCreate()")
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = intent.getStringExtra("title")
        simpleActivityLauncher = BetterActivityResult.registerForActivityResult(this, ActivityResultContracts.StartActivityForResult()) { result ->
            toast("Result in BaseActivity: ${result.resultCode}")
        }
        val lang = LangUtil.getInstance(this).getAppLanguage()
        LogContext.log.i(tag, "Pref lang=$lang")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    //    override fun onOptionsItemSelected(item: MenuItem): Boolean {
    //        return when (item.itemId) {
    //            R.id.home -> {
    //                finish()
    //                true
    //            }
    //            else -> super.onOptionsItemSelected(item)
    //        }
    //    }

    /**
     * If you set `android:configChanges="orientation|screenSize"` for activity on `AndroidManifest`, this method will be called.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        LogContext.log.i(tag, "onConfigurationChanged()")
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        LogContext.log.i(tag, "onDestroy()")
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

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
                if (event.action == MotionEvent.ACTION_DOWN && (x < focusView.left || x > focusView.right || y < focusView.top || y > focusView.bottom)) {
                    closeSoftKeyboard()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ret
    }
}