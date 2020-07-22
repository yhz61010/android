package com.ho1ho.socket_sdk.framework.base

import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.socket_sdk.framework.base.inter.ConnectionStatus
import com.ho1ho.socket_sdk.framework.base.inter.NettyConnectionListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.websocketx.*
import io.netty.util.CharsetUtil
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseChannelInboundHandler<T>(private val baseClient: BaseNettyClient) :
    SimpleChannelInboundHandler<T>(), ReadSocketDataListener<T> {
    private val tag = javaClass.simpleName

    private var handshakeFuture: ChannelPromise? = null
    private val handshaker: WebSocketClientHandshaker? by lazy {
        if (baseClient.isWebSocket) {
            val httpHeaders: HttpHeaders = DefaultHttpHeaders()
            WebSocketClientHandshakerFactory.newHandshaker(
                baseClient.webSocketUri,
                WebSocketVersion.V13,
                null,
                false,
                httpHeaders,
                1024 * 1024 /*5 * 65536*/
            )
        } else {
            null
        }
    }

    @Volatile
    private var caughtException = false

    abstract fun release()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== handlerAdded =====")
        if (baseClient.isWebSocket) {
            handshakeFuture = ctx.newPromise()
        }
        super.handlerAdded(ctx)
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== Channel is registered to EventLoop =====")
        super.channelRegistered(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== Channel is active Connected to: ${ctx.channel().remoteAddress()} =====")
        caughtException = false
        baseClient.retryTimes.set(0)
        baseClient.disconnectManually = false
        if (baseClient.isWebSocket) {
            handshaker?.handshake(ctx.channel())
        }
        super.channelActive(ctx)
        baseClient.connectState.set(ConnectionStatus.CONNECTED)
        baseClient.connectionListener.onConnected(baseClient)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        LLog.w(
            tag,
            "===== disconnectManually=${baseClient.disconnectManually} caughtException=$caughtException Disconnected from: ${ctx.channel()
                .remoteAddress()} | Channel is inactive and reached its end of lifetime ====="
        )
        super.channelInactive(ctx)

        if (!caughtException) {
            if (baseClient.disconnectManually) {
                baseClient.connectState.set(ConnectionStatus.DISCONNECTED)
                baseClient.connectionListener.onDisconnected(baseClient)
            } else { // Server down
                baseClient.connectState.set(ConnectionStatus.FAILED)
                baseClient.connectionListener.onFailed(baseClient, NettyConnectionListener.CONNECTION_ERROR_SERVER_DOWN, "Server down")
                baseClient.doRetry()
            }
            LLog.w(tag, "=====> Socket disconnected <=====")
        } else {
            LLog.e(tag, "Caught socket exception! DO NOT fire onDisconnect() method!")
        }
    }

    /**
     * According to the [official example](https://github.com/netty/netty/blob/master/example/src/main/java/io/netty/example/uptime/UptimeClientHandler.java), if connection is disconnected after connecting,
     * reconnect it here.
     */
    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== Channel is unregistered from EventLoop =====")
        super.channelUnregistered(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== handlerRemoved =====")
        super.handlerRemoved(ctx)
    }

    /**
     * Call [ctx.close().syncUninterruptibly()] synchronized.
     */
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (caughtException) {
            LLog.e(tag, "exceptionCaught had been triggered. Do not fire it again.")
            return
        }
        caughtException = true
        val exceptionType = when (cause) {
            is IOException -> "IOException"
            is IllegalArgumentException -> "IllegalArgumentException"
            else -> "Unknown Exception"
        }
        LLog.e(tag, "===== Caught $exceptionType =====")
        LLog.e(tag, "Exception: ${cause.message}")

//        val channel = ctx.channel()
//        val isChannelActive = channel.isActive
//        LLog.e(tagName, "Channel is active: $isChannelActive")
//        if (isChannelActive) {
//            ctx.close()
//        }
        ctx.close().syncUninterruptibly()

        if ("IOException" == exceptionType) {
            baseClient.connectState.set(ConnectionStatus.FAILED)
            baseClient.connectionListener.onFailed(baseClient, NettyConnectionListener.CONNECTION_ERROR_NETWORK_LOST, "Network lost")
            baseClient.doRetry()
        } else {
            baseClient.connectState.set(ConnectionStatus.EXCEPTION)
            baseClient.connectionListener.onException(baseClient, cause)
        }

        LLog.e(tag, "============================")
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        LLog.i(tag, "===== userEventTriggered(${ctx.name()}) =====")
        super.userEventTriggered(ctx, evt)
    }

    /**
     * DO NOT override this method
     */
    override fun channelRead0(ctx: ChannelHandlerContext, msg: T) {
        if (msg is FullHttpResponse) {
            LLog.i(tag, "Response status=${msg.status()} isSuccess=${msg.decoderResult().isSuccess} protocolVersion=${msg.protocolVersion()}")
            if (msg.decoderResult().isFailure || !"websocket".equals(msg.headers().get("Upgrade"), ignoreCase = true)) {
                val exceptionInfo =
                    "Unexpected FullHttpResponse (getStatus=${msg.status()}, content=${msg.content().toString(CharsetUtil.UTF_8)})"
                LLog.e(tag, exceptionInfo)
                throw IllegalStateException(exceptionInfo)
            }
        }

        if (baseClient.isWebSocket) {
            if (handshaker?.isHandshakeComplete == false) {
                try {
                    handshaker?.finishHandshake(ctx.channel(), msg as FullHttpResponse)
                    LLog.w(tag, "=====> WebSocket client connected <=====")
                    handshakeFuture?.setSuccess()
                } catch (e: WebSocketHandshakeException) {
                    LLog.e(tag, "=====> WebSocket client failed to connect <=====")
                    handshakeFuture?.setFailure(e)
                }
                return
            }

            val frame = msg as WebSocketFrame
            if (frame is CloseWebSocketFrame) {
                LLog.w(tag, "=====> WebSocket Client received close frame <=====")
                ctx.channel().close()
                return
            }
        }

        onReceivedData(ctx, msg)
    }
}

interface ReadSocketDataListener<T> {
    fun onReceivedData(ctx: ChannelHandlerContext, msg: T)
}
