package com.leovp.socket_sdk.framework.client

import com.leovp.socket_sdk.framework.base.BaseNetty

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
interface ClientConnectListener<in T : BaseNetty> {

    fun onConnected(netty: T)
    fun onReceivedData(netty: T, data: Any?, action: Int = -1)
    fun onDisconnected(netty: T)
    fun onFailed(netty: T, code: Int, msg: String?)

    companion object {
        private const val CONNECTION_ERROR_BASE_CODE = 0x1000 // 4096

        const val CONNECTION_ERROR_ALREADY_RELEASED = CONNECTION_ERROR_BASE_CODE + 1
        const val CONNECTION_ERROR_CONNECT_EXCEPTION = CONNECTION_ERROR_BASE_CODE + 2
        const val CONNECTION_ERROR_UNEXPECTED_EXCEPTION = CONNECTION_ERROR_BASE_CODE + 3
        const val CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES = CONNECTION_ERROR_BASE_CODE + 4
        const val CONNECTION_ERROR_CONNECTION_DISCONNECT = CONNECTION_ERROR_BASE_CODE + 5
        const val CONNECTION_ERROR_NETWORK_LOST = CONNECTION_ERROR_BASE_CODE + 6
    }
}