package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import kotlinx.android.synthetic.main.activity_device_info.*

class DeviceInfoActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)

        val deviceInfo = DeviceUtil.getDeviceInfo(this)
        tv.text = deviceInfo
        LLog.i(TAG, deviceInfo)
    }

    companion object {
        private const val TAG = "DeviceInfo"
    }
}