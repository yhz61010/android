package com.leovp.leoandroidbaseutil.basic_components.examples.log

import android.os.Bundle
import android.util.Log
import com.leovp.androidbase.utils.device.DeviceUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

/**
 * Check the [LogContext] documents to learn how to initialize your custom log wrapper.
 *
 * After initializing, the usage is very simple:
 * ```kotlin
 * LogContext.log.w(ITAG, "Device Info:\n${DeviceUtil.getDeviceInfo(this)}")
 * ```
 */
class LogActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        LogContext.log.v("Hello v", outputType = 1)
        LogContext.log.d("Hello d", outputType = 2)
        LogContext.log.i("Hello i", outputType = 3)
        LogContext.log.w("Hello w", outputType = 4)
        LogContext.log.e("Hello e with fullOutput", fullOutput = true, outputType = 5)
        LogContext.log.f("Hello f", outputType = 6)

        LogContext.log.v(TAG, "Hello v", Exception("exception-v"), outputType = 7)
        LogContext.log.d(TAG, "Hello d", Exception("exception-d"), outputType = 8)
        LogContext.log.i(TAG, "Hello i", Exception("exception-i"), outputType = 9)
        LogContext.log.w(TAG, "Hello w", Exception("exception-w"), outputType = 10)
        LogContext.log.e(TAG, "Hello e", Exception("exception-e"), outputType = 11)
        LogContext.log.f(TAG, "Hello f", Exception("exception-f"), outputType = 12)

        LogContext.log.w(ITAG, "2Device Info:\n${DeviceUtil.getInstance(this).getDeviceInfo()}", outputType = 13)

        LogContext.enableLog = false
        LogContext.log.w(ITAG, "This log will NOT be outputted", outputType = 14)

        LogContext.enableLog = true
        LogContext.log.w(ITAG, "This log will be outputted", outputType = 15)

        val sb = StringBuilder()
        for (i in 0 until 1000) {
            sb.append("[$i]")
            sb.append(System.nanoTime())
            sb.append(" | ")
        }
        val string = sb.toString()
        LogContext.log.w(TAG, "Long Log[${string.length}][truncated]=$string", outputType = 16)
        LogContext.log.w(TAG, "Long Log[${string.length}][full]=$string", fullOutput = true, outputType = 17)

        if (LogContext.isLogInitialized()) {
            Log.w(TAG, "Log is initialized.")
        } else {
            Log.e(TAG, "Log is NOT initialized.")
        }
    }

    override fun onStop() {
//        (LogContext.log as CLog).flushLog()
        super.onStop()
    }

    override fun onDestroy() {
        LogContext.log.w(ITAG, "=====> onDestroy <=====")
//        (LogContext.log as CLog).closeLog()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LogTest"
    }
}