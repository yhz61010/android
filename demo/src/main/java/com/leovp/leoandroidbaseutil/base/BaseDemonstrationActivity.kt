package com.leovp.leoandroidbaseutil.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.system.LangUtil
import com.leovp.androidbase.utils.ui.BetterActivityResult
import com.leovp.log_sdk.LogContext

/**
 * Author: Michael Leo
 * Date: 20-6-17 上午11:14
 */
open class BaseDemonstrationActivity : AppCompatActivity() {
    lateinit var simpleActivityLauncher: BetterActivityResult<Intent, ActivityResult>

    private val appLangChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            recreate()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LangUtil.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogContext.log.i("onCreate()")
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = intent.getStringExtra("title")
        simpleActivityLauncher = BetterActivityResult.registerForActivityResult(this, ActivityResultContracts.StartActivityForResult()) { result ->
            toast("Result in BaseActivity: ${result.resultCode}")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(appLangChangeReceiver, IntentFilter(LangUtil.INTENT_APP_LANG_CHANGE))
        val lang = LangUtil.getAppLanguage(this)
        LogContext.log.i("Pref lang=$lang")
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
        LogContext.log.i("onConfigurationChanged()")
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        LogContext.log.i("onDestroy()")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(appLangChangeReceiver)
        super.onDestroy()
    }
}