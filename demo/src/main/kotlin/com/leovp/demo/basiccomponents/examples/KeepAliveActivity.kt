package com.leovp.demo.basiccomponents.examples

import android.os.Bundle
import android.view.View
import com.leovp.android.exts.toast
import com.leovp.androidbase.utils.system.KeepAlive
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityKeepAliveBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

class KeepAliveActivity : BaseDemonstrationActivity<ActivityKeepAliveBinding>(R.layout.activity_keep_alive) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityKeepAliveBinding {
        return ActivityKeepAliveBinding.inflate(layoutInflater)
    }

    private val keepAlive: KeepAlive by lazy {
        KeepAlive(application, R.raw.single_note30, 0.05f) {
            if (LogContext.enableLog) LogContext.log.i("KeepAliveActivity Time up!!!")
            toast("KeepAliveActivity Time up!!!")
        }
    }

    fun onKeepAliveClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("Start KeepAlive")
        keepAlive.start()
    }

    fun onStopClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("Stop KeepAlive")
        keepAlive.release()
    }
}
