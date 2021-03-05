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
 */
object BluetoothUtil {
    private val bluetoothManager: BluetoothManager by lazy { app.bluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        bluetoothManager.adapter
//        BluetoothAdapter.getDefaultAdapter()

        // TODO The following descriptions are right?
        // Bluetooth            -> use BluetoothAdapter#getDefaultAdapter()
        // Bluetooth Low Energy -> use BluetoothManager#getAdapter()
    }

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
    fun enable(): Boolean = if (!isEnabled) bluetoothAdapter.enable() else true

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun disable(): Boolean = if (isEnabled) bluetoothAdapter.disable() else true

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
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
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
    fun startDiscovery(): Boolean = if (isEnabled) bluetoothAdapter.startDiscovery() else false

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun cancelDiscovery(): Boolean = if (isEnabled) bluetoothAdapter.cancelDiscovery() else true

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

    fun stopAdvertising(callback: AdvertiseCallback) {
        val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser.stopAdvertising(callback)
    }

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     */
    fun release() = if (isEnabled) stopScan() else Unit
}

interface ScanDeviceCallback {
    fun onScanned(device: BluetoothDevice, rssi: Int, result: ScanResult? = null)
}
