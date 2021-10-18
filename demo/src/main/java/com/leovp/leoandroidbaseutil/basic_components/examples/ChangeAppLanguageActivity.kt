package com.leovp.leoandroidbaseutil.basic_components.examples

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leovp.androidbase.exts.android.setOnSingleClickListener
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.system.LangUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityChangeAppLanguageBinding
import com.leovp.log_sdk.LogContext

/**
 * Attention:
 * For Chinese language(Simplified Chinese, Traditional Chinese),
 * - If you set language in `zh_CN`, you should create `values-zh-rCN` folder in `values` folder.
 * - If you set language in `zh`, you should create `values-zh` folder in `values` folder.
 *
 * Add the following codes into your base activity:
 *
 * ```kotlin
 * private val appLangChangeReceiver = object : BroadcastReceiver() {
 *     override fun onReceive(context: Context, intent: Intent?) {
 *     recreate()
 *     }
 * }
 *
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     LocalBroadcastManager.getInstance(app).registerReceiver(appLangChangeReceiver, IntentFilter(LangUtil.INTENT_APP_LANG_CHANGE))
 *     LangUtil.changeAppLanguage(this@BaseDemonstrationActivity)
 * }
 *
 * override fun onDestroy() {
 *     LocalBroadcastManager.getInstance(app).unregisterReceiver(appLangChangeReceiver)
 *     super.onDestroy()
 * }
 * ```
 */
class ChangeAppLanguageActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityChangeAppLanguageBinding.inflate(layoutInflater).apply { setContentView(root) }
        val itemList = resources.getStringArray(R.array.lang_list)
        val itemCodeList = resources.getStringArray(R.array.lang_code_list)

        LogContext.log.i("Default language: ${LangUtil.getDefaultDisplayLanguage()}[${LangUtil.getDefaultLanguageCountryCode()}][${LangUtil.getDefaultLanguageFullCode()}]")
        LogContext.log.i("Device locale: ${LangUtil.getDeviceLocale()}[${LangUtil.getDeviceLanguageCountryCode()}]")
        LogContext.log.i("========================================================")

        binding.btnSelectLang.setOnSingleClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_lang)
                .setItems(itemList) { dlg, which ->
                    val langCode = itemCodeList[which]
                    LangUtil.saveLanguageAndRefreshUI(this@ChangeAppLanguageActivity, LangUtil.getLocale(langCode)!!)
                    dlg.dismiss()
                }
                .show()
        }

        binding.btnStartService.setOnSingleClickListener { startService(Intent(this@ChangeAppLanguageActivity, ChangeAppTestService::class.java)) }
    }
}

class ChangeAppTestService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LangUtil.setLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        toast(R.string.tv_i18n)
        stopSelf()
    }
}