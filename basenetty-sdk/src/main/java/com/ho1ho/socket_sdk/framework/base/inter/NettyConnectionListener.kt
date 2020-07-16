package com.ho1ho.socket_sdk.framework.base.inter

import com.ho1ho.socket_sdk.framework.base.BaseNettyClient
import io.netty.channel.ChannelHandlerContext

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
interface NettyConnectionListener {

    fun onConnecting(client: BaseNettyClient)
    fun onConnected(client: BaseNettyClient)
    fun onDisconnected(client: BaseNettyClient)
    fun onFailed(client: BaseNettyClient)
    fun onException(client: BaseNettyClient, cause: Throwable)

    //    fun onConnectionTimeout()

    // =========================================
//    fun sendHeartbeat() {}
//    fun onReceiveHeartbeat(msg: Any?) {}

    // =========================================
    fun onReaderIdle(ctx: ChannelHandlerContext?) {}
    fun onWriterIdle(ctx: ChannelHandlerContext?) {}
    fun onAllIdle(ctx: ChannelHandlerContext?) {}
}