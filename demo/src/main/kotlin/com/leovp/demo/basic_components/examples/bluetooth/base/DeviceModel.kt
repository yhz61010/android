package com.leovp.demo.basic_components.examples.bluetooth.base

import android.bluetooth.BluetoothDevice
import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 21-3-3 下午6:14
 */
@Keep
data class DeviceModel(
    var device: BluetoothDevice,
    var name: String?,
    var macAddress: String,
    var rssi: String?
) {
    var index: Int = 0
}
