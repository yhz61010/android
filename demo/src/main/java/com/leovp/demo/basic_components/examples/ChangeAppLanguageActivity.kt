package com.leovp.demo.basic_components.examples

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leovp.androidbase.exts.android.toast
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityChangeAppLanguageBinding
import com.leovp.lib_common_android.exts.setOnSingleClickListener
import com.leovp.lib_common_android.utils.LangUtil
import com.leovp.lib_json.toJsonString
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import org.greenrobot.eventbus.EventBus

/**
 * Attention:
 * For Chinese language(Simplified Chinese, Traditional Chinese),
 * - If you set language in `zh_CN`, you should create `values-zh-rCN` folder in `values` folder.
 * - If you set language in `zh`, you should create `values-zh` folder in `values` folder.
 */
class ChangeAppLanguageActivity : BaseDemonstrationActivity() {
    override fun getTagName(): String = ITAG

    private val langUtil: LangUtil by lazy { LangUtil.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityChangeAppLanguageBinding.inflate(layoutInflater).apply { setContentView(root) }
        val itemList = resources.getStringArray(R.array.lang_list)
        LogContext.log.w("itemList=${itemList.toJsonString()}")
        val itemCodeList = resources.getStringArray(R.array.lang_code_list)

        LogContext.log.i("Default language: ${langUtil.getDefaultDisplayLanguage()}[${langUtil.getDefaultLanguageCountryCode()}][${langUtil.getDefaultLanguageFullCode()}]")
        LogContext.log.i("Device locale: ${langUtil.getDeviceLocale()}[${langUtil.getDeviceLanguageCountryCode()}]")
        LogContext.log.i("========================================================")

        binding.btnSelectLang.setOnSingleClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_lang)
                .setItems(itemList) { dlg, which ->
                    val langCode = itemCodeList[which]
                    LogContext.log.e(ITAG, "=====> MaterialAlertDialogBuilder setLocale()")
                    langUtil.setLocale(this@ChangeAppLanguageActivity.applicationContext, langUtil.getLocale(langCode)!!, refreshUI = true) { refreshUi ->
                        if (refreshUi) EventBus.getDefault().post(LangChangeEvent())
                    }
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
        LogContext.log.e(ITAG, "=====> ChangeAppTestService setLocale()")
        super.attachBaseContext(LangUtil.getInstance(base).setLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        toast(R.string.tv_i18n)
        stopSelf()
    }
}