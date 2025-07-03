package com.leovp.demo.basiccomponents.examples.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresPermission
import androidx.recyclerview.widget.LinearLayoutManager
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.android.exts.bluetoothManager
import com.leovp.android.exts.getParcelableExtraOrNull
import com.leovp.android.exts.toast
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.androidbase.utils.device.ScanDeviceCallback
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.bluetooth.base.DeviceAdapter
import com.leovp.demo.basiccomponents.examples.bluetooth.base.DeviceModel
import com.leovp.demo.databinding.ActivityBluetoothScanBinding
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

@SuppressLint("SetTextI18n")
class BluetoothScanActivity :
    BaseDemonstrationActivity<ActivityBluetoothScanBinding>(R.layout.activity_bluetooth_scan) {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityBluetoothScanBinding {
        return ActivityBluetoothScanBinding.inflate(layoutInflater)
    }

    private var adapter: DeviceAdapter? = null
    private val bluetoothDeviceMap = mutableMapOf<String, DeviceModel>()

    private val bluetooth: BluetoothUtil by lazy { BluetoothUtil.getInstance(bluetoothManager.adapter) }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initReceiver()
        initView()
        initBluetooth()

        XXPermissions.with(this)
            .permission(
                Permission.ACCESS_FINE_LOCATION,
                Permission.ACCESS_COARSE_LOCATION,
                Permission.BLUETOOTH_ADVERTISE,
                Permission.BLUETOOTH_CONNECT,
                Permission.BLUETOOTH_SCAN
            )
            .request(object : OnPermissionCallback {
                @SuppressLint("InlinedApi")
                @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
                override fun onGranted(granted: MutableList<String>, all: Boolean) {
                    doDiscovery()
                }

                override fun onDenied(denied: MutableList<String>, never: Boolean) {
                    this@BluetoothScanActivity.toast("Please grant Location permissions.")
                }
            })
    }

    private fun initReceiver() {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)

        registerReceiver(bluetoothReceiver, filter)
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
    override fun onDestroy() {
        super.onDestroy()
        bluetooth.release()
        unregisterReceiver(bluetoothReceiver)
    }

    private fun initView() {
        title = "Bluetooth Scan"
        adapter = DeviceAdapter().apply {
            @SuppressLint("InlinedApi")
            onItemClickListener = object : DeviceAdapter.OnItemClickListener {
                @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
                override fun onItemClick(item: DeviceModel, position: Int) {
                    bluetooth.cancelDiscovery()
                    startActivity<BluetoothClientActivity>({ intent ->
                        intent.putExtra(
                            "device",
                            item.device
                        )
                    })
                }
            }
        }

        binding.rvDeviceList.run {
            layoutManager = LinearLayoutManager(this@BluetoothScanActivity)
            adapter = this@BluetoothScanActivity.adapter
        }
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun initBluetooth() {
        if (!bluetooth.isSupportBle(packageManager)) {
            toast("Does not support bluetooth!")
            finish()
            return
        }
        bluetooth.enable()
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
    private fun doDiscovery() {
        bluetoothDeviceMap.clear()
        binding.btnDiscovery.text = "Discovery"
        val rtn = bluetooth.startDiscovery()
        LogContext.log.w(ITAG, "startDiscovery rtn=$rtn")
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
    fun onDiscoveryClick(@Suppress("unused") view: View) {
        LogContext.log.w(ITAG, "onDiscoveryClick")
        toast("onDiscoveryClick")
        doDiscovery()
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    fun onScanClick(@Suppress("unused") view: View) {
        LogContext.log.w(ITAG, "onScanClick Before Scanning, please stop it first if you don't do that.")
        toast("onScanClick Before Scanning, please stop it first if you don't do that.")
        doScan()
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN])
    fun onStopScan(@Suppress("unused") view: View) {
        LogContext.log.w(ITAG, "onStopScan")
        toast("onStopScan")
        bluetoothDeviceMap.clear()
        bluetooth.cancelDiscovery()
        bluetooth.stopScan()
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    private fun doScan() {
        bluetoothDeviceMap.clear()
        binding.btnDoScan.text = "Scan"
        bluetooth.scan(object : ScanDeviceCallback {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanned(device: BluetoothDevice, rssi: Int, result: ScanResult?) {
                // if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (result?.isConnectable == false) {
                    // Call [device.name] needs <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" /> permission.
                    LogContext.log.e(ITAG, "Ignore device:${device.name}|${device.address}")
                    return
                }
                // }
                // Remove redundant data
                if (bluetoothDeviceMap.containsKey(device.address)) {
                    return
                }
                // Call [device.name] needs <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" /> permission.
                val model = DeviceModel(device, device.name, device.address, rssi.toString())
                bluetoothDeviceMap[device.address] = model
                binding.btnDoScan.text = "Scan(${bluetoothDeviceMap.size})"
                val list = bluetoothDeviceMap.values.toMutableList()
                list.sortByDescending { it.name }
                adapter?.clearAndAddList(list)
            }
        })
    }

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("InlinedApi")
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice =
                        intent.getParcelableExtraOrNull(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val model = DeviceModel(device, device.name, device.address, null)
                    LogContext.log.w(ITAG, "Found device[${device.name}] ${device.address}")
                    bluetoothDeviceMap[device.address] = model
                    binding.btnDiscovery.text = "Discovery(${bluetoothDeviceMap.size})"
                    val list = bluetoothDeviceMap.values.toMutableList()
                    list.sortByDescending { it.name }
                    adapter?.clearAndAddList(list)
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    LogContext.log.w(ITAG, "Bluetooth discovery started")
                    toast("Discovery started")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    LogContext.log.w(ITAG, "Bluetooth discovery finished")
                    toast("Discovery done")
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    LogContext.log.w(ITAG, "Device connected")
                    toast("ACTION_ACL_CONNECTED")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    LogContext.log.w(ITAG, "Device disconnected")
                    toast("ACTION_ACL_DISCONNECTED")
                }

                else -> {
                    LogContext.log.e(ITAG, "Bluetooth discovery unknown error.")
                    toast("Bluetooth discovery unknown error.")
                }
            }
        }
    }
}
