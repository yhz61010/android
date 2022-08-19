package com.leovp.demo.basic_components.examples.aidl

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.leovp.lib_common_android.exts.toast
import com.leovp.demo.basic_components.examples.aidl.model.LocalLog
import com.leovp.lib_json.toJsonString
import com.leovp.log_sdk.LogContext
import kotlinx.coroutines.DelicateCoroutinesApi
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

        @DelicateCoroutinesApi
        override fun writeLog(appPackage: String, log: LocalLog) {
            GlobalScope.launch {
                LogContext.log.w("Write log: [$appPackage]=${log.toJsonString()}")
                toast("Write log: [$appPackage]=${log.toJsonString()}")
            }
        }
    }
}
