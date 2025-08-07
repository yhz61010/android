package com.leovp.demo.basiccomponents.examples.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresPermission
import androidx.recyclerview.widget.LinearLayoutManager
import com.leovp.android.exts.toast
import com.leovp.android.exts.wifiManager
import com.leovp.androidbase.utils.device.WifiUtil
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.wifi.base.WifiAdapter
import com.leovp.demo.basiccomponents.examples.wifi.base.WifiModel
import com.leovp.demo.databinding.ActivityWifiBinding
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

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
class WifiActivity : BaseDemonstrationActivity<ActivityWifiBinding>(R.layout.activity_wifi) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityWifiBinding =
        ActivityWifiBinding.inflate(layoutInflater)

    private var adapter: WifiAdapter? = null
    private val wifi: WifiUtil by lazy { WifiUtil.getInstance(this) }

    private val wifiScanReceiver = object : BroadcastReceiver() {
        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess(wifiManager.scanResults)
            } else {
                scanFailure()
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initView()
        initReceiver()
        initData()

        LogContext.log.e(ITAG, "current ssid=${wifi.getCurrentSsid()}")
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    private fun initData() {
        val previousScanResults: List<ScanResult>? = wifiManager.scanResults
        if (previousScanResults?.isEmpty() == true) {
            LogContext.log.w(tag, "Scan automatically.")
            toast("Scan automatically.")
            @Suppress("DEPRECATION")
            doScan()
        } else {
            LogContext.log.w(tag, "Found previously scan results.")
            toast("Found previously scan results.")
            previousScanResults?.let { scanSuccess(it) }
        }
    }

    private fun initReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)
    }

    private fun initView() {
        adapter = WifiAdapter(wifi.getCurrentSsid()).apply {
            onItemClickListener = object : WifiAdapter.OnItemClickListener {
                override fun onItemClick(item: WifiModel, position: Int) {
                    binding.etWifiName.setText(item.name)
                }
            }
        }

        binding.rvWifiList.run {
            layoutManager = LinearLayoutManager(this@WifiActivity)
            adapter = this@WifiActivity.adapter
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun scanSuccess(results: List<ScanResult>) {
        val wifiList: MutableList<WifiModel> = mutableListOf()
        results.forEachIndexed { index, scanResult ->
            LogContext.log.i(tag, "Result=${scanResult.toJsonString()}")
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                scanResult.wifiSsid.toString()
            } else {
                @Suppress("DEPRECATION")
                scanResult.SSID
            }
            if (ssid.isNotBlank()) {
                val wifiModel = WifiModel(
                    ssid,
                    scanResult.BSSID,
                    scanResult.level,
                    scanResult.frequency
                ).apply { this.index = index + 1 }
                wifiList.add(wifiModel)
            }
        }
        val wifiMap = wifiList.groupBy { it.name }

        val mergedWifiList: MutableList<WifiModel> = mutableListOf()
        for ((_, sameWifiList) in wifiMap) {
            mergedWifiList.add(sameWifiList.maxByOrNull { it.freq }!!)
        }
        mergedWifiList.sortByDescending { it.signalLevel }
        adapter?.clearAndAddList(mergedWifiList)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    private fun scanFailure() {
        LogContext.log.w(tag, "scanFailure")
        toast("scanFailure")
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        val results = wifiManager.scanResults
        scanSuccess(results)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiScanReceiver)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    fun onSetWifiClick(@Suppress("unused") view: View) {
        val ssid = binding.etWifiName.text.toString()
        val pwd = binding.etWifiPwd.text.toString()
        wifi.connectWifi(ssid, pwd)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    fun onScanWifiClick(@Suppress("unused") view: View) {
        LogContext.log.w(tag, "Scan manually.")
        toast("Scan manually.")
        adapter?.clear()
        @Suppress("DEPRECATION")
        doScan()
    }

    /**
     * The WifiManager.startScan() usage is limited on Android 8+, especially on Android 9+.
     *
     * https://developer.android.com/guide/topics/connectivity/wifi-scan#wifi-scan-throttling
     */
    @Deprecated("WifiManager#startScan() was deprecated in API level 28(Android 9).")
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE])
    private fun doScan() {
        // https://developer.android.com/guide/topics/connectivity/wifi-scan#wifi-scan-throttling
        @Suppress("DEPRECATION")
        val success = wifiManager.startScan()
        LogContext.log.w(ITAG, "doScan()=$success")
        if (!success) {
            // scan failure handling
            scanFailure()
        }
    }
}
