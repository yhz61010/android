package com.leovp.androidbase.utils.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.bluetoothManager
import com.leovp.log_sdk.LogContext
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Need following permissions:
 *
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <!--  For get nearby devices, need location permission when above android M  -->
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 *
 * Author: Michael Leo
 * Date: 21-3-3 下午5:38
 *
 * [BLE_1](https://blog.csdn.net/qq_25827845/article/details/52400782)
 * [BLE_2](https://blog.csdn.net/qq_25827845/article/details/52997523)
 */
@Suppress("unused")
object BluetoothUtil {
    private val bluetoothManager: BluetoothManager by lazy { app.bluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        bluetoothManager.adapter
//        BluetoothAdapter.getDefaultAdapter()

        // TODO The following descriptions are right?
        // Bluetooth            -> use BluetoothAdapter#getDefaultAdapter()
        // Bluetooth Low Energy -> use BluetoothManager#getAdapter()
    }

    /**
     * Create bond with device
     *
     * `platform/packages/apps/Settings.git`
     *
     * `/Settings/src/com/android/settings/bluetooth/CachedBluetoothDevice.java`
     */
    fun createBond(bluetoothDeviceClass: Class<*>, btDevice: BluetoothDevice?): Boolean {
        val createBondMethod = bluetoothDeviceClass.getMethod("createBond")
        return createBondMethod.invoke(btDevice) as Boolean
    }

    /**
     * Remove bond
     *
     * platform/packages/apps/Settings.git
     *
     * /Settings/src/com/android/settings/bluetooth/CachedBluetoothDevice.java
     */
    fun removeBond(bluetoothClass: Class<*>, btDevice: BluetoothDevice?): Boolean {
        val removeBondMethod: Method = bluetoothClass.getMethod("removeBond")
        return removeBondMethod.invoke(btDevice) as Boolean
    }

    /**
     * Set pin
     */
    fun setPin(bluetoothClass: Class<out BluetoothDevice>, device: BluetoothDevice, str: String): Boolean {
        runCatching {
            val removeBondMethod = bluetoothClass.getDeclaredMethod("setPin", ByteArray::class.java)
            return removeBondMethod.invoke(device, str.toByteArray()) as Boolean
        }.onFailure {
            it.printStackTrace()
            return false
        }
        return true
    }

    /**
     * Cancel input
     */
    fun cancelPairingUserInput(bluetoothClass: Class<*>, device: BluetoothDevice?): Boolean {
        val createBondMethod = bluetoothClass.getMethod("cancelPairingUserInput")
        // cancelBondProcess(bluetoothClass, device)
        return createBondMethod.invoke(device) as Boolean
    }

    /**
     * Cancel bond
     */
    fun cancelBondProcess(bluetoothClass: Class<*>, device: BluetoothDevice?): Boolean {
        val createBondMethod = bluetoothClass.getMethod("cancelBondProcess")
        return createBondMethod.invoke(device) as Boolean
    }

    /**
     *  Confirm pairing
     */
    fun setPairingConfirmation(btClass: Class<*>, device: BluetoothDevice?, isConfirm: Boolean) {
        val setPairingConfirmation = btClass.getDeclaredMethod("setPairingConfirmation", Boolean::class.javaPrimitiveType)
        setPairingConfirmation.invoke(device, isConfirm)
    }

    fun printAllInformation(clsShow: Class<*>) {
        runCatching {
            // Get all methods
            val hideMethod: Array<Method> = clsShow.methods
            hideMethod.forEachIndexed { index, method ->
                if (LogContext.enableLog) {
                    LogContext.log.d("Method[$index] name: ${method.name}")
                }
            }
            // Get all const values
            val allFields: Array<Field> = clsShow.fields
            allFields.forEachIndexed { index, field ->
                if (LogContext.enableLog) {
                    LogContext.log.d("Field[$index] name: ${field.name}")
                }
            }
        }.onFailure { it.printStackTrace() }
    }
    // ========================================================================

    private var scanDeviceCallback: ScanDeviceCallback? = null

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            scanDeviceCallback?.onScanned(result.device, result.rssi, result)
        }
    }

    private val leScanCallback = LeScanCallback { device, rssi, _ ->
        scanDeviceCallback?.onScanned(device, rssi)
    }

    // Bluetooth Low Energy
    fun isSupportBle(): Boolean = app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    @SuppressLint("MissingPermission")
    var isEnabled: Boolean = bluetoothAdapter.isEnabled
        private set

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun enable(): Boolean = bluetoothAdapter.enable()

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun disable(): Boolean = bluetoothAdapter.disable()

    /**
     * Make sure bluetooth is enabled before calling this method.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun scan(scanDeviceCallback: ScanDeviceCallback) {
        this.scanDeviceCallback = scanDeviceCallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
        } else {
            bluetoothAdapter.startLeScan(leScanCallback)
        }
    }

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        } else {
            bluetoothAdapter.stopLeScan(leScanCallback)
        }
    }

    /**
     * **DO NOT forget to implement the bluetooth receiver.**
     * Make sure bluetooth is enabled before calling this method.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean = bluetoothAdapter.startDiscovery()

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun cancelDiscovery(): Boolean = bluetoothAdapter.cancelDiscovery()

    /**
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising(name: String, callback: AdvertiseCallback) {
        val settings = AdvertiseSettings.Builder()
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .build()
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .build()
        bluetoothAdapter.name = name
        val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, callback)
    }

    /**
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun stopAdvertising(callback: AdvertiseCallback) {
        val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser.stopAdvertising(callback)
    }

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     */
    fun release() {
        cancelDiscovery()
        stopScan()
    }

    /**
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH" />
     * permission
     */
    @SuppressLint("MissingPermission")
    var boundedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        private set
}

interface ScanDeviceCallback {
    fun onScanned(device: BluetoothDevice, rssi: Int, result: ScanResult? = null)
}
