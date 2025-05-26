package com.leovp.demo.basiccomponents.examples.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresPermission
import com.leovp.android.exts.bluetoothManager
import com.leovp.android.exts.toast
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityBluetoothServerBinding
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import java.util.UUID

/**
 *  Need following permissions:
 *
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <!-- Required only if your app isn't using the Device Companion Manager. -->
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 */
class BluetoothServerActivity :
    BaseDemonstrationActivity<ActivityBluetoothServerBinding>(R.layout.activity_bluetooth_server) {
    override fun getTagName(): String = ITAG

    companion object {
        private const val SERVER_NAME = "Leo_BLE_Server"

        // Service UUID
        var serviceUuid = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

        // Characteristic UUID for reading
        var characteristicReadUuid = UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb")

        // Characteristic UUID for writing
        var characteristicWriteUuid = UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb")
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityBluetoothServerBinding {
        return ActivityBluetoothServerBinding.inflate(layoutInflater)
    }

    private val bluetooth: BluetoothUtil by lazy { BluetoothUtil.getInstance(bluetoothManager.adapter) }

    private var connectedDevice: BluetoothDevice? = null
    private var characteristicRead: BluetoothGattCharacteristic? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    private val advertiseCallback = object : AdvertiseCallback() {
        @SuppressLint("InlinedApi")
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            LogContext.log.w(ITAG, "onStartSuccess=${settingsInEffect.toJsonString()}")
            addService()
        }
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE
        ]
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initView()
        initBleServer()
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onDestroy() {
        super.onDestroy()
        bluetooth.stopAdvertising(advertiseCallback)
        disconnect()
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnect() {
        bluetoothGattServer?.run {
            clearServices()
            close()
        }
    }

    private fun initView() {
        title = "Bluetooth Server"
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE
        ]
    )
    private fun initBleServer() {
        if (!bluetooth.isSupportBle(packageManager)) {
            toast("Does not support bluetooth!")
            finish()
            return
        }
        bluetooth.enable()
        bluetooth.startAdvertising(SERVER_NAME, advertiseCallback)
    }

    /**
     * Add service and characteristic
     */
    @SuppressLint("InlinedApi")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun addService() {
        val gattService =
            BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        // Read only characteristic
        characteristicRead = BluetoothGattCharacteristic(
            characteristicReadUuid,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // Write only characteristic
        val characteristicWrite = BluetoothGattCharacteristic(
            characteristicWriteUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ
                or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        // Add characteristics to service
        gattService.addCharacteristic(characteristicRead)
        gattService.addCharacteristic(characteristicWrite)
        // Client monitor listener
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        bluetoothGattServer?.addService(gattService)
    }

    private val gattServerCallback: BluetoothGattServerCallback =
        object : BluetoothGattServerCallback() {
            @SuppressLint("InlinedApi")
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                super.onConnectionStateChange(device, status, newState)
                connectedDevice = device
                var state = ""
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    state = "Connected"
                    runOnUiThread {
                        this@BluetoothServerActivity.title =
                            "Bluetooth Server - ${connectedDevice?.name ?: connectedDevice?.address ?: ""}"
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    state = "Disconnected"
                }
                LogContext.log.w(ITAG, "onConnectionStateChange device=$device status=$status newState=$state")
            }

            @SuppressLint("InlinedApi")
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray,
            ) {
                super.onCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value
                )
                val data = String(value)
                LogContext.log.w(ITAG, "Received message=$data")
                toast("Received message=$data")
                // Response message
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
                sendData("I received: $data")
            }
        }

    @SuppressLint("InlinedApi")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun onSendClick(@Suppress("unused") view: View) {
        val msg = binding.etMsg.text.toString()
        sendData(msg)
        binding.etMsg.setText("")
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendData(msg: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGattServer?.notifyCharacteristicChanged(
                    connectedDevice!!,
                    characteristicRead!!,
                    false,
                    msg.toByteArray()
                )
            } else {
                @Suppress("DEPRECATION")
                characteristicRead?.value = msg.toByteArray()
                @Suppress("DEPRECATION")
                bluetoothGattServer?.notifyCharacteristicChanged(
                    connectedDevice,
                    characteristicRead,
                    false
                )
            }

            LogContext.log.w(ITAG, "Send message to client: $msg")
        }.onFailure { it.printStackTrace() }
    }
}
