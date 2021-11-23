package com.leovp.socket_sdk.framework.server

import com.leovp.log_sdk.LogContext
import com.leovp.socket_sdk.framework.base.ReadSocketDataListener
import com.leovp.socket_sdk.framework.base.ServerConnectStatus
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.websocketx.*
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.GlobalEventExecutor
import java.io.IOException
import java.net.URI

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午8:18
 */
abstract class BaseServerChannelInboundHandler<T>(private val netty: BaseNettyServer) : SimpleChannelInboundHandler<T>(),
    ReadSocketDataListener<T> {
    private val tag = netty.tag

    // All client channels
    @Suppress("WeakerAccess")
    val clients: ChannelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

    private var channelPromise: ChannelPromise? = null
    private var handshaker: WebSocketClientHandshaker? = null

    abstract fun release()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerAdded =====")
        super.handlerAdded(ctx)
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== Channel is registered to EventLoop =====")
        super.channelRegistered(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== Client Channel is active: ${ctx.channel().remoteAddress()} =====")
        val clientChannel = ctx.channel()
        // Add active client to server
        clients.add(clientChannel)
        super.channelActive(ctx)
        netty.connectState.set(ServerConnectStatus.CLIENT_CONNECTED)
        netty.connectionListener.onClientConnected(netty, clientChannel)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        LogContext.log.w(tag, "===== Client disconnected: ${ctx.channel().remoteAddress()} =====")
        val clientChannel = ctx.channel()
        clients.remove(clientChannel)
        if (netty.isWebSocket) {
            handshaker?.close(clientChannel, CloseWebSocketFrame())
        }
        super.channelInactive(ctx)

        netty.connectState.set(ServerConnectStatus.CLIENT_DISCONNECTED)
        netty.connectionListener.onClientDisconnected(netty, clientChannel)
    }

    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== Channel is unregistered from EventLoop =====")
        super.channelUnregistered(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerRemoved =====")
        super.handlerRemoved(ctx)
    }

    /**
     * Call [ctx.close().syncUninterruptibly()] synchronized.
     */
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val exceptionType = when (cause) {
            is IOException -> "IOException"
            is IllegalArgumentException -> "IllegalArgumentException"
            else -> "Unknown Exception"
        }
        LogContext.log.e(tag, "===== Caught $exceptionType =====")
        LogContext.log.e(tag, "Exception: ${cause.message}", cause)

//        val channel = ctx.channel()
//        val isChannelActive = channel.isActive
//        LogContext.log.e(tagName, "Channel is active: $isChannelActive")
//        if (isChannelActive) {
//            ctx.close()
//        }
        ctx.close().syncUninterruptibly()

        netty.connectState.set(ServerConnectStatus.FAILED)
        netty.connectionListener.onStartFailed(netty, ServerConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION, "Caught exception")

        LogContext.log.e(tag, "============================")
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        LogContext.log.i(tag, "===== userEventTriggered ($evt) =====")
        super.userEventTriggered(ctx, evt)
    }

    /**
     * DO NOT override this method
     */
    override fun channelRead0(ctx: ChannelHandlerContext, msg: T) {
        if (netty.isWebSocket) {
            // Process the handshake from client to server
            if (msg is FullHttpResponse) {
                LogContext.log.i(tag, "Response status=${msg.status()} isSuccess=${msg.decoderResult().isSuccess} protocolVersion=${msg.protocolVersion()}")
                handleHttpRequest(ctx, msg.retain())
                return
            }

            // The following codes process WebSocket connection
            val frame = msg as WebSocketFrame
            if (frame is CloseWebSocketFrame) {
                LogContext.log.w(tag, "=====> WebSocket Client received close frame <=====")
                ctx.channel().close()
                return
            }

//        val receivedData = when (frame) {
//            is BinaryWebSocketFrame -> {
//                frame.content().retain()
//            }
//            is TextWebSocketFrame -> {
//                frame.text().retain()
//            }
//            is PingWebSocketFrame -> {
//                frame.content().retain().toString(Charset.forName("UTF-8"))
//            }
//            is PongWebSocketFrame -> {
//                frame.content().retain().toString(Charset.forName("UTF-8"))
//            }
//            else -> {
//                null
//            }
//        }

//            if (handshaker?.isHandshakeComplete == false) {
//                try {
//                    handshaker?.finishHandshake(ctx.channel(), msg as FullHttpResponse)
//                    LogContext.log.w(tag, "=====> WebSocket client connected <=====")
//                    channelPromise?.setSuccess()
//                } catch (e: WebSocketHandshakeException) {
//                    LogContext.log.e(tag, "=====> WebSocket client failed to connect <=====")
//                    channelPromise?.setFailure(e)
//                }
//                return
//            }
        }

        onReceivedData(ctx, msg)
    }

    /**
     * WebSocket request is something like this:
     *
     * GET ws://websocket.example.com/ HTTP/1.1
     * Origin: http://example.com
     * Connection: Upgrade
     * Host: websocket.example.com
     * Upgrade: websocket
     *
     * HTTP/1.1 101 WebSocket Protocol Handshake
     * Date: Sun, 20 Nov 2016 12:45:56 GMT
     * Connection: Upgrade
     * Upgrade: WebSocket
     */
    private fun handleHttpRequest(ctx: ChannelHandlerContext, msg: FullHttpResponse) {
        if (msg.decoderResult().isFailure || !"websocket".equals(msg.headers().get("Upgrade"), ignoreCase = true)) {
            if (msg.decoderResult().isFailure || !"websocket".equals(msg.headers().get("Upgrade"), ignoreCase = true)) {
                val exceptionInfo = "Unexpected FullHttpResponse (getStatus=${msg.status()}, content=${msg.content().toString(CharsetUtil.UTF_8)})"
                LogContext.log.e(tag, exceptionInfo)
                throw IllegalStateException(exceptionInfo)
            }
            return
        }
        handshaker = WebSocketClientHandshakerFactory.newHandshaker(
            // FIXME what about wss?
//            msg.headers().get(HttpHeaderNames.HOST)
            URI("ws://${ctx.channel()}/${netty.webSocketPath}"),
            WebSocketVersion.V13,
            null,
            false,
            DefaultHttpHeaders(),
            1024 * 1024 /*5 * 65536*/
        )
        channelPromise = ctx.newPromise()
        handshaker?.handshake(ctx.channel())
    }
}
