package com.leovp.demo.basiccomponents.examples

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import com.leovp.android.exts.LeoToast
import com.leovp.android.exts.cancelToast
import com.leovp.android.exts.toast
import com.leovp.androidbase.exts.android.getMetaData
import com.leovp.demo.BuildConfig
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityToastBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

class ToastActivity : BaseDemonstrationActivity<ActivityToastBinding>(R.layout.activity_toast) {
    override fun getTagName(): String = ITAG

    private lateinit var serviceIntent: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Handler(Looper.getMainLooper()).postDelayed({
            toast("Custom toast in background test.", error = true)
        }, 2000)

        serviceIntent = Intent(this, TestService::class.java)
        startService(serviceIntent)
    }

    override fun onDestroy() {
        stopService(serviceIntent)
        super.onDestroy()
    }

    class TestService : Service() {
        override fun onBind(intent: Intent): IBinder? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        override fun onCreate() {
            super.onCreate()
            LogContext.log.e(ITAG, "=====>>>>> TestService created <<<<<=====")
            val metaData: String? = getMetaData("service_meta_data")
            LogContext.log.e(ITAG, "metaData=$metaData")
            mainHandler.post {
                toast("TestService created.")
                LogContext.log.e(ITAG, "=> TestService created")
            }
        }
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityToastBinding =
        ActivityToastBinding.inflate(layoutInflater)

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
        LeoToast.getInstance(this).init(
            LeoToast.ToastConfig(
                buildConfigInDebug = BuildConfig.DEBUG,
                toastIcon = R.mipmap.ic_launcher_round,
                textSize = 24f,
                layout = R.layout.custom_toast_layout,
                gravity = Gravity.CENTER
            )
        )
        toast("This is custom error color toast.", bgColor = "#711CDE", error = true)
    }

    fun onCustomToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("This is custom color toast.", bgColor = "#FF00FF")
    }

    fun onDebugToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast(
            "I have a dream. A song to sing. " +
                "To help me cope with anything. If you see the wonder.",
            debug = true,
            error = true
        )
    }

    fun onCancelToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cancelToast()
    }
}
