package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity

/**
 *  Need following permissions:
 *
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <!--  For get nearby devices, need location permission when above android M  -->
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 */
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
        startActivity(BluetoothScanActivity::class)
    }

    fun onGotoServerSideClick(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(BluetoothServerActivity::class)
    }
}