package com.ho1ho.socket_sdk.framework.client

import com.ho1ho.socket_sdk.framework.base.BaseNetty

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
        const val CONNECTION_ERROR_ALREADY_RELEASED = 0x1000

        //        const val CONNECTION_ERROR_CAN_NOT_CONNECT_TO_SERVER = 0x1001
        const val CONNECTION_ERROR_CONNECT_EXCEPTION = 0x1002
        const val CONNECTION_ERROR_UNEXPECTED_EXCEPTION = 0x1003
        const val CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES = 0x1004
        const val CONNECTION_ERROR_SERVER_DOWN = 0x1005
        const val CONNECTION_ERROR_NETWORK_LOST = 0x1006
    }
}