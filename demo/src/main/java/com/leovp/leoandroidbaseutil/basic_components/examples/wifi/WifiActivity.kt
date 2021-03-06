package com.leovp.leoandroidbaseutil.basic_components.examples.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.android.wifiManager
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.BluetoothClientActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.wifi.base.WifiAdapter
import com.leovp.leoandroidbaseutil.basic_components.examples.wifi.base.WifiModel
import com.leovp.leoandroidbaseutil.databinding.ActivityWifiBinding

/**
 * Need following permission:
 * On Android 8.0 and Android 8.1:
 * <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
 * Each background app can scan one time in a 30-minute period.
 *
 * On Android 9 besides Android 8.x permission, you also need to add following permission:
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 * or
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 * Each foreground app can scan four times in a 2-minute period. This allows for a burst of scans in a short time.
 * All background apps combined can scan one time in a 30-minute period.
 *
 * On Android 10 besides Android 8.x permission, you also need to add following permission:
 * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 * The same throttling limits from Android 9 apply.
 * There is a new developer option to toggle the throttling off for local testing (under Developer Options > Networking > Wi-Fi scan throttling).
 */
class WifiActivity : BaseDemonstrationActivity() {
    private var _binding: ActivityWifiBinding? = null
    private val binding get() = _binding!!

    private var adapter: WifiAdapter? = null

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess(app.wifiManager.scanResults)
            } else {
                scanFailure()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityWifiBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }
        initView()
        initReceiver()
        initData()
    }

    private fun initData() {
        val previousScanResults: List<ScanResult>? = app.wifiManager.scanResults
        if (previousScanResults?.isNullOrEmpty() == true) {
            LogContext.log.w("Scan automatically.")
            toast("Scan automatically.")
            doScan()
        } else {
            LogContext.log.w("Found previously scan results.")
            toast("Found previously scan results.")
            scanSuccess(previousScanResults)
        }
    }

    private fun initReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)
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

    private fun scanSuccess(results: List<ScanResult>) {
        val wifiList: MutableList<WifiModel> = mutableListOf()
        results.forEachIndexed { index, scanResult ->
            LogContext.log.i("Result=${scanResult.toJsonString()}")
            val wifiModel = WifiModel(scanResult.SSID, scanResult.level).apply { this.index = index + 1 }
            wifiList.add(wifiModel)
        }
        adapter?.clearAndAddList(wifiList)
    }

    private fun scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        val results = app.wifiManager.scanResults
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    fun onSetWifiClick(@Suppress("UNUSED_PARAMETER") view: View) {

    }

    fun onScanWifiClick(@Suppress("UNUSED_PARAMETER") view: View) {
        LogContext.log.w("Scan manually.")
        toast("Scan manually.")
        adapter?.clear()
        doScan()
    }

    private fun doScan() {
        val success = app.wifiManager.startScan()
        if (!success) {
            // scan failure handling
            scanFailure()
        }
    }
}