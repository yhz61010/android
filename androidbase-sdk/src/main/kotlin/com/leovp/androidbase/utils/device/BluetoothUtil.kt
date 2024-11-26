package com.leovp.androidbase.utils.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import com.leovp.kotlin.utils.SingletonHolder
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
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
class BluetoothUtil private constructor(private val bluetoothAdapter: BluetoothAdapter) {
    companion object : SingletonHolder<BluetoothUtil, BluetoothAdapter>(::BluetoothUtil)

    //    private val bluetoothManager: BluetoothManager by lazy { ctx.bluetoothManager }
    //    private val bluetoothAdapter: BluetoothAdapter by lazy {
    //        bluetoothManager.adapter
    // //        BluetoothAdapter.getDefaultAdapter()
    //
    //        // TODO The following descriptions are right?
    //        // Bluetooth            -> use BluetoothAdapter#getDefaultAdapter()
    //        // Bluetooth Low Energy -> use BluetoothManager#getAdapter()
    //    }

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
        val setPairingConfirmation =
            btClass.getDeclaredMethod(
                "setPairingConfirmation",
                Boolean::class.javaPrimitiveType
            )
        setPairingConfirmation.invoke(device, isConfirm)
    }

    fun printAllInformation(clsShow: Class<*>) {
        runCatching {
            // Get all methods
            val hideMethod: Array<Method> = clsShow.methods
            hideMethod.forEachIndexed { index, method ->
                LogContext.log.d(ITAG, "Method[$index] name: ${method.name}")
            }
            // Get all const values
            val allFields: Array<Field> = clsShow.fields
            allFields.forEachIndexed { index, field ->
                LogContext.log.d(ITAG, "Field[$index] name: ${field.name}")
            }
        }.onFailure { it.printStackTrace() }
    }
    // ========================================================================

    private var scanDeviceCallback: ScanDeviceCallback? = null

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
    fun isSupportBle(pm: PackageManager): Boolean = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    var isEnabled: Boolean = bluetoothAdapter.isEnabled
        private set

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Starting with Build.VERSION_CODES.TIRAMISU,
     * applications are not allowed to enable/disable Bluetooth.
     * Compatibility Note: For applications targeting Build.VERSION_CODES.TIRAMISU or above,
     * this API will always fail and return false.
     * If apps are targeting an older SDK (Build.VERSION_CODES.S or below),
     * they can continue to use this API.
     *
     * https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#enable()
     *
     * Bluetooth should never be enabled without direct user consent.
     * If you want to turn on Bluetooth in order to create a wireless connection,
     * you should use the ACTION_REQUEST_ENABLE Intent,
     * which will raise a dialog that requests user permission to turn on Bluetooth.
     * The enable() method is provided only for applications that include a user interface
     * for changing system settings, such as a "power manager" app.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
     * permission
     */
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT])
    fun enable(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        false
    } else {
        @Suppress("DEPRECATION")
        bluetoothAdapter.enable()
    }

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Starting with Build.VERSION_CODES.TIRAMISU,
     * applications are not allowed to enable/disable Bluetooth.
     * Compatibility Note: For applications targeting Build.VERSION_CODES.TIRAMISU or above,
     * this API will always fail and return false.
     * If apps are targeting an older SDK (Build.VERSION_CODES.S or below),
     * they can continue to use this API.
     *
     * https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#disable()
     *
     * Bluetooth should never be disabled without direct user consent.
     * The disable() method is provided only for applications that include a user interface
     * for changing system settings, such as a "power manager" app.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
     * permission
     */
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT])
    fun disable(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        false
    } else {
        @Suppress("DEPRECATION")
        bluetoothAdapter.disable()
    }

    /**
     * Make sure bluetooth is enabled before calling this method.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
     * <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
     * permission
     */
    @SuppressLint("InlinedApi")
    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN
        ]
    )
    fun scan(scanDeviceCallback: ScanDeviceCallback) {
        this.scanDeviceCallback = scanDeviceCallback
        bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
    }

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
     * permission
     */
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
    fun stopScan() {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    /**
     * **DO NOT forget to implement the bluetooth receiver.**
     * Make sure bluetooth is enabled before calling this method.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
     * permission
     */
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
    fun startDiscovery(): Boolean = bluetoothAdapter.startDiscovery()

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     *
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
     * permission
     */
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
    fun cancelDiscovery(): Boolean = bluetoothAdapter.cancelDiscovery()

    /**
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
     * <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
     * <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
     * permission
     */
    @SuppressLint("InlinedApi")
    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE
        ]
    )
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
    @SuppressLint("InlinedApi")
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising(callback: AdvertiseCallback) {
        val bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser.stopAdvertising(callback)
    }

    /**
     * You'd better add a short delay after calling this method to make sure the operation will be done.
     */
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
    fun release() {
        cancelDiscovery()
        stopScan()
    }

    /**
     * Requires
     * <uses-permission android:name="android.permission.BLUETOOTH" />
     * <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
     * permission
     */
    @SuppressLint("InlinedApi", "MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_CONNECT])
    var boundedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        private set
}

interface ScanDeviceCallback {
    fun onScanned(device: BluetoothDevice, rssi: Int, result: ScanResult? = null)
}
