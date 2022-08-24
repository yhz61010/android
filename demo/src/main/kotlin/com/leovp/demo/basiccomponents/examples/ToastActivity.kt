package com.leovp.demo.basiccomponents.examples

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityToastBinding
import com.leovp.android.exts.cancelToast
import com.leovp.android.exts.toast
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

class ToastActivity : BaseDemonstrationActivity<ActivityToastBinding>() {
    override fun getTagName(): String = ITAG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Handler(Looper.getMainLooper()).postDelayed({
            toast("Custom toast in background test.", error = true)
        }, 2000)

        startService(Intent(this, TestService::class.java))
    }

    class TestService : Service() {
        override fun onBind(intent: Intent): IBinder? = null

        override fun onCreate() {
            super.onCreate()
            LogContext.log.e(ITAG, "=====>>>>> TestService created <<<<<=====")
            toast("TestService created.")
        }
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityToastBinding {
        return ActivityToastBinding.inflate(layoutInflater)
    }

    fun onAndroidToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("DEBUG: onClientDisconnected: 127.0.0.1:42542", origin = true)
    }

    fun onNormalToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("DEBUG: onClientDisconnected: 127.0.0.1:42542")
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
        toast(
            "I have a dream. A song to sing. " +
                "To help me cope with anything. If you see the wonder.",
            debug = true, error = true
        )
    }

    fun onCancelToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cancelToast()
    }
}
