package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leovp.androidbase.exts.android.setOnSingleClickListener
import com.leovp.androidbase.utils.system.LangUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityChangeAppLanguageBinding

/**
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

        binding.btnSelectLang.setOnSingleClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_lang)
                .setItems(itemList) { dlg, which ->
                    val langCode = itemCodeList[which]
                    LangUtil.saveLanguageAndRefreshUI(LangUtil.getLocale(langCode)!!)
                    dlg.dismiss()
                }
                .show()
        }
    }
}