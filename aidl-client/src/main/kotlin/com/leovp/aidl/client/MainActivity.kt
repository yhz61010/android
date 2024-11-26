package com.leovp.aidl.client

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.leovp.aidl.client.model.LocalLog
import com.leovp.android.exts.LeoToast
import com.leovp.android.exts.toast
import com.leovp.log.LLog
import com.leovp.log.LogContext
import com.leovp.log.base.AbsLog
import com.leovp.log.base.ITAG

class MainActivity : AppCompatActivity() {
    private var remoteService: ILocalLogService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        LeoToast.getInstance(this).init(LeoToast.ToastConfig(BuildConfig.DEBUG, R.mipmap.ic_launcher_round))

        LogContext.setLogImpl(
            LLog(tagPrefix = "LEO-AIDL-CLIENT", enableLog = true, logLevel = AbsLog.LogLevel.VERB)
        )

        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                LogContext.log.i(ITAG, "onServiceConnected")
                remoteService = ILocalLogService.Stub.asInterface(service)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                LogContext.log.i(ITAG, "onServiceDisconnected")
                remoteService = null
            }
        }

        bindService(serviceConnection)
    }

    private fun bindService(serviceConnection: ServiceConnection) {
        // Intent action is configured in AndroidManifest.xml
        val intent = Intent("service.name")
        // It's necessary on Android 5.0+
        intent.setPackage("com.leovp.demo.dev")
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    fun onSendClick(@Suppress("unused") view: View) {
        remoteService?.writeLog(packageName, LocalLog("INFO", "Message from client side."))
    }

    fun onReceiveClick(@Suppress("unused") view: View) {
        toast("Get from remote service: ${remoteService?.getLogCount("dummy value")}")
    }
}
