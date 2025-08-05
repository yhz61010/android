package com.leovp.demo.base

import android.content.Context
import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.leovp.android.utils.LangUtil
import com.leovp.androidbase.framework.BaseActivity
import com.leovp.log.LogContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Author: Michael Leo
 * Date: 20-6-17 上午11:14
 */
abstract class BaseDemonstrationActivity<B : ViewBinding>(
    @LayoutRes layoutResId: Int = 0,
    init: (ActivityConfig.() -> Unit)? = null
) : BaseActivity<B>(layoutResId, init) {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LangUtil.getInstance(base).setAppLanguage(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogContext.log.i(tag, "onCreate()")
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
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

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    class LangChangeEvent

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLangChangedEvent(@Suppress("UNUSED_PARAMETER") event: LangChangeEvent) {
        recreate()
    }
}
