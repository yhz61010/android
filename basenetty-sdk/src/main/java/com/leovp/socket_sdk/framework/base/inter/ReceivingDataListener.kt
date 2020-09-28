package com.leovp.socket_sdk.framework.base.inter

/**
 * Author: Michael Leo
 * Date: 20-5-25 下午2:42
 */
interface ReceivingDataListener {
//    fun onReceiveData(which: Int, obj: BaseReceivingData?)
fun onReceiveData(which: Int, obj: Any?)
}