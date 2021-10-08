package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityBluetoothBinding
import com.leovp.log_sdk.LogContext

/**
 *  Need following permissions:
 *
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <!-- Required only if your app isn't using the Device Companion Manager. -->
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 */
class BluetoothActivity : BaseDemonstrationActivity() {
    private var _binding: ActivityBluetoothBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityBluetoothBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }

        BluetoothUtil.enable()
    }

    override fun onResume() {
        super.onResume()

        var msg = ""
        val boundedDevices = BluetoothUtil.boundedDevices
        if (boundedDevices.isNotEmpty()) {
            boundedDevices.forEachIndexed { index, bluetoothDevice ->
                msg += "${index + 1}: ${bluetoothDevice.name}|${bluetoothDevice.address}\n"
            }
        }
        binding.tvInfo.text = msg
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
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