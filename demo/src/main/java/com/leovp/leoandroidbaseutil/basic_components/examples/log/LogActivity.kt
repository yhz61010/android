package com.leovp.leoandroidbaseutil.basic_components.examples.log

import android.os.Bundle
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.utils.device.DeviceUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity

/**
 * CLog is the wrapper of XlogCLog is the wrapper of Xlog
 * Before using it, you must initialize it in `Application` by calling:
 * ```kotlin
 * LogContext.setLogImp(CLog().apply { init(this@CustomApplication) })
 * ```
 *
 * After CLog initializing, the usage of CLog is just the same as LogContext.log.
 * ```kotlin
 * LogContext.log.w(ITAG, "Device Info:\n${DeviceUtil.getDeviceInfo(this)}")
 * ```
 *
 * It's better to call
 * ```kotlin
 * (LogContext.log as CLog).flushLog()
 * ```
 * in `onStop()` to make sure flush all memory logs into file.
 *
 * When you no need to use CLog, please close it.
 * ```kotlin
 * (LogContext.log as CLog).closeLog()
 * ```
 * Generally speaking, this method should be called before you exit app.
 *
 *
 * LLog is the wrapper of Android default log
 * No need to do any initializing before using it.
 */
class LogActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        LogContext.log.w(ITAG, "2Device Info:\n${DeviceUtil.getDeviceInfo(this)}")
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