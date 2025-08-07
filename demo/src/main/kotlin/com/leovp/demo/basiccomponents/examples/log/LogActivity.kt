package com.leovp.demo.basiccomponents.examples.log

import android.os.Bundle
import android.util.Log
import com.leovp.android.utils.DeviceUtil
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityLogBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.log.base.LogOutType
import com.leovp.log.base.w

/**
 * Check the [LogContext] documents to learn how to initialize your custom log wrapper.
 *
 * After initializing, the usage is very simple:
 * ```kotlin
 * LogContext.log.w(ITAG, "Device Info:\n${DeviceUtil.getDeviceInfo(this)}")
 * ```
 */
class LogActivity : BaseDemonstrationActivity<ActivityLogBinding>(R.layout.activity_log) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityLogBinding =
        ActivityLogBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LogContext.log.v(TAG, "Hello v", outputType = LogOutType(1))
        LogContext.log.d(TAG, "Hello d", outputType = LogOutType(2))
        LogContext.log.i(TAG, "Hello i", outputType = LogOutType(3))
        LogContext.log.w(TAG, "Hello w", outputType = LogOutType(4))
        LogContext.log.e(TAG, "Hello e with fullOutput", fullOutput = true, outputType = LogOutType(5))
        LogContext.log.f(TAG, "Hello f", outputType = LogOutType(6))

        LogContext.log.v(TAG, "Hello v", Exception("exception-v"), outputType = LogOutType(7))
        LogContext.log.d(TAG, "Hello d", Exception("exception-d"), outputType = LogOutType(8))
        LogContext.log.i(TAG, "Hello i", Exception("exception-i"), outputType = LogOutType(9))
        LogContext.log.w(TAG, "Hello w", Exception("exception-w"), outputType = LogOutType(10))
        LogContext.log.e(TAG, "Hello e", Exception("exception-e"), outputType = LogOutType(11))
        LogContext.log.f(TAG, "Hello f", Exception("exception-f"), outputType = LogOutType(12))

        w {
            tag = TAG
            outputType = LogOutType(13)
            message = "2Device Info:\n${DeviceUtil.getInstance(this@LogActivity).getDeviceInfo()}"
        }

        val sb = StringBuilder()
        for (i in 0 until 1000) {
            sb.append("[$i]")
            sb.append(System.nanoTime())
            sb.append(" | ")
        }
        val string = sb.toString()
        LogContext.log.w(TAG, "Long Log[${string.length}][truncated]=$string", outputType = LogOutType(16))
        LogContext.log.w(
            TAG,
            "Long Log[${string.length}][full]=$string",
            fullOutput = true,
            outputType = LogOutType(17)
        )

        if (LogContext.isLogInitialized()) {
            Log.w(TAG, "Log is initialized.")
        } else {
            Log.e(TAG, "Log is NOT initialized.")
        }
    }

    //    override fun onStop() {
    // //        (LogContext.log as CLog).flushLog()
    //        super.onStop()
    //    }

    override fun onDestroy() {
        LogContext.log.w(ITAG, "=====> onDestroy <=====")
        //        (LogContext.log as CLog).closeLog()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LogTest"
    }
}
