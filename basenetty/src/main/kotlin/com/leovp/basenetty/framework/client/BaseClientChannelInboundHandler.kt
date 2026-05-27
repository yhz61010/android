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
        LogContext.log.i(
            tag,
            "===== Channel is active. Connected to: ${ctx.channel().remoteAddress()} ====="
        )
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
            runCatching {
                handshaker?.close(ctx.channel(), CloseWebSocketFrame())
            }.onFailure {
                LogContext.log.e(tag, "Closing handshaker for websocket", it)
            }
        }
        super.channelInactive(ctx)
    }

    /**
     * According to the
     * [official example]
     * (https://github.com/netty/netty/blob/master/example/src/main/java/io/netty/example/uptime
     * /UptimeClientHandler.java),
     * if connection is disconnected after connecting,
     * reconnect it here.
     */
    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== Channel is unregistered from EventLoop =====")
        super.channelUnregistered(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerRemoved =====  caughtException=$caughtException")

        // No matter which side is lost network, the `caughtException` will be `true`.

        super.handlerRemoved(ctx)

        // In theory, we should do reconnect in channelUnregistered. However, according to our
        // business requirement(only one single user logged-in allowed),
        // I must do reconnect here to make sure worker thread had already been released.
        if (!caughtException) {
            val status = netty.connectStatus.get()
            LogContext.log.i(
                tag,
                "handlerRemoved(disconnect) " +
                    "manually=${netty.disconnectManually} status=${status.name}"
            )
            if (!netty.disconnectManually && status != ClientConnectStatus.DISCONNECTED) {
                if (status == ClientConnectStatus.FAILED) {
                    // connect() already called onFailed; just retry without duplicate callback.
                    LogContext.log.i(
                        tag,
                        "handlerRemoved: connect already reported failure, retrying"
                    )
                    netty.doRetry()
                } else {
                    LogContext.log.i(tag, "Set failed exception status.")
                    netty.connectStatus.set(ClientConnectStatus.FAILED)
                    netty.connectionListener.onFailed(
                        netty,
                        ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION,
                        "Connect exception or disconnect"
                    )
                    netty.doRetry()
                }
            }
            // In else block, this means the client is stopped manually.
            LogContext.log.w(tag, "=====> Socket disconnected <=====")
        } else {
            val status = netty.connectStatus.get()
            if (netty.disconnectManually ||
                status == ClientConnectStatus.DISCONNECTING ||
                status == ClientConnectStatus.DISCONNECTED
            ) {
                LogContext.log.i(
                    tag,
                    "handlerRemoved(exception) ignored because " +
                        "disconnect is already handled. " +
                        "manually=${netty.disconnectManually} " +
                        "status=${status.name}"
                )
            } else if (status == ClientConnectStatus.FAILED) {
                // exceptionCaught() already called onFailed for non-IOException;
                // just retry without duplicate callback.
                // For IOException, exceptionCaught() only sets FAILED and defers
                // onFailed + doRetry to here.
                LogContext.log.i(
                    tag,
                    "handlerRemoved(exception): failure already reported, retrying"
                )
                netty.doRetry()
            } else {
                LogContext.log.e(
                    tag,
                    "Caught socket exception! DO NOT fire " +
                        "ClientConnectListener#onDisconnected() method!"
                )
                netty.connectStatus.set(ClientConnectStatus.FAILED)
                netty.connectionListener.onFailed(
                    netty,
                    ClientConnectListener.CONNECTION_ERROR_SOCKET_EXCEPTION,
                    "Socket Exception"
                )
                // When network lost, you will go into here.
                netty.doRetry()
            }
        }
    }

    /** Close asynchronously; channelInactive/handlerRemoved handle cleanup. */
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
        ctx.close()

        LogContext.log.e(tag, "============================")

        // Set FAILED and report onFailed here; handlerRemoved() sees FAILED status
        // and only calls doRetry() without duplicate onFailed callback.
        // Skip onFailed if disconnect is already in progress (manual disconnect or releasing).
        val status = netty.connectStatus.get()
        if (netty.disconnectManually ||
            status == ClientConnectStatus.DISCONNECTING ||
            status == ClientConnectStatus.DISCONNECTED
        ) {
            LogContext.log.i(
                tag,
                "exceptionCaught: skipping onFailed because disconnect is in progress. " +
                    "manually=${netty.disconnectManually} status=${status.name}"
            )
            return
        }
        netty.connectStatus.set(ClientConnectStatus.FAILED)
        if ("IOException" == exceptionType) {
            LogContext.log.w(tag, "Network lost")
            netty.connectionListener.onFailed(
                netty,
                ClientConnectListener.CONNECTION_ERROR_NETWORK_LOST,
                "Network lost",
                cause
            )
        } else {
            netty.connectionListener.onFailed(
                netty,
                ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION,
                "Unexpected error",
                cause
            )
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
            // Handle CloseFrame first — even before handshake completes — so state is set
            // correctly before channel.close() triggers handlerRemoved.
            if (msg is CloseWebSocketFrame) {
                LogContext.log.w(tag, "=====> WebSocket Client received close frame <=====")
                netty.connectStatus.set(ClientConnectStatus.DISCONNECTED)
                netty.connectionListener.onDisconnected(netty, true)
                ctx.channel().close()
                return
            }

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
                // LogContext.log.i(tag, "Response status=${msg.status()}
                // isSuccess=${msg.decoderResult().isSuccess}
                // protocolVersion=${msg.protocolVersion()}")
                // if (msg.decoderResult().isFailure ||
                // !"websocket".equals(msg.headers().get("Upgrade"), ignoreCase = true)) {
                val exceptionInfo = "Unexpected FullHttpResponse (getStatus=${msg.status()}, " +
                    "content=${msg.content().toString(CharsetUtil.UTF_8)}) " +
                    "isSuccess=${msg.decoderResult().isSuccess} " +
                    "protocolVersion=${msg.protocolVersion()}"
                LogContext.log.e(tag, exceptionInfo)
                throw IllegalStateException(exceptionInfo)
            }
        }

        onReceivedData(ctx, msg)
    }
}
