package com.leovp.basenetty.framework.server

import com.leovp.basenetty.framework.base.ReadSocketDataListener
import com.leovp.log.LogContext
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午8:18
 */
abstract class BaseServerChannelInboundHandler<T>(
    private val netty: BaseNettyServer
) : SimpleChannelInboundHandler<T>(), ReadSocketDataListener<T> {

    private val tag = netty.tag

    abstract fun release()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerAdded =====")
        super.handlerAdded(ctx)
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        LogContext.log.i(
            tag,
            "===== Channel is registered to EventLoop ====="
        )
        super.channelRegistered(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        LogContext.log.i(
            tag,
            "===== Client Channel is active: " +
                "${ctx.channel().remoteAddress()} ====="
        )
        val clientChannel = ctx.channel()
        netty.clients.add(clientChannel)
        super.channelActive(ctx)
        netty.connectionListener.onClientConnected(netty, clientChannel)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        LogContext.log.w(
            tag,
            "===== Client disconnected: " +
                "${ctx.channel().remoteAddress()} ====="
        )
        val clientChannel = ctx.channel()
        netty.clients.remove(clientChannel)
        super.channelInactive(ctx)
        netty.connectionListener.onClientDisconnected(
            netty, clientChannel
        )
    }

    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        LogContext.log.i(
            tag,
            "===== Channel is unregistered from EventLoop ====="
        )
        super.channelUnregistered(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerRemoved =====")
        super.handlerRemoved(ctx)
    }

    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(
        ctx: ChannelHandlerContext,
        cause: Throwable
    ) {
        val exceptionType = when (cause) {
            is IOException -> "IOException"
            is IllegalArgumentException -> "IllegalArgumentException"
            else -> "Unknown Exception"
        }
        LogContext.log.e(tag, "===== Caught $exceptionType =====")
        LogContext.log.e(tag, "Exception: ${cause.message}", cause)
        ctx.close()
        LogContext.log.e(tag, "============================")
    }

    override fun userEventTriggered(
        ctx: ChannelHandlerContext,
        evt: Any?
    ) {
        LogContext.log.i(
            tag, "===== userEventTriggered ($evt) ====="
        )
        super.userEventTriggered(ctx, evt)
    }

    /**
     * DO NOT override this method
     */
    override fun channelRead0(ctx: ChannelHandlerContext, msg: T) {
        if (netty.isWebSocket) {
            val frame = msg as WebSocketFrame
            if (frame is CloseWebSocketFrame) {
                LogContext.log.w(
                    tag,
                    "=====> WebSocket Client received close frame <====="
                )
                ctx.channel().close()
                return
            }
        }
        onReceivedData(ctx, msg)
    }
}
