package com.ho1ho.socket_sdk.framework.base

import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.socket_sdk.framework.base.inter.ConnectionStatus
import com.ho1ho.socket_sdk.framework.base.inter.NettyConnectionListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseChannelInboundHandler<T>(private val baseClient: BaseNettyClient) :
    SimpleChannelInboundHandler<T>() {

    private val tag = javaClass.simpleName

    @Volatile
    private var caughtException = false

    abstract fun release()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== handlerAdded(${ctx.name()}) =====")
        super.handlerAdded(ctx)
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== Channel is registered to EventLoop(${ctx.name()}) =====")
        super.channelRegistered(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== Channel is active(${ctx.name()}) Connected to: ${ctx.channel().remoteAddress()} =====")
//        mBaseClient.connectionListener?.onConnectionCreated(mBaseClient)
        caughtException = false
        baseClient.retryTimes.set(0)
        baseClient.disconnectManually = false
        super.channelActive(ctx)
        baseClient.connectState.set(ConnectionStatus.CONNECTED)
        baseClient.connectionListener.onConnected(baseClient)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        LLog.w(
            tag,
            "===== disconnectManually=${baseClient.disconnectManually} Disconnected from: ${ctx.channel()
                .remoteAddress()} | Channel is inactive and reached its end of lifetime(${ctx.name()}) ====="
        )
        super.channelInactive(ctx)

        if (!caughtException) {
            baseClient.connectState.set(ConnectionStatus.DISCONNECTED)
            baseClient.connectionListener.onDisconnected(baseClient)
            if (!baseClient.disconnectManually) {
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
        LLog.i(tag, "===== Channel is unregistered from EventLoop(${ctx.name()}) =====")
        super.channelUnregistered(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        LLog.i(tag, "===== handlerRemoved(${ctx.name()}) =====")
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

    override fun channelRead0(ctx: ChannelHandlerContext, msg: T) {
    }
}