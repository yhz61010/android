package com.ho1ho.socket_sdk.framework.base.inter

import com.ho1ho.socket_sdk.framework.base.BaseNettyClient
import io.netty.channel.ChannelHandlerContext

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
interface NettyConnectionListener {

    fun onConnectionConnecting(client: BaseNettyClient)
    fun onConnectionCreated(client: BaseNettyClient)
    fun onConnectionDisconnect(client: BaseNettyClient)
    fun onConnectionFailed(client: BaseNettyClient)

    //    fun onConnectionTimeout()
    fun onCaughtException(client: BaseNettyClient, cause: Throwable)

    // =========================================
//    fun sendHeartbeat() {}
//    fun onReceiveHeartbeat(msg: Any?) {}

    // =========================================
    fun onReaderIdle(ctx: ChannelHandlerContext?) {}
    fun onWriterIdle(ctx: ChannelHandlerContext?) {}
    fun onAllIdle(ctx: ChannelHandlerContext?) {}
}