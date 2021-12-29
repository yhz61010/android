package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.androidbase.utils.device.ScanDeviceCallback
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.base.DeviceAdapter
import com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.base.DeviceModel
import com.leovp.leoandroidbaseutil.databinding.ActivityBluetoothScanBinding
import com.leovp.lib_common_android.exts.bluetoothManager
import com.leovp.log_sdk.LogContext
import java.util.*

@SuppressLint("SetTextI18n")
class BluetoothScanActivity : BaseDemonstrationActivity() {

    private var _binding: ActivityBluetoothScanBinding? = null
    private val binding get() = _binding!!

    private var adapter: DeviceAdapter? = null
    private val bluetoothDeviceMap = mutableMapOf<String, DeviceModel>()

    private val bluetooth: BluetoothUtil by lazy { BluetoothUtil.getInstance(bluetoothManager.adapter) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityBluetoothScanBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }

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
                override fun onGranted(granted: MutableList<String>?, all: Boolean) {
                    doDiscovery()
                }

                override fun onDenied(denied: MutableList<String>?, never: Boolean) {
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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        bluetooth.release()
        unregisterReceiver(bluetoothReceiver)
    }

    private fun initView() {
        title = "Bluetooth Scan"
        adapter = DeviceAdapter().apply {
            onItemClickListener = object : DeviceAdapter.OnItemClickListener {
                override fun onItemClick(item: DeviceModel, position: Int) {
                    bluetooth.cancelDiscovery()
                    startActivity(BluetoothClientActivity::class, { intent -> intent.putExtra("device", item.device) })
                }
            }
        }

        binding.rvDeviceList.run {
            layoutManager = LinearLayoutManager(this@BluetoothScanActivity)
            adapter = this@BluetoothScanActivity.adapter
        }
    }

    private fun initBluetooth() {
        if (!bluetooth.isSupportBle(packageManager)) {
            toast("Does not support bluetooth!")
            finish()
            return
        }
        bluetooth.enable()
    }

    private fun doDiscovery() {
        bluetoothDeviceMap.clear()
        binding.btnDiscovery.text = "Discovery"
        val rtn = bluetooth.startDiscovery()
        LogContext.log.w("startDiscovery rtn=$rtn")
    }

    fun onDiscoveryClick(@Suppress("UNUSED_PARAMETER") view: View) {
        LogContext.log.w("onDiscoveryClick")
        toast("onDiscoveryClick")
        doDiscovery()
    }

    fun onScanClick(@Suppress("UNUSED_PARAMETER") view: View) {
        LogContext.log.w("onScanClick Before Scanning, please stop it first if you don't do that.")
        toast("onScanClick Before Scanning, please stop it first if you don't do that.")
        doScan()
    }

    fun onStopScan(@Suppress("UNUSED_PARAMETER") view: View) {
        LogContext.log.w("onStopScan")
        toast("onStopScan")
        bluetoothDeviceMap.clear()
        bluetooth.cancelDiscovery()
        bluetooth.stopScan()
    }

    private fun doScan() {
        bluetoothDeviceMap.clear()
        binding.btnDoScan.text = "Scan"
        bluetooth.scan(object : ScanDeviceCallback {
            override fun onScanned(device: BluetoothDevice, rssi: Int, result: ScanResult?) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (result?.isConnectable == false) {
                        // Call [device.name] needs <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" /> permission.
                        LogContext.log.e("Ignore device:${device.name}|${device.address}")
                        return
                    }
                }
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
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val model = DeviceModel(device, device.name, device.address, null)
                    LogContext.log.w("Found device: ${model.toJsonString()}")
                    bluetoothDeviceMap[device.address] = model
                    binding.btnDiscovery.text = "Discovery(${bluetoothDeviceMap.size})"
                    val list = bluetoothDeviceMap.values.toMutableList()
                    list.sortByDescending { it.name }
                    adapter?.clearAndAddList(list)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    LogContext.log.w("Bluetooth discovery started")
                    toast("Discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    LogContext.log.w("Bluetooth discovery finished")
                    toast("Discovery done")
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    LogContext.log.w("Device connected")
                    toast("ACTION_ACL_CONNECTED")
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    LogContext.log.w("Device disconnected")
                    toast("ACTION_ACL_DISCONNECTED")
                }
                else -> {
                    LogContext.log.e("Bluetooth discovery unknown error.")
                    toast("Bluetooth discovery unknown error.")
                }
            }
        }
    }
}