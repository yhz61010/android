package com.leovp.leoandroidbaseutil.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.system.LangUtil

/**
 * Author: Michael Leo
 * Date: 20-6-17 上午11:14
 */
open class BaseDemonstrationActivity : AppCompatActivity() {
    private val appLangChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            LangUtil.changeAppLanguage(this@BaseDemonstrationActivity)
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogContext.log.i("onCreate()")
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra("title")
        LocalBroadcastManager.getInstance(app).registerReceiver(appLangChangeReceiver, IntentFilter(LangUtil.INTENT_APP_LANG_CHANGE))
        val lang = LangUtil.getAppLanguage()
        LogContext.log.i("Pref lang=$lang")
        LangUtil.changeAppLanguage(this@BaseDemonstrationActivity)
    }

    /**
     * If you set `android:configChanges="orientation|screenSize"` for activity on `AndroidManifest`, this method will be called.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        LogContext.log.i("onConfigurationChanged()")
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        LogContext.log.i("onDestroy()")
        LocalBroadcastManager.getInstance(app).unregisterReceiver(appLangChangeReceiver)
        super.onDestroy()
    }
}