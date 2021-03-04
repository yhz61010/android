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
import com.leovp.leoandroidbaseutil.databinding.ActivityBluetoothClientBinding
import java.util.*

class BluetoothClientActivity : BaseDemonstrationActivity() {

    private var _binding: ActivityBluetoothClientBinding? = null
    private val binding get() = _binding!!

    private var bluetoothGatt: BluetoothGatt? = null
    private var adapter: DeviceAdapter? = null
    private val bluetoothDeviceMap = mutableMapOf<String, DeviceModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityBluetoothClientBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }

        initView()
        initBluetooth()
        initReceiver()
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
        title = "Bluetooth Client"
        adapter = DeviceAdapter().apply {
            onItemClickListener = object : DeviceAdapter.OnItemClickListener {
                override fun onItemClick(item: DeviceModel, position: Int) {
                    bluetoothGatt = item.device.connectGatt(this@BluetoothClientActivity, false, object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                            super.onConnectionStateChange(gatt, status, newState)
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    LogContext.log.w("STATE_CONNECTED")
                                    gatt!!.discoverServices()
                                }
                                BluetoothProfile.STATE_CONNECTING -> {
                                    LogContext.log.w("STATE_CONNECTING")
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> {
                                    LogContext.log.w("STATE_DISCONNECTED")
                                }
                                BluetoothProfile.STATE_DISCONNECTING -> {
                                    LogContext.log.w("STATE_DISCONNECTING")
                                }
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            LogContext.log.w("onServicesDiscovered status=$status")
                            super.onServicesDiscovered(gatt, status)
                            //设置读特征值的监听，接收服务端发送的数据
                            val service = bluetoothGatt!!.getService(BluetoothServerActivity.UUID_SERVER)
                            val characteristic = service.getCharacteristic(BluetoothServerActivity.UUID_CHAR_READ)
                            val b = bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
                            LogContext.log.w("setCharacteristicNotification b=$b")
                        }

                        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                            super.onCharacteristicChanged(gatt, characteristic)
                            val data = String(characteristic.value)
                            LogContext.log.w("onCharacteristicChanged characteristic=${characteristic.toJsonString()} data=$data")
                        }
                    })
                }
            }
        }

        binding.rvDeviceList.run {
            layoutManager = LinearLayoutManager(this@BluetoothClientActivity)
            adapter = this@BluetoothClientActivity.adapter
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

    fun onScanClick(@Suppress("UNUSED_PARAMETER") view: View) {
        bluetoothDeviceMap.clear()
        binding.btnDoScan.text = "Scan"
        BluetoothUtil.scan()
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