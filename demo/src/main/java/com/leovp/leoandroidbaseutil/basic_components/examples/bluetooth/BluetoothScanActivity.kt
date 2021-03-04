package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.base.DeviceAdapter
import com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.base.DeviceModel
import com.leovp.leoandroidbaseutil.databinding.ActivityBluetoothScanBinding
import java.util.*

class BluetoothScanActivity : BaseDemonstrationActivity() {

    private var _binding: ActivityBluetoothScanBinding? = null
    private val binding get() = _binding!!

    private var adapter: DeviceAdapter? = null
    private val bluetoothDeviceMap = mutableMapOf<String, DeviceModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityBluetoothScanBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }

        initView()
        initBluetooth()
        initReceiver()
        doScan()
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
    }

    private fun initView() {
        title = "Bluetooth Scan"
        adapter = DeviceAdapter().apply {
            onItemClickListener = object : DeviceAdapter.OnItemClickListener {
                override fun onItemClick(item: DeviceModel, position: Int) {
                    val intent = Intent(this@BluetoothScanActivity, BluetoothClientActivity::class.java)
                    intent.putExtra("device", item.device)
                    startActivity(intent)
                }
            }
        }

        binding.rvDeviceList.run {
            layoutManager = LinearLayoutManager(this@BluetoothScanActivity)
            adapter = this@BluetoothScanActivity.adapter
        }
    }

    private fun initBluetooth() {
        if (!BluetoothUtil.isSupportBle()) {
            toast("Does not support bluetooth!")
            finish()
            return
        }
        BluetoothUtil.enable()
    }

    private fun doScan() {
        bluetoothDeviceMap.clear()
        binding.btnDoScan.text = "Scan"
        BluetoothUtil.scan()
    }

    fun onScanClick(@Suppress("UNUSED_PARAMETER") view: View) {
        doScan()
    }

    fun onStopScan(@Suppress("UNUSED_PARAMETER") view: View) {
        bluetoothDeviceMap.clear()
        BluetoothUtil.stopScan()
    }

    private fun oldScan() {
        bluetoothDeviceMap.clear()
        binding.btnDoScan.text = "Scan"
        BluetoothUtil.scan { device: BluetoothDevice, rssi: Int ->
            // Remove redundant data
            if (bluetoothDeviceMap.containsKey(device.address)) {
                return@scan
            }
            val model = DeviceModel(device, device.name, device.address, rssi.toString())
            bluetoothDeviceMap[device.address] = model
            binding.btnDoScan.text = "Scan(${bluetoothDeviceMap.size})"
            val list = bluetoothDeviceMap.values.toMutableList()
            list.sortBy { it.name }
            adapter?.clearAndAddList(list)
        }
    }

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val model = DeviceModel(device, device.name, device.address, null)
                    LogContext.log.w("Found device: ${model.toJsonString()}")
                    bluetoothDeviceMap[device.address] = model
                    binding.btnDoScan.text = "Scan(${bluetoothDeviceMap.size})"
                    val list = bluetoothDeviceMap.values.toMutableList()
                    list.sortByDescending { it.name }
                    adapter?.clearAndAddList(list)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    LogContext.log.w("Bluetooth discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    LogContext.log.w("Bluetooth discovery finished")
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    LogContext.log.w("Device connected")
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    LogContext.log.w("Device disconnected")
                }
            }
        }
    }
}