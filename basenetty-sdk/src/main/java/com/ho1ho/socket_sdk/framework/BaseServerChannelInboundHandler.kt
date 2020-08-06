package com.ho1ho.socket_sdk.framework

import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.socket_sdk.framework.inter.ConnectionStatus
import com.ho1ho.socket_sdk.framework.inter.NettyConnectionListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.websocketx.*
import io.netty.util.CharsetUtil
import java.io.IOException
import java.net.URI


/**
 * Author: Michael Leo
 * Date: 20-8-5 下午8:18
 */
abstract class BaseServerChannelInboundHandler<T>(private val netty: BaseNettyServer) : SimpleChannelInboundHandler<T>(), ReadSocketDataListener<T> {
    private val tag = javaClass.simpleName

    private var channelPromise: ChannelPromise? = null
    private var handshaker: WebSocketClientHandshaker? = null

    @Volatile
    private var caughtException = false

    abstract fun release()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== handlerAdded =====")
        super.handlerAdded(ctx)
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== Channel is registered to EventLoop =====")
        super.channelRegistered(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== Client Channel is active: ${ctx.channel().remoteAddress()} =====")
        // Add active client to server
        netty.clients.add(ctx.channel())
        caughtException = false
        netty.retryTimes.set(0)
        netty.disconnectManually = false
        if (netty.isWebSocket) {
            handshaker?.handshake(ctx.channel())
        }
        super.channelActive(ctx)
        netty.connectState.set(ConnectionStatus.CONNECTED)
        netty.connectionListener.onConnected(netty)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        LLog.w(
            tag,
            "===== disconnectManually=${netty.disconnectManually} caughtException=$caughtException Disconnected from: ${ctx.channel()
                .remoteAddress()} | Channel is inactive and reached its end of lifetime ====="
        )
        netty.clients.remove(ctx.channel())
        if (netty.isWebSocket) {
            handshaker?.close(ctx.channel(), CloseWebSocketFrame())
        }
        super.channelInactive(ctx)

        if (!caughtException) {
            if (netty.disconnectManually) {
                netty.connectState.set(ConnectionStatus.DISCONNECTED)
                netty.connectionListener.onDisconnected(netty)
            } else { // Server down
                netty.connectState.set(ConnectionStatus.FAILED)
                netty.connectionListener.onFailed(netty, NettyConnectionListener.CONNECTION_ERROR_SERVER_DOWN, "Server down")
                netty.doRetry()
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
        LLog.e(tag, "Exception: ${cause.message}", cause)

//        val channel = ctx.channel()
//        val isChannelActive = channel.isActive
//        LLog.e(tagName, "Channel is active: $isChannelActive")
//        if (isChannelActive) {
//            ctx.close()
//        }
        ctx.close().syncUninterruptibly()

        if ("IOException" == exceptionType) {
            netty.connectState.set(ConnectionStatus.FAILED)
            netty.connectionListener.onFailed(netty, NettyConnectionListener.CONNECTION_ERROR_NETWORK_LOST, "Network lost")
            netty.doRetry()
        } else {
            netty.connectState.set(ConnectionStatus.EXCEPTION)
            netty.connectionListener.onException(netty, cause)
        }

        LLog.e(tag, "============================")
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        LLog.i(tag, "===== userEventTriggered =====")
        super.userEventTriggered(ctx, evt)
    }

    /**
     * DO NOT override this method
     */
    override fun channelRead0(ctx: ChannelHandlerContext, msg: T) {
        if (netty.isWebSocket) {
            if (msg is FullHttpResponse) {
                LLog.i(tag, "Response status=${msg.status()} isSuccess=${msg.decoderResult().isSuccess} protocolVersion=${msg.protocolVersion()}")
                handleHttpRequest(ctx, msg.retain())
                return
            }

            val frame = msg as WebSocketFrame
            if (frame is CloseWebSocketFrame) {
                LLog.w(tag, "=====> WebSocket Client received close frame <=====")
                ctx.channel().close()
                return
            }
            //PingWebSocketFrame
//        if (frame is PingWebSocketFrame) {
//            ctx.channel().write(PongWebSocketFrame(frame.content().retain()))
//            return;
//        }

//        val receivedData = when (frame) {
//            is BinaryWebSocketFrame -> {
//                frame.content()
//            }
//            is TextWebSocketFrame -> {
//                frame.text()
//            }
//            is PingWebSocketFrame -> {
//                frame.content().toString(Charset.forName("UTF-8"))
//            }
//            is PongWebSocketFrame -> {
//                frame.content().toString(Charset.forName("UTF-8"))
//            }
//            else -> {
//                null
//            }
//        }

//            if (handshaker?.isHandshakeComplete == false) {
//                try {
//                    handshaker?.finishHandshake(ctx.channel(), msg as FullHttpResponse)
//                    LLog.w(tag, "=====> WebSocket client connected <=====")
//                    channelPromise?.setSuccess()
//                } catch (e: WebSocketHandshakeException) {
//                    LLog.e(tag, "=====> WebSocket client failed to connect <=====")
//                    channelPromise?.setFailure(e)
//                }
//                return
//            }
        }

        onReceivedData(ctx, msg)
    }

    private fun handleHttpRequest(ctx: ChannelHandlerContext, msg: FullHttpResponse) {
        if (msg.decoderResult().isFailure || !"websocket".equals(msg.headers().get("Upgrade"), ignoreCase = true)) {
            if (msg.decoderResult().isFailure || !"websocket".equals(msg.headers().get("Upgrade"), ignoreCase = true)) {
                val exceptionInfo =
                    "Unexpected FullHttpResponse (getStatus=${msg.status()}, content=${msg.content().toString(CharsetUtil.UTF_8)})"
                LLog.e(tag, exceptionInfo)
                throw IllegalStateException(exceptionInfo)
            }
            return
        }
        handshaker = WebSocketClientHandshakerFactory.newHandshaker(
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
