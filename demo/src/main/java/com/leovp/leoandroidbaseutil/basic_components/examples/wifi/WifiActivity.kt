package com.leovp.leoandroidbaseutil.basic_components.examples.wifi

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.BluetoothClientActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.wifi.base.WifiAdapter
import com.leovp.leoandroidbaseutil.basic_components.examples.wifi.base.WifiModel
import com.leovp.leoandroidbaseutil.databinding.ActivityWifiBinding

class WifiActivity : BaseDemonstrationActivity() {
    private var _binding: ActivityWifiBinding? = null
    private val binding get() = _binding!!

    private var adapter: WifiAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityWifiBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }
        initView()
        initData()
    }

    private fun initView() {
        adapter = WifiAdapter().apply {
            onItemClickListener = object : WifiAdapter.OnItemClickListener {
                override fun onItemClick(item: WifiModel, position: Int) {
                    BluetoothUtil.cancelDiscovery()
                    val intent = Intent(this@WifiActivity, BluetoothClientActivity::class.java)
                    intent.putExtra("device", item.name)
                    startActivity(intent)
                }
            }
        }

        binding.rvWifiList.run {
            layoutManager = LinearLayoutManager(this@WifiActivity)
            adapter = this@WifiActivity.adapter
        }
    }

    private fun initData() {
        val wifiList: MutableList<WifiModel> = mutableListOf()
        for (i in 0..10) {
            val wifiModel = WifiModel("WIFI-${i + 1}").apply { index = i + 1 }
            wifiList.add(wifiModel)
        }
        adapter?.clearAndAddList(wifiList)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}