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

        LogContext.log.w(ITAG, "2Device Info:\n${DeviceUtil.getDeviceInfo(this)}", outputType = 13)

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