package com.leovp.leoandroidbaseutil.basic_components.examples.aidl

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.basic_components.examples.aidl.model.LocalLog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LocalLogService : Service() {

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private val binder = object : ILocalLogService.Stub() {
        override fun getLogCount(app: String): Int {
//            ThreadLocalRandom.current()
            return (0..100).random()
        }

        override fun writeLog(appPackage: String, log: LocalLog) {
            GlobalScope.launch {
                LogContext.log.w("Write log: [$appPackage]=${log.toJsonString()}")
                toast("Write log: [$appPackage]=${log.toJsonString()}")
            }
        }
    }
}