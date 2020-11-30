package com.leovp.leoandroidbaseutil.basic_components.examples.log

import android.os.Bundle
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.utils.device.DeviceUtil
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

        LogContext.log.w(ITAG, "2Device Info:\n${DeviceUtil.getDeviceInfo()}")
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
}