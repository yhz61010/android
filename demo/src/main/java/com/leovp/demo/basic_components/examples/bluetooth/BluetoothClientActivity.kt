package com.leovp.demo.basic_components.examples.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.leovp.androidbase.exts.android.toast
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityBluetoothClientBinding
import com.leovp.lib_common_android.exts.getParcelableExtraOrNull
import com.leovp.lib_json.toJsonString
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

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
 * <!-- Required only if your app isn't using the Device Companion Manager. -->
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 *
 * @see <a href="https://www.jianshu.com/p/a27f3ca027e3">BLE Develop</a>
 * @see <a href="https://www.jianshu.com/p/71116665fd08">FAQ</a>
 */
class BluetoothClientActivity : BaseDemonstrationActivity<ActivityBluetoothClientBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityBluetoothClientBinding {
        return ActivityBluetoothClientBinding.inflate(layoutInflater)
    }

    private var device: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null

    @SuppressLint("InlinedApi")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initView()
        initData()
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnect() {
        bluetoothGatt?.run {
            disconnect()
            close()
        }
    }

    private fun initView() {
        title = "Bluetooth Client"
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun initData() {
        device = intent.getParcelableExtraOrNull("device")
        bluetoothGatt = device!!.connectGatt(this, false, object : BluetoothGattCallback() {
            @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED     -> {
                        LogContext.log.w("STATE_CONNECTED")
                        gatt!!.discoverServices()
                    }
                    BluetoothProfile.STATE_CONNECTING    -> {
                        LogContext.log.w("STATE_CONNECTING")
                    }
                    BluetoothProfile.STATE_DISCONNECTED  -> {
                        LogContext.log.w("STATE_DISCONNECTED")
                    }
                    BluetoothProfile.STATE_DISCONNECTING -> {
                        LogContext.log.w("STATE_DISCONNECTING")
                    }
                }
            }

            @SuppressLint("InlinedApi")
            @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                LogContext.log.w("onServicesDiscovered status=$status")
                super.onServicesDiscovered(gatt, status)
                // Set characteristics
                val service = bluetoothGatt!!.getService(BluetoothServerActivity.SERVICE_UUID)
                val characteristic =
                        service.getCharacteristic(BluetoothServerActivity.CHARACTERISTIC_READ_UUID)
                val successFlag =
                        bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
                LogContext.log.w("setCharacteristicNotification b=$successFlag")
            }

            // Receive data
            @Deprecated("Deprecated in Java. Since Android 13.")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    val data = String(characteristic.value)
                    LogContext.log.w("onCharacteristicChanged Below Android 13 characteristic=${characteristic.toJsonString()} data=$data")
                    toast("Received msg=$data")
                }
            }

            // Receive data
            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun onCharacteristicChanged(gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray) {
                super.onCharacteristicChanged(gatt, characteristic, value)
                val data = String(value)
                LogContext.log.w("onCharacteristicChanged Above Android 13 characteristic=${characteristic.toJsonString()} data=$data")
                toast("Received msg=$data")
            }

            // SENT callback
            override fun onCharacteristicWrite(gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int) {
                LogContext.log.w("onCharacteristicWrite characteristic=${characteristic.toJsonString()} status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    LogContext.log.w("Sent successfully.")
                }
                super.onCharacteristicWrite(gatt, characteristic, status)
            }
        })
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun onSendClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val msg = binding.etMsg.text.toString()
        sendData(msg)
        binding.etMsg.setText("")
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendData(msg: String) {
        if (bluetoothGatt == null) {
            return
        }
        // Get service
        val service = bluetoothGatt!!.getService(BluetoothServerActivity.SERVICE_UUID) ?: return
        // Get writing characteristic
        val characteristic =
                service.getCharacteristic(BluetoothServerActivity.CHARACTERISTIC_WRITE_UUID)
        bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt!!.writeCharacteristic(characteristic,
                msg.toByteArray(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = msg.toByteArray()
            @Suppress("DEPRECATION")
            bluetoothGatt!!.writeCharacteristic(characteristic)
        }

        LogContext.log.w("Send message to server=$msg")
    }
}