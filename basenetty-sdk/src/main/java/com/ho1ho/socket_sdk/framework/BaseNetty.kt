package com.ho1ho.socket_sdk.framework

import io.netty.channel.ChannelHandlerContext

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午2:53
 */
abstract class BaseNetty

interface ReadSocketDataListener<T> {
    fun onReceivedData(ctx: ChannelHandlerContext, msg: T)
}
