package com.leovp.aidl.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.leovp.aidl.client.model.LocalLog
import com.leovp.android.exts.LeoToast
import com.leovp.android.exts.ToastConfig
import com.leovp.android.exts.toast
import com.leovp.log.LLog
import com.leovp.log.LogContext

class MainActivity : AppCompatActivity() {
    private var remoteService: ILocalLogService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        LeoToast.getInstance(this).apply {
            config = ToastConfig(BuildConfig.DEBUG, R.mipmap.ic_launcher_round)
            initForegroundComponentForToast(application)
        }

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
        intent.setPackage("com.leovp.demo.dev")
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun onSendClick(@Suppress("UNUSED_PARAMETER") view: View) {
        remoteService?.writeLog(packageName, LocalLog("INFO", "Message from client side."))
    }

    fun onReceiveClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("Get from remote service: ${remoteService?.getLogCount("dummy value")}")
    }
}
