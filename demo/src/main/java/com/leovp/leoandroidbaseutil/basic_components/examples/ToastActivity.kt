package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity

class ToastActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_toast)
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
}