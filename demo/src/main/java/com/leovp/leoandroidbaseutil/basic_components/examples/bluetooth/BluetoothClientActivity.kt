package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.bluetooth.*
import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityBluetoothClientBinding

class BluetoothClientActivity : BaseDemonstrationActivity() {

    private var _binding: ActivityBluetoothClientBinding? = null
    private val binding get() = _binding!!

    private var device: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityBluetoothClientBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }

        initView()
        initData()
    }

    private fun initView() {
        title = "Bluetooth Client"
    }

    private fun initData() {
        device = intent.getParcelableExtra("device")
        bluetoothGatt = device!!.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        LogContext.log.w("STATE_CONNECTED")
                        gatt!!.discoverServices()
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        LogContext.log.w("STATE_CONNECTING")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        LogContext.log.w("STATE_DISCONNECTED")
                    }
                    BluetoothProfile.STATE_DISCONNECTING -> {
                        LogContext.log.w("STATE_DISCONNECTING")
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                LogContext.log.w("onServicesDiscovered status=$status")
                super.onServicesDiscovered(gatt, status)
                //设置读特征值的监听，接收服务端发送的数据
                val service = bluetoothGatt!!.getService(BluetoothServerActivity.UUID_SERVER)
                val characteristic = service.getCharacteristic(BluetoothServerActivity.UUID_CHAR_READ)
                val b = bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
                LogContext.log.w("setCharacteristicNotification b=$b")
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)
                val data = String(characteristic.value)
                LogContext.log.w("onCharacteristicChanged characteristic=${characteristic.toJsonString()} data=$data")
                toast("Received msg=$data")
            }
        })
    }

    fun onSendClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val msg = binding.etMsg.text.toString()
        sendData(msg)
        binding.etMsg.setText("")
    }

    private fun sendData(msg: String) {
        if (bluetoothGatt == null) {
            return
        }
        //找到服务
        val service = bluetoothGatt!!.getService(BluetoothServerActivity.UUID_SERVER) ?: return
        //拿到写的特征值
        val characteristic = service.getCharacteristic(BluetoothServerActivity.UUID_CHAR_WRITE)
        bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
        characteristic.value = msg.toByteArray()
        bluetoothGatt!!.writeCharacteristic(characteristic)
        LogContext.log.w("Send message to server=$msg")
    }
}