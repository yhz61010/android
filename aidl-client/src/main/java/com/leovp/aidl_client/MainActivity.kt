package com.leovp.aidl_client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.leovp.demo.basic_components.examples.aidl.ILocalLogService
import com.leovp.demo.basic_components.examples.aidl.model.LocalLog
import com.leovp.lib_common_android.exts.LeoToast
import com.leovp.lib_common_android.exts.toast
import com.leovp.log_sdk.LLog
import com.leovp.log_sdk.LogContext

class MainActivity : AppCompatActivity() {
    private var remoteService: ILocalLogService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        LogContext.setLogImp(LLog("LEO-AIDL-CLIENT"))

        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                LogContext.log.i("onServiceConnected")
                remoteService = ILocalLogService.Stub.asInterface(service)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                LogContext.log.i("onServiceDisconnected")
                remoteService = null
            }
        }

        bindService(serviceConnection)
    }

    override fun onDestroy() {
        LeoToast.getInstance(this).removeToastRotationWatcher()
        super.onDestroy()
    }

    private fun bindService(serviceConnection: ServiceConnection) {
        // Intent action is configured in AndroidManifest.xml
        val intent = Intent("service.name")
        // It's necessary on Android 5.0+
        intent.setPackage("com.leovp.demo")
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun onSendClick(@Suppress("UNUSED_PARAMETER") view: View) {
        remoteService?.writeLog(packageName, LocalLog("INFO", "Message from client side."))
    }

    fun onReceiveClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("Get from remote service: ${remoteService?.getLogCount("dummy value")}")
    }
}