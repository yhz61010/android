package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.base.DeviceAdapter
import com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.base.DeviceModel
import com.leovp.leoandroidbaseutil.databinding.ActivityScanBluetoothBinding
import java.util.*

class ScanBluetoothActivity : BaseDemonstrationActivity() {

    private var _binding: ActivityScanBluetoothBinding? = null
    private val binding get() = _binding!!

    private var adapter: DeviceAdapter? = null

    private val bluetoothDeviceMap = mutableMapOf<String, DeviceModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityScanBluetoothBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }

        initView()
        initBluetooth()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun initView() {
        title = "Scan Bluetooth"
        adapter = DeviceAdapter()
        binding.rvDeviceList.run {
            layoutManager = LinearLayoutManager(this@ScanBluetoothActivity)
            adapter = this@ScanBluetoothActivity.adapter
        }
    }

    private fun initBluetooth() {
        if (!BluetoothUtil.isSupportBle()) {
            toast("Does not support bluetooth!")
            return
        }
        BluetoothUtil.enable()
    }

    fun onScanClick(@Suppress("UNUSED_PARAMETER") view: View) {
        bluetoothDeviceMap.clear()
        binding.btnDoScan.text = "Scan"
        BluetoothUtil.scan { device: BluetoothDevice, rssi: Int ->
            // Remove redundant data
            if (bluetoothDeviceMap.containsKey(device.address)) {
                return@scan
            }
            val model = DeviceModel(bluetoothDeviceMap.size + 1, device, device.name, device.address, rssi.toString())
            bluetoothDeviceMap[device.address] = model
            binding.btnDoScan.text = "Scan(${bluetoothDeviceMap.size})"
            val list = bluetoothDeviceMap.values.toMutableList()
            list.sortBy { it.name }
            adapter?.clearAndAddList(list)

            LogContext.log.d("All Keys=${bluetoothDeviceMap.keys}")
        }
    }

    fun onStopScan(@Suppress("UNUSED_PARAMETER") view: View) {
        bluetoothDeviceMap.clear()
        binding.btnDoScan.text = "Scan"
    }
}