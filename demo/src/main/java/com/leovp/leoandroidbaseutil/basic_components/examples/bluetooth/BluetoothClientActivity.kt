package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.bluetooth.*
import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityBluetoothClientBinding

/**
 * Steps:
 * 1. Acquire permissions
 * 2. Enable bluetooth
 * 3. Search devices
 * 4. Connect device
 * 5. Communicate
 *   5.1 Wait for the device to connect successfully
 *   5.2 Discovery service
 *   5.3 Get BluetoothGattCharacteristic
 *   5.4 Enable monitor
 *   5.5 Read & Write data
 * 6. Disconnect
 *
 *  Need following permissions:
 *
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <!--  For get nearby devices, need location permission when above android M  -->
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 *
 * @see [BLE_Develop](https://www.jianshu.com/p/a27f3ca027e3)
 * @see [FAQ](https://www.jianshu.com/p/71116665fd08)
 */
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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        disconnect()
    }

    private fun disconnect() {
        bluetoothGatt?.run {
            disconnect()
            close()
        }
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
                // Set characteristics
                val service = bluetoothGatt!!.getService(BluetoothServerActivity.SERVICE_UUID)
                val characteristic = service.getCharacteristic(BluetoothServerActivity.CHARACTERISTIC_READ_UUID)
                val successFlag = bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
                LogContext.log.w("setCharacteristicNotification b=$successFlag")
            }

            // Receive data
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)
                val data = String(characteristic.value)
                LogContext.log.w("onCharacteristicChanged characteristic=${characteristic.toJsonString()} data=$data")
                toast("Received msg=$data")
            }

            // SENT callback
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                LogContext.log.w("onCharacteristicWrite characteristic=${characteristic.toJsonString()} status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    LogContext.log.w("Sent successfully.")
                }
                super.onCharacteristicWrite(gatt, characteristic, status)
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
        // Get service
        val service = bluetoothGatt!!.getService(BluetoothServerActivity.SERVICE_UUID) ?: return
        // Get writing characteristic
        val characteristic = service.getCharacteristic(BluetoothServerActivity.CHARACTERISTIC_WRITE_UUID)
        bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
        characteristic.value = msg.toByteArray()
        bluetoothGatt!!.writeCharacteristic(characteristic)
        LogContext.log.w("Send message to server=$msg")
    }
}