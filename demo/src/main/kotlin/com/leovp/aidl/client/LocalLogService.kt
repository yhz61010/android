package com.leovp.aidl.client

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.leovp.aidl.client.model.LocalLog
import com.leovp.android.exts.toast
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
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
