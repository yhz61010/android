package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leovp.androidbase.exts.android.setOnSingleClickListener
import com.leovp.androidbase.utils.system.LangUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityChangeAppLanguageBinding

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