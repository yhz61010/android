package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.bluetoothManager
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityBluetoothServerBinding
import java.util.*

class BluetoothServerActivity : BaseDemonstrationActivity() {
    companion object {
        private const val SERVER_NAME = "Leo_BLE_Server"

        //服务uuid
        var UUID_SERVER = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

        //读的特征值¸
        var UUID_CHAR_READ = UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb")

        //写的特征值
        var UUID_CHAR_WRITE = UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb")
    }

    private var _binding: ActivityBluetoothServerBinding? = null
    private val binding get() = _binding!!

    private var connectedDevice: BluetoothDevice? = null
    private var characteristicRead: BluetoothGattCharacteristic? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            LogContext.log.w("onStartSuccess=${settingsInEffect.toJsonString()}")
            addService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityBluetoothServerBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }

        initView()
        initBleServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        BluetoothUtil.stopAdvertising(advertiseCallback)
        bluetoothGattServer?.run {
            clearServices()
            close()
        }
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
        BluetoothUtil.startAdvertising(SERVER_NAME, advertiseCallback)
    }

    /**
     * 添加读写服务UUID，特征值等
     */
    private fun addService() {
        val gattService = BluetoothGattService(UUID_SERVER, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        //只读的特征值
        characteristicRead = BluetoothGattCharacteristic(
            UUID_CHAR_READ,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        //只写的特征值
        val characteristicWrite = BluetoothGattCharacteristic(
            UUID_CHAR_WRITE,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ
                    or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        //将特征值添加至服务里
        gattService.addCharacteristic(characteristicRead)
        gattService.addCharacteristic(characteristicWrite)
        //监听客户端的连接
        bluetoothGattServer = app.bluetoothManager.openGattServer(this, gattServerCallback)
        bluetoothGattServer?.addService(gattService)
    }

    private val gattServerCallback: BluetoothGattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            connectedDevice = device
            var state = ""
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                state = "Connected"
                runOnUiThread { this@BluetoothServerActivity.title = "Bluetooth Server - ${connectedDevice?.name ?: connectedDevice?.address ?: ""}" }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                state = "Disconnected"
            }
            LogContext.log.w("onConnectionStateChange device=$device status=$status newState=$state")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            val data = String(value)
            LogContext.log.w("Received message=$data")
            toast("Received message=$data")
            // Response message
            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
        }
    }


    fun onSendClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val msg = binding.etMsg.text.toString()
        sendData(msg)
        binding.etMsg.setText("")
    }

    private fun sendData(msg: String) {
        characteristicRead?.value = msg.toByteArray()
        bluetoothGattServer?.notifyCharacteristicChanged(connectedDevice, characteristicRead, false)
        LogContext.log.w("Send message to client: $msg")
    }
}