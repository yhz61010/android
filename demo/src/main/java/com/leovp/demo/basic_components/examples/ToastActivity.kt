package com.leovp.demo.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.cancelToast
import com.leovp.androidbase.exts.android.toast
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityToastBinding
import com.leovp.log_sdk.base.ITAG

class ToastActivity : BaseDemonstrationActivity<ActivityToastBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityToastBinding {
        return ActivityToastBinding.inflate(layoutInflater)
    }

    fun onNormalToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("This is normal toast.")
    }

    fun onErrorToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("This is error toast.", error = true)
    }

    fun onCustomErrorColorToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("This is custom error color toast.", error = true, errorColor = "#711CDE")
    }

    fun onCancelToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cancelToast()
    }
}