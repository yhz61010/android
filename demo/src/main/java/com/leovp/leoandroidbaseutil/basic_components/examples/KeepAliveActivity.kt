package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.utils.system.KeepAlive
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity

class KeepAliveActivity : BaseDemonstrationActivity() {

    private val keepAlive: KeepAlive by lazy { KeepAlive(this, R.raw.single_note30) }

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