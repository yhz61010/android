package com.leovp.demo.base

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.leovp.android.utils.LangUtil
import com.leovp.androidbase.framework.BaseActivity
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 20-6-17 上午11:14
 */
abstract class BaseDemonstrationActivity<B : ViewBinding>(
    @LayoutRes layoutResId: Int = 0,
    init: (ActivityConfig.() -> Unit)? = null
) : BaseActivity<B>(layoutResId, init) {

    override fun onCreate(savedInstanceState: Bundle?) {
        LogContext.log.i(tag, "onCreate()")
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra("title")
        //        simpleActivityLauncher = BetterActivityResult.registerForActivityResult(this, ActivityResultContracts.StartActivityForResult()) { result ->
        //            toast("Result in BaseActivity: ${result.resultCode}")
        //        }
        val lang = LangUtil.getInstance(this).getAppLanguage()
        LogContext.log.i(tag, "Pref lang=$lang")
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
}
