package com.leovp.demo.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.system.KeepAlive
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

class KeepAliveActivity : BaseDemonstrationActivity() {
    override fun getTagName(): String = ITAG

    private val keepAlive: KeepAlive by lazy {
        KeepAlive(application, R.raw.single_note30, 0.05f) {
            if (LogContext.enableLog) LogContext.log.i("KeepAliveActivity Time up!!!")
            toast("KeepAliveActivity Time up!!!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keep_alive)
    }

    fun onKeepAliveClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("Start KeepAlive")
        keepAlive.keepAlive()
    }

    fun onStopClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("Stop KeepAlive")
        keepAlive.release()
    }
}