package com.leovp.demo.basiccomponents.examples

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.android.exts.toast
import com.leovp.android.utils.LangUtil
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityChangeAppLanguageBinding
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import org.greenrobot.eventbus.EventBus

/**
 * Attention:
 * For Chinese language(Simplified Chinese, Traditional Chinese),
 * - If you set language in `zh_CN`, you should create `values-zh-rCN` folder in `values` folder.
 * - If you set language in `zh`, you should create `values-zh` folder in `values` folder.
 */
class ChangeAppLanguageActivity : BaseDemonstrationActivity<ActivityChangeAppLanguageBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityChangeAppLanguageBinding {
        return ActivityChangeAppLanguageBinding.inflate(layoutInflater)
    }

    private val langUtil: LangUtil by lazy { LangUtil.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.act_title_change_app_lang)
        val itemList = resources.getStringArray(R.array.lang_list)
        LogContext.log.w("itemList=${itemList.toJsonString()}")
        val itemCodeList = resources.getStringArray(R.array.lang_code_list)

        LogContext.log.i(
            "Default language: ${langUtil.getDefaultDisplayLanguage()}" +
                "[${langUtil.getDefaultLanguageCountryCode()}][${langUtil.getDefaultLanguageFullCode()}]"
        )
        LogContext.log.i("Device locale: ${langUtil.getDeviceLocale()}[${langUtil.getDeviceLanguageCountryCode()}]")
        LogContext.log.i("========================================================")

        binding.btnSelectLang.setOnSingleClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_lang)
                .setItems(itemList) { dlg, which ->
                    val langCode = itemCodeList[which]
                    LogContext.log.e(ITAG, "=====> MaterialAlertDialogBuilder setLocale()")
                    langUtil.setAppLanguage(
                        this@ChangeAppLanguageActivity.applicationContext,
                        langUtil.getLocale(langCode)!!,
                        refreshUI = true
                    ) { refreshUi ->
                        if (refreshUi) EventBus.getDefault().post(LangChangeEvent())
                    }
                    dlg.dismiss()
                }
                .show()
        }

        binding.btnStartService.setOnSingleClickListener {
            startService(
                Intent(this@ChangeAppLanguageActivity, ChangeAppTestService::class.java)
            )
        }
    }
}

class ChangeAppTestService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(base: Context) {
        LogContext.log.e(ITAG, "=====> ChangeAppTestService setLocale()")
        super.attachBaseContext(LangUtil.getInstance(base).setAppLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()
        toast(R.string.tv_i18n)
        stopSelf()
    }
}
