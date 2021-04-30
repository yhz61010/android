package com.leovp.socket_sdk.framework.client

import com.leovp.socket_sdk.framework.base.BaseNetty

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
interface ClientConnectListener<in T : BaseNetty> {

    fun onConnected(netty: T)

    /**
     * Note that, this method **DOES NOT** indicate the receiving data from socket.
     *
     * This interface should be used in your specified command
     * so that you can pass the data whatever you want to your upper business layer.
     */
    fun onReceivedData(netty: T, data: Any?, action: Int = -1) {}

    /**
     * @param byRemote
     *            Attention, this parameter is not implemented by normal TCP Socket. Only used by WebSocket
     *            Whether connection is disconnected by remote server.
     */
    fun onDisconnected(netty: T, byRemote: Boolean)
    fun onFailed(netty: T, code: Int, msg: String?, e: Throwable? = null)

    companion object {
        private const val CONNECTION_ERROR_BASE_CODE = 0x1000 // 4096

        const val CONNECTION_ERROR_ALREADY_RELEASED = CONNECTION_ERROR_BASE_CODE + 1 // 4097
        const val CONNECTION_ERROR_CONNECT_EXCEPTION = CONNECTION_ERROR_BASE_CODE + 2 // 4098
        const val CONNECTION_ERROR_UNEXPECTED_EXCEPTION = CONNECTION_ERROR_BASE_CODE + 3 // 4099
        const val CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES = CONNECTION_ERROR_BASE_CODE + 4 // 4100
        const val CONNECTION_ERROR_CONNECTION_DISCONNECT = CONNECTION_ERROR_BASE_CODE + 5 // 4101
        const val CONNECTION_ERROR_NETWORK_LOST = CONNECTION_ERROR_BASE_CODE + 6 // 4102
        const val CONNECTION_ERROR_SOCKET_EXCEPTION = CONNECTION_ERROR_BASE_CODE + 7 // 4103
        const val DISCONNECT_MANUALLY_ERROR = CONNECTION_ERROR_BASE_CODE + 8 // 4104
        const val DISCONNECT_MANUALLY_EXCEPTION = CONNECTION_ERROR_BASE_CODE + 9 // 4105
    }
}