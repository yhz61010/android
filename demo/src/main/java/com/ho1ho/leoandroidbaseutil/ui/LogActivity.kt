package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity

class LogActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        // CLog is the wrapper of Xlog
        // Before using it, you must initialize it by calling CLog.init(ctx) in Application
        // Check CustomApplication
        CLog.w(ITAG, "1Device Info:\n${DeviceUtil.getDeviceInfo(this)}")

        // LLog is the wrapper of Android default log
        // No need to do any initializing before using it
        LLog.w(ITAG, "2Device Info:\n${DeviceUtil.getDeviceInfo(this)}")
    }

    override fun onStop() {
        CLog.flushLog(false)
        super.onStop()
    }

    override fun onDestroy() {
        CLog.w(ITAG, "=====> onDestroy <=====")
        // When you no need to use CLog, please close it.
        // Generally speaking, this method should be called before you exit app.
        // As demonstration, we do not close it here. Instead, we call it in MainActivity.
//        CLog.closeLog()
        super.onDestroy()
    }
}