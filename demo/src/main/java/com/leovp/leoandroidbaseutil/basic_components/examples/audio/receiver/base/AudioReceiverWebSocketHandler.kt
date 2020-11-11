package com.leovp.leoandroidbaseutil.basic_components.examples.audio.receiver.base

import com.leovp.socket_sdk.framework.server.BaseNettyServer
import com.leovp.socket_sdk.framework.server.BaseServerChannelInboundHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.WebSocketFrame

/**
 * Author: Michael Leo
 * Date: 20-11-10 上午10:15
 */
@ChannelHandler.Sharable
class AudioReceiverWebSocketHandler(private val netty: BaseNettyServer) : BaseServerChannelInboundHandler<Any>(netty) {
    override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
        val receivedByteBuf = (msg as WebSocketFrame).content().retain()
        // Data Length
        receivedByteBuf.readIntLE()
        // Command ID
        receivedByteBuf.readByte()
        // Protocol version
        receivedByteBuf.readByte()

        runCatching {
            val bodyBytes = ByteArray(receivedByteBuf.readableBytes())
            receivedByteBuf.getBytes(6, bodyBytes)
            receivedByteBuf.release()

            netty.connectionListener.onReceivedData(netty, ctx.channel(), bodyBytes)
        }
    }

    override fun release() {
    }
}