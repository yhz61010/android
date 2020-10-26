package com.leovp.socket_sdk.framework.client

import com.leovp.androidbase.utils.log.LogContext
import com.leovp.socket_sdk.framework.base.ClientConnectStatus
import com.leovp.socket_sdk.framework.base.ReadSocketDataListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.websocketx.*
import io.netty.util.CharsetUtil
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseClientChannelInboundHandler<T>(private val netty: BaseNettyClient) : SimpleChannelInboundHandler<T>(),
    ReadSocketDataListener<T> {
    private val tag = netty.tag

    internal var channelPromise: ChannelPromise? = null
    private var handshaker: WebSocketClientHandshaker? = null

    @Volatile
    private var caughtException = false

    abstract fun release()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerAdded =====")
        if (netty.isWebSocket) {
            handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                netty.webSocketUri,
                WebSocketVersion.V13,
                null,
                false,
                DefaultHttpHeaders(),
                1024 * 1024 /*5 * 65536*/
            )
            channelPromise = ctx.newPromise()
        }
        super.handlerAdded(ctx)
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== Channel is registered to EventLoop =====")
        super.channelRegistered(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== Channel is active Connected to: ${ctx.channel().remoteAddress()} =====")
        caughtException = false
        if (netty.isWebSocket) {
            handshaker?.handshake(ctx.channel())
        }
        super.channelActive(ctx)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        LogContext.log.w(
            tag,
            "===== disconnectManually=${netty.disconnectManually} caughtException=$caughtException Disconnected from: ${
                ctx.channel().remoteAddress()
            } | Channel is inactive and reached its end of lifetime ====="
        )
        if (netty.isWebSocket) {
            handshaker?.close(ctx.channel(), CloseWebSocketFrame())
        }
        super.channelInactive(ctx)

        if (!caughtException) {
            if (netty.disconnectManually) {
                netty.connectState.set(ClientConnectStatus.DISCONNECTED)
                netty.connectionListener.onDisconnected(netty)
            } else {
                netty.connectState.set(ClientConnectStatus.FAILED)
                netty.connectionListener.onFailed(netty, ClientConnectListener.CONNECTION_ERROR_CONNECTION_DISCONNECT, "Disconnect")
                netty.doRetry()
            }
            LogContext.log.w(tag, "=====> Socket disconnected <=====")
        } else {
            LogContext.log.e(tag, "Caught socket exception! DO NOT fire onDisconnect() method!")
        }
    }

    /**
     * According to the [official example](https://github.com/netty/netty/blob/master/example/src/main/java/io/netty/example/uptime/UptimeClientHandler.java), if connection is disconnected after connecting,
     * reconnect it here.
     */
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
        if (caughtException) {
            LogContext.log.e(tag, "exceptionCaught had been triggered. Do not fire it again.")
            return
        }
        caughtException = true
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
        runCatching {
            ctx.close().syncUninterruptibly()
        }.onFailure {
            LogContext.log.e(tag, "close channel error.", it)
            it.printStackTrace()
        }

        if ("IOException" == exceptionType) {
            netty.connectState.set(ClientConnectStatus.FAILED)
            netty.connectionListener.onFailed(netty, ClientConnectListener.CONNECTION_ERROR_NETWORK_LOST, "Network lost")
            netty.doRetry()
        } else {
            netty.connectState.set(ClientConnectStatus.FAILED)
            netty.connectionListener.onFailed(netty, ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION, "Unexpected error")
        }

        LogContext.log.e(tag, "============================")
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        LogContext.log.i(tag, "===== userEventTriggered ($evt)=====")
        super.userEventTriggered(ctx, evt)
    }

    /**
     * DO NOT override this method
     */
    override fun channelRead0(ctx: ChannelHandlerContext, msg: T) {
        if (netty.isWebSocket) {
            if (msg is FullHttpResponse) {
                LogContext.log.i(tag, "Response status=${msg.status()} isSuccess=${msg.decoderResult().isSuccess} protocolVersion=${msg.protocolVersion()}")
                if (msg.decoderResult().isFailure || !"websocket".equals(msg.headers().get("Upgrade"), ignoreCase = true)) {
                    val exceptionInfo =
                        "Unexpected FullHttpResponse (getStatus=${msg.status()}, content=${msg.content().toString(CharsetUtil.UTF_8)})"
                    LogContext.log.e(tag, exceptionInfo)
                    throw IllegalStateException(exceptionInfo)
                }
            }

            if (handshaker?.isHandshakeComplete == false) {
                try {
                    handshaker?.finishHandshake(ctx.channel(), msg as FullHttpResponse)
                    LogContext.log.w(tag, "=====> WebSocket client connected <=====")
                    channelPromise?.setSuccess()
                } catch (e: WebSocketHandshakeException) {
                    LogContext.log.e(tag, "=====> WebSocket client failed to connect <=====")
                    channelPromise?.setFailure(e)
                }
                return
            }

            val frame = msg as WebSocketFrame
            if (frame is CloseWebSocketFrame) {
                LogContext.log.w(tag, "=====> WebSocket Client received close frame <=====")
                ctx.channel().close()
                return
            }
        }

        onReceivedData(ctx, msg)
    }
}