package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.leoandroidbaseutil.R
import kotlinx.android.synthetic.main.activity_device_info.*

class DeviceInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)

        title = intent.getStringExtra("title")
        tv.text = DeviceUtil.getDeviceInfo(this)
    }
}