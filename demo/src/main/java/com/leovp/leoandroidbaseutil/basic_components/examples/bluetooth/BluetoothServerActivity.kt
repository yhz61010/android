package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityBluetoothServerBinding
import java.util.*

class BluetoothServerActivity : BaseDemonstrationActivity() {
    companion object {
        //服务uuid
        var UUID_SERVER = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

        //读的特征值¸
        var UUID_CHAR_READ = UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb")

        //写的特征值
        var UUID_CHAR_WRITE = UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb")
    }

    private var _binding: ActivityBluetoothServerBinding? = null
    private val binding get() = _binding!!

    private val connectedDevice: BluetoothDevice? = null
    private val characteristicRead: BluetoothGattCharacteristic? = null
    private val bluetoothGattServer: BluetoothGattServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityBluetoothServerBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }

        initView()
        initBleServer()
    }

    private fun initView() {
        title = "Bluetooth Server"
    }

    private fun initBleServer() {
        if (!BluetoothUtil.isSupportBle()) {
            toast("Does not support bluetooth!")
            finish()
            return
        }
        BluetoothUtil.enable()
    }

    fun onSendClick(@Suppress("UNUSED_PARAMETER") view: View) {

    }
}