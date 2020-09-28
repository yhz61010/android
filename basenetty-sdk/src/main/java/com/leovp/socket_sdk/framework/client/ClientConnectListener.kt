package com.leovp.socket_sdk.framework.client

import com.leovp.socket_sdk.framework.base.BaseNetty

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
interface ClientConnectListener<in T : BaseNetty> {

    fun onConnected(netty: T)
    fun onReceivedData(netty: T, data: Any?)
    fun onDisconnected(netty: T)
    fun onFailed(netty: T, code: Int, msg: String?)

    companion object {
        const val CONNECTION_ERROR_ALREADY_RELEASED = 0x1000 // 4096

        //        const val CONNECTION_ERROR_CAN_NOT_CONNECT_TO_SERVER = 0x1001
        const val CONNECTION_ERROR_CONNECT_EXCEPTION = 0x1002 // 4098
        const val CONNECTION_ERROR_UNEXPECTED_EXCEPTION = 0x1003 // 4099
        const val CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES = 0x1004 // 4100
        const val CONNECTION_ERROR_SERVER_DOWN = 0x1005 // 4101
        const val CONNECTION_ERROR_NETWORK_LOST = 0x1006 // 4102
    }
}