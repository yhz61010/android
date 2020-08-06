package com.ho1ho.socket_sdk.framework.inter

import com.ho1ho.socket_sdk.framework.BaseNetty

/**
 * Author: Michael Leo
 * Date: 20-8-6 下午6:25
 */
interface ServerConnectListener {

    fun onConnected(netty: BaseNetty)
    fun onReceivedData(netty: BaseNetty, data: Any?)
    fun onDisconnected(netty: BaseNetty)
    fun onFailed(netty: BaseNetty, code: Int, msg: String?)
    fun onException(netty: BaseNetty, cause: Throwable)

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