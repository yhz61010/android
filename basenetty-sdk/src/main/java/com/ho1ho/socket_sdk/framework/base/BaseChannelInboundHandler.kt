package com.ho1ho.socket_sdk.framework.base

import com.ho1ho.androidbase.utils.LLog
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseChannelInboundHandler<T>(private val baseClient: BaseNettyClient) :
    SimpleChannelInboundHandler<T>() {

    private val tagName = javaClass.simpleName

    @Volatile
    private var caughtException = false

    abstract fun release()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        LLog.i(tagName, "===== handlerAdded(${ctx.name()}) =====")
        super.handlerAdded(ctx)
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        LLog.i(tagName, "===== Channel is registered to EventLoop(${ctx.name()}) =====")
        super.channelRegistered(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        LLog.i(tagName, "===== Channel is active(${ctx.name()}) Connected to: ${ctx.channel().remoteAddress()} =====")
//        mBaseClient.connectionListener?.onConnectionCreated(mBaseClient)
        super.channelActive(ctx)
        baseClient.connectState = BaseNettyClient.STATUS_CONNECTED
        baseClient.connectionListener.onConnected(baseClient)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        LLog.i(
            tagName,
            "===== Disconnected from: ${ctx.channel().remoteAddress()} | Channel is inactive and reached its end of lifetime(${ctx.name()}) ====="
        )
        super.channelInactive(ctx)

        if (!caughtException) {
            baseClient.connectState = BaseNettyClient.STATUS_DISCONNECTED
            baseClient.connectionListener.onDisconnected(baseClient)
            LLog.w(tagName, "=====> Socket disconnected <=====")
        } else {
            LLog.e(tagName, "Caught socket exception! DO NOT fire onDisconnect() method!")
        }
    }

    /**
     * According to the [official example](https://github.com/netty/netty/blob/master/example/src/main/java/io/netty/example/uptime/UptimeClientHandler.java), if connection is disconnected after connecting,
     * reconnect it here.
     */
    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        LLog.i(tagName, "===== Channel is unregistered from EventLoop(${ctx.name()}) =====")
        super.channelUnregistered(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        LLog.i(tagName, "===== handlerRemoved(${ctx.name()}) =====")
        super.handlerRemoved(ctx)
    }

    /**
     * Call [ctx.close().syncUninterruptibly()] synchronized.
     */
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (caughtException) {
            LLog.e(tagName, "exceptionCaught had been triggered. Do not fire it again.")
            return
        }
        caughtException = true
        val exceptionType = when (cause) {
            is IOException -> "IOException"
            is IllegalArgumentException -> "IllegalArgumentException"
            else -> "Unknown Exception"
        }
        LLog.e(tagName, "===== Caught $exceptionType =====")
        LLog.e(tagName, "Exception: ${cause.message}")

//        val channel = ctx.channel()
//        val isChannelActive = channel.isActive
//        LLog.e(tagName, "Channel is active: $isChannelActive")
//        if (isChannelActive) {
//            ctx.close()
//        }
        ctx.close().syncUninterruptibly()

        baseClient.connectState = BaseNettyClient.STATUS_CONNECT_EXCEPTION
        baseClient.connectionListener.onException(baseClient, cause)
        LLog.e(tagName, "============================")
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        LLog.i(tagName, "===== userEventTriggered(${ctx.name()}) =====")
        super.userEventTriggered(ctx, evt)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: T) {
    }
}