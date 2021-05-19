package com.leovp.leoandroidbaseutil.basic_components.examples

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.system.KeepAlive
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity

class KeepAliveActivity : BaseDemonstrationActivity() {

    class KeepAliveReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (LogContext.enableLog) LogContext.log.i("Time up! Time up! Time up!")
            toast("Time up")
        }
    }

    private val keepAlive: KeepAlive by lazy { KeepAlive(this, R.raw.single_note30, 0.1f, KeepAliveReceiver()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keep_alive)
    }

    fun onKeepAliveClick(view: View) {
        keepAlive.keepAlive()
    }

    fun onStopClick(view: View) {
        keepAlive.release()
    }
}