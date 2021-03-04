package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.base

import android.bluetooth.BluetoothDevice

/**
 * Author: Michael Leo
 * Date: 21-3-3 下午6:14
 */
data class DeviceModel(
    var device: BluetoothDevice, var name: String?, var macAddress: String, var rssi: String?
) {
    var index: Int = 0
}