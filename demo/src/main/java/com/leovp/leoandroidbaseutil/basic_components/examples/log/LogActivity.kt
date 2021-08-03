package com.leovp.leoandroidbaseutil.basic_components.examples.log

import android.os.Bundle
import com.leovp.androidbase.exts.android.utils.DeviceUtil
import com.leovp.androidbase.exts.kotlin.ITAG
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity

/**
 * You can implement your log wrapper by implement `ILog` or else the default log wrapper `LLog` will be used.
 * `LLog` is the wrapper of Android default log
 *
 * After implementing your log wrapper, you must initialize it in `Application` by calling:
 * Assume that `CLog` is your custom log wrapper.
 * ```kotlin
 * LogContext.setLogImp(CLog().apply { init(this@CustomApplication) })
 * ```
 * The default implementation is like this below:
 * ```kotlin
 * LogContext.setLogImp(LLog())
 * ```
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

        LogContext.log.v("Hello v")
        LogContext.log.d("Hello d")
        LogContext.log.i("Hello i")
        LogContext.log.w("Hello w")
        LogContext.log.e("Hello e with fullOutput", fullOutput = true)
        LogContext.log.f("Hello f")

        LogContext.log.v(TAG, "Hello v")
        LogContext.log.d(TAG, "Hello d")
        LogContext.log.i(TAG, "Hello i")
        LogContext.log.w(TAG, "Hello w")
        LogContext.log.e(TAG, "Hello e")
        LogContext.log.f(TAG, "Hello f")

        LogContext.log.v(TAG, "Hello v", Exception("exception-v"))
        LogContext.log.d(TAG, "Hello d", Exception("exception-d"))
        LogContext.log.i(TAG, "Hello i", Exception("exception-i"))
        LogContext.log.w(TAG, "Hello w", Exception("exception-w"))
        LogContext.log.e(TAG, "Hello e", Exception("exception-e"))
        LogContext.log.f(TAG, "Hello f", Exception("exception-f"))

        LogContext.log.w(ITAG, "2Device Info:\n${DeviceUtil.getDeviceInfo(this)}")

        LogContext.enableLog = false
        LogContext.log.w(ITAG, "This log will NOT be outputted")

        LogContext.enableLog = true
        LogContext.log.w(ITAG, "This log will be outputted")


        val sb = StringBuilder()
        for (i in 0 until 1000) {
            sb.append("[$i]")
            sb.append(System.nanoTime())
            sb.append(" | ")
        }
        val string = sb.toString()
        LogContext.log.w(TAG, "Long Log[${string.length}][truncated]=$string")
        LogContext.log.w(TAG, "Long Log[${string.length}][full]=$string", fullOutput = true)
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