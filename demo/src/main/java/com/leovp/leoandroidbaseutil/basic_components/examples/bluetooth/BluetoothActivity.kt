package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity

class BluetoothActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)
        BluetoothUtil.enable()
    }

    fun onEnableBluetooth(@Suppress("UNUSED_PARAMETER") view: View) {
        val enable = BluetoothUtil.enable()
        LogContext.log.i("Enable=$enable")
        toast("Enable=$enable")
    }

    fun onDisableBluetooth(@Suppress("UNUSED_PARAMETER") view: View) {
        val disable = BluetoothUtil.disable()
        LogContext.log.i("Disable=$disable")
        toast("Disable=$disable")
    }

    fun onScanClick(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(ScanBluetoothActivity::class)
    }
}