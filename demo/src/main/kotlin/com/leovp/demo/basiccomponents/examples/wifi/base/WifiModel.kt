package com.leovp.demo.basiccomponents.examples.wifi.base

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 21-3-6 下午3:59
 */
@Keep
data class WifiModel(val name: String, val bssid: String, val signalLevel: Int, val freq: Int) {
    var index: Int = 0
}
