package com.leovp.demo.basic_components.examples

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityToastBinding
import com.leovp.lib_common_android.exts.cancelToast
import com.leovp.lib_common_android.exts.initForegroundComponentForToast
import com.leovp.lib_common_android.exts.toast
import com.leovp.lib_common_android.exts.toastIcon
import com.leovp.log_sdk.base.ITAG

class ToastActivity : BaseDemonstrationActivity<ActivityToastBinding>() {
    override fun getTagName(): String = ITAG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initForegroundComponentForToast(application)
        toastIcon = R.mipmap.ic_launcher_round

        Handler(Looper.getMainLooper()).postDelayed({
            toast("Custom toast in background test.", error = true)
        }, 2000)
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityToastBinding {
        return ActivityToastBinding.inflate(layoutInflater)
    }

    fun onAndroidToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("This is Android toast.", origin = true)
    }

    fun onNormalToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("This is normal toast.")
    }

    fun onErrorToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("This is error toast.", error = true)
    }

    fun onCustomErrorColorToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("This is custom error color toast.", bgColor = "#711CDE", error = true)
    }

    fun onCustomToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("This is custom color toast.", bgColor = "#FF00FF")
    }

    fun onDebugToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("Debug toast.", debug = true, error = true)
    }

    fun onCancelToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cancelToast()
    }
}