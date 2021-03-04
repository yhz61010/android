package com.leovp.androidbase.utils.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.bluetoothManager

/**
 * Need following permissions:
 *
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <!--  For get nearby devices, need location permission when above android M  -->
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 *
 * Author: Michael Leo
 * Date: 21-3-3 下午5:38
 */
object BluetoothUtil {
    private val bluetoothManager: BluetoothManager by lazy { app.bluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }

    // Bluetooth Low Energy
    fun isSupportBle(): Boolean {
        return app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun enable(): Boolean {
        return if (!bluetoothAdapter.isEnabled) bluetoothAdapter.enable() else true
    }

    /**
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun disable(): Boolean {
        return if (bluetoothAdapter.isEnabled) bluetoothAdapter.disable() else true
    }

    /**
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * permission
     */
    @SuppressLint("MissingPermission")
    fun scan(callback: (device: BluetoothDevice, rssi: Int) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothAdapter.bluetoothLeScanner?.startScan(object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    val device = result.device
                    callback(device, result.rssi)
                }
            })
        } else {
            bluetoothAdapter.startLeScan { device, rssi, _ -> callback(device, rssi) }
        }
    }
}