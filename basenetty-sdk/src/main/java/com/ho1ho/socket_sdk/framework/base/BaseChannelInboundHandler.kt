package com.ho1ho.socket_sdk.framework.base

import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.CLog
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseChannelInboundHandler<T>(protected val mBaseClient: BaseNettyClient) :
    SimpleChannelInboundHandler<T>() {

    abstract fun release()

    private var caughtException = false

    @Throws(Exception::class)
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        CLog.i(ITAG, "===== handlerAdded(${ctx.name()}) =====")
        super.handlerAdded(ctx)
    }

    @Throws(Exception::class)
    override fun channelRegistered(ctx: ChannelHandlerContext) {
        CLog.i(ITAG, "===== Channel is registered to EventLoop(${ctx.name()}) =====")
        super.channelRegistered(ctx)
    }

    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext) {
        CLog.i(ITAG, "===== Channel is active(${ctx.name()}) =====")
//        mBaseClient.connectionListener?.onConnectionCreated(mBaseClient)
        super.channelActive(ctx)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        CLog.w(ITAG, "===== Channel is inactive and reached its end of lifetime(${ctx.name()}) =====")
        if (!caughtException) {
            mBaseClient.connectState = BaseNettyClient.DISCONNECTED
            mBaseClient.connectionListener?.onConnectionDisconnect(mBaseClient)
        } else {
            CLog.e(ITAG, "Caught socket exception! DO NOT fire onConnectionDisconnect() method!")
        }
        super.channelInactive(ctx)
    }

    @Throws(Exception::class)
    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        CLog.i(ITAG, "===== Channel is unregistered from EventLoop(${ctx.name()}) =====")
        super.channelUnregistered(ctx)
    }

    @Throws(Exception::class)
    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        CLog.i(ITAG, "===== handlerRemoved(${ctx.name()}) =====")
        super.handlerRemoved(ctx)
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        caughtException = true
        CLog.e(ITAG, "===== Caught Exception =====")
        CLog.e(ITAG, "Exception: ${cause.message}")
        CLog.e(ITAG, "============================")

        val channel = ctx.channel()
        val isChannelActive = channel.isActive
        CLog.e(ITAG, "Channel is active: $isChannelActive")
        if (isChannelActive) {
            ctx.close()
        }
        when (cause) {
            is IOException -> {
                CLog.e(ITAG, "===== Caught IOException =====")
            }
            is IllegalArgumentException -> {
                CLog.e(ITAG, "IllegalArgumentException msg=${cause.message}")
            }
            else -> {
                CLog.e(ITAG, "Unknown Exception msg=${cause.message}")
            }
        }
        mBaseClient.connectState = BaseNettyClient.CONNECT_EXCEPTION
        mBaseClient.connectionListener?.onCaughtException(mBaseClient, cause)
    }

    @Throws(Exception::class)
    override fun channelRead0(ctx: ChannelHandlerContext, msg: T) {
    }
}