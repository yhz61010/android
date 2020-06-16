package com.ho1ho.leoandroidbaseutil

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.AppUtil
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.androidbase.utils.network.NetworkMonitor
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtView.text = DeviceUtil.getDeviceInfo(this)
        CLog.w(ITAG, "1Device Info:\n${DeviceUtil.getDeviceInfo(this)}")
//        LLog.w(ITAG, "2Device Info:\n${DeviceUtil.getDeviceInfo(this)}")

        networkMonitor = NetworkMonitor(this, "220.181.38.148")
        networkMonitor.startMonitor(3)
    }

    override fun onStop() {
        CLog.flushLog(false)
        super.onStop()
    }

    override fun onDestroy() {
        networkMonitor.stopMonitor()
        CLog.closeLog()
        super.onDestroy()
        AppUtil.exitApp(this)
    }
}
