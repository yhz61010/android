package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresPermission
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.device.BluetoothUtil
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityBluetoothBinding
import com.leovp.lib_common_android.exts.bluetoothManager
import com.leovp.log_sdk.LogContext

/**
 *  Need following permissions:
 *
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <!-- Required only if your app isn't using the Device Companion Manager. -->
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 */
class BluetoothActivity : BaseDemonstrationActivity() {
    private var _binding: ActivityBluetoothBinding? = null
    private val binding get() = _binding!!

    private val bluetooth: BluetoothUtil by lazy { BluetoothUtil.getInstance(bluetoothManager.adapter) }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityBluetoothBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }

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
                    this@BluetoothActivity.toast("All permissions granted.")
                }

                override fun onDenied(denied: MutableList<String>?, never: Boolean) {
                    this@BluetoothActivity.toast("Permissions denied.", error = true)
                }
            })

        bluetooth.enable()
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onResume() {
        super.onResume()

        var msg = ""
        val boundedDevices = bluetooth.boundedDevices
        if (boundedDevices.isNotEmpty()) {
            boundedDevices.forEachIndexed { index, bluetoothDevice ->
                msg += "${index + 1}: ${bluetoothDevice.name}|${bluetoothDevice.address}\n"
            }
        }
        binding.tvInfo.text = msg
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT])
    fun onEnableBluetooth(@Suppress("UNUSED_PARAMETER") view: View) {
        val enable = bluetooth.enable()
        LogContext.log.i("Enable=$enable")
        toast("Enable=$enable")
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT])
    fun onDisableBluetooth(@Suppress("UNUSED_PARAMETER") view: View) {
        val disable = bluetooth.disable()
        LogContext.log.i("Disable=$disable")
        toast("Disable=$disable")
    }

    fun onScanClick(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity<BluetoothScanActivity>()
    }

    fun onGotoServerSideClick(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity<BluetoothServerActivity>()
    }
}