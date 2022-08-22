package com.leovp.basenetty.framework.client

import com.leovp.basenetty.framework.base.ClientConnectStatus
import com.leovp.basenetty.framework.base.ReadSocketDataListener
import com.leovp.log.LogContext
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.util.CharsetUtil
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseClientChannelInboundHandler<T>(private val netty: BaseNettyClient) :
    SimpleChannelInboundHandler<T>(),
    ReadSocketDataListener<T> {
    private val tag = netty.tag

    internal var channelPromise: ChannelPromise? = null
    private var handshaker: WebSocketClientHandshaker? = null

    @Volatile
    private var caughtException = false

    /**
     * When client is disconnected by manually or released, this method will be called.
     */
    abstract fun release()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerAdded =====")
        if (netty.isWebSocket) {
            val headers = DefaultHttpHeaders()
            netty.setHeaders(headers)
            handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                netty.webSocketUri,
                WebSocketVersion.V13,
                null,
                // FIXME Need to set this value dynamically
                true,
                headers,
                1 shl 20
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
        LogContext.log.i(tag, "===== Channel is active. Connected to: ${ctx.channel().remoteAddress()} =====")
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
            "===== Channel is inactive and reached its end of lifetime | " +
                "disconnectManually=${netty.disconnectManually} caughtException=$caughtException " +
                "Disconnected from: ${ctx.channel().remoteAddress()}  ====="
        )
        if (netty.isWebSocket) {
            LogContext.log.i(tag, "Closing handshaker for websocket")
            runCatching { handshaker?.close(ctx.channel(), CloseWebSocketFrame()) }.onFailure { it.printStackTrace() }
        }
        super.channelInactive(ctx)
    }

    /**
     * According to the
     * [official example](https://github.com/netty/netty/blob/master/example/src/main/java/io/netty/example/uptime/UptimeClientHandler.java),
     * if connection is disconnected after connecting,
     * reconnect it here.
     */
    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== Channel is unregistered from EventLoop =====")
        super.channelUnregistered(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerRemoved =====")
        super.handlerRemoved(ctx)

        // In theory, we should do reconnect in channelUnregistered. However, according to our business requirement(only one single user logged-in allowed),
        // I must do reconnect here to make sure worker thread had already been released.
        if (!caughtException) {
            if (netty.disconnectManually) {
                LogContext.log.i(tag, "handlerRemoved(disconnect) manually=${netty.disconnectManually}")
                //                netty.connectState.set(ClientConnectState.DISCONNECTED)
                //                netty.connectionListener.onDisconnected(netty)
            } else {
                LogContext.log.i(tag, "Set failed exception status.")
                netty.connectStatus.set(ClientConnectStatus.FAILED)
                netty.connectionListener.onFailed(
                    netty,
                    ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION,
                    "Connect exception or disconnect"
                )
                // For instance, "Unable to resolve host xxx" error will go into here when you connect to server without network.
                //                LogContext.log.e(tag, "=====> CHK11 <=====")
                netty.doRetry()
            }
            LogContext.log.w(tag, "=====> Socket disconnected <=====")
        } else {
            LogContext.log.e(tag, "Caught socket exception! DO NOT fire ClientConnectListener#onDisconnected() method!")
            netty.connectStatus.set(ClientConnectStatus.FAILED)
            netty.connectionListener.onFailed(netty, ClientConnectListener.CONNECTION_ERROR_SOCKET_EXCEPTION, "Socket Exception")
            // When network lost, you will go into here.
            //            LogContext.log.e(tag, "=====> CHK13 <=====")
            netty.doRetry()
        }
    }

    /**
     * Call [ctx.close().syncUninterruptibly()] synchronized.
     */
    @Deprecated("Deprecated in Java")
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
            ctx.close().sync()
        }.onFailure {
            LogContext.log.e(tag, "close channel error.", it)
            it.printStackTrace()
        }

        LogContext.log.e(tag, "============================")

        if ("IOException" == exceptionType) {
            netty.connectStatus.set(ClientConnectStatus.FAILED)
            LogContext.log.w(tag, "Network lost")
            // This exception will trigger handlerRemoved(), so we retry at that time.

            //            netty.connectionListener.onFailed(netty, ClientConnectListener.CONNECTION_ERROR_NETWORK_LOST, "Network lost")
            //            LogContext.log.e(tag, "=====> CHK12 <=====")
            //            netty.doRetry()
        } else {
            netty.connectStatus.set(ClientConnectStatus.FAILED)
            netty.connectionListener.onFailed(netty, ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION, "Unexpected error", cause)
        }
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
            if (handshaker?.isHandshakeComplete == false) {
                try {
                    handshaker?.finishHandshake(ctx.channel(), msg as FullHttpResponse)
                    LogContext.log.i(tag, "===== WebSocket hand shake finished =====")
                    channelPromise?.setSuccess()
                } catch (e: WebSocketHandshakeException) {
                    LogContext.log.e(tag, "===== WebSocket hand shake failed =====", e)
                    channelPromise?.setFailure(e)
                }
                return
            }

            if (msg is FullHttpResponse) {
                // LogContext.log.i(tag, "Response status=${msg.status()} isSuccess=${msg.decoderResult().isSuccess} protocolVersion=${msg.protocolVersion()}")
                // if (msg.decoderResult().isFailure || !"websocket".equals(msg.headers().get("Upgrade"), ignoreCase = true)) {
                val exceptionInfo = "Unexpected FullHttpResponse (getStatus=${msg.status()}, " +
                    "content=${msg.content().toString(CharsetUtil.UTF_8)}) " +
                    "isSuccess=${msg.decoderResult().isSuccess} protocolVersion=${msg.protocolVersion()}"
                LogContext.log.e(tag, exceptionInfo)
                throw IllegalStateException(exceptionInfo)
            }

            val frame = msg as WebSocketFrame
            if (frame is CloseWebSocketFrame) {
                LogContext.log.w(tag, "=====> WebSocket Client received close frame <=====")
                ctx.channel().close()
                netty.connectStatus.set(ClientConnectStatus.DISCONNECTED)
                netty.connectionListener.onDisconnected(netty, true)
                return
            }
        }

        onReceivedData(ctx, msg)
    }
}
