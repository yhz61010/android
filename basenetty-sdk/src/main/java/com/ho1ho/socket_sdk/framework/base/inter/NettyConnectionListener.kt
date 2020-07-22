package com.ho1ho.socket_sdk.framework.base.inter

import com.ho1ho.socket_sdk.framework.base.BaseNettyClient

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
interface NettyConnectionListener {

    fun onConnecting(client: BaseNettyClient)
    fun onConnected(client: BaseNettyClient)
    fun onReceivedData(client: BaseNettyClient, data: Any?)
    fun onDisconnected(client: BaseNettyClient)
    fun onFailed(client: BaseNettyClient, code: Int, msg: String?)
    fun onException(client: BaseNettyClient, cause: Throwable)

    //    fun onConnectionTimeout()

    // =========================================
//    fun sendHeartbeat() {}
//    fun onReceiveHeartbeat(msg: Any?) {}

    // =========================================
//    fun onReaderIdle(ctx: ChannelHandlerContext?) {}
//    fun onWriterIdle(ctx: ChannelHandlerContext?) {}
//    fun onAllIdle(ctx: ChannelHandlerContext?) {}

    companion object {
        const val CONNECTION_ERROR_ALREADY_RELEASED = 0x1000

        @Suppress("unused")
        const val CONNECTION_ERROR_CAN_NOT_CONNECT_TO_SERVER = 0x1001
        const val CONNECTION_ERROR_CONNECT_EXCEPTION = 0x1002
        const val CONNECTION_ERROR_UNEXPECTED_EXCEPTION = 0x1003
        const val CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES = 0x1004
        const val CONNECTION_ERROR_SERVER_DOWN = 0x1005
        const val CONNECTION_ERROR_NETWORK_LOST = 0x1006
    }
}

enum class ConnectionStatus {
    /**
     * This is the connection default status after initializing netty client.
     *
     * Only you release socket, it will be in this status.
     * In this status, you can not reconnect again. You must create netty client again.
     */
    UNINITIALIZED,
    CONNECTING,
    CONNECTED,

    /**
     * After connecting, this connection is **ONLY** be working in this status if you do intent to disconnect to server as you expect.
     *
     * **Attention:** [FAILED] and [EXCEPTION] listeners will **NOT** trigger [DISCONNECTED] listener.
     */
    DISCONNECTED,

    /**
     * During netty initializing connecting phase, if connect to server failed, the connecting state will be assigned in this status.
     * It will be fired if try to connect to server failed(Example, Server down, invalid ip or port and etc.) or retry to connect failed.
     *
     * Once connecting is in this status, [DISCONNECTED] and [EXCEPTION] listeners will **NOT** be triggered.
     */
    FAILED,

    /**
     * After connecting server successfully, if any error occurred while connecting, for example: Network disconnected, this status will be assigned.
     *
     * Once connecting is in this status, [DISCONNECTED] and [FAILED] listeners will **NOT** be triggered either.
     */
    EXCEPTION
}