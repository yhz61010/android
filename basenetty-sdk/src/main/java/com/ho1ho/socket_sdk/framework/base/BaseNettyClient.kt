package com.ho1ho.socket_sdk.framework.base

import android.os.Handler
import android.os.HandlerThread
import com.ho1ho.androidbase.exts.toHexStringLE
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.socket_sdk.framework.base.inter.ConnectionStatus
import com.ho1ho.socket_sdk.framework.base.inter.NettyConnectionListener
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import java.net.ConnectException
import java.util.concurrent.RejectedExecutionException

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseNettyClient protected constructor(
    private val host: String,
    private val port: Int,
    val connectionListener: NettyConnectionListener
) {
    init {
        init()
    }

    private lateinit var bootstrap: Bootstrap
    private lateinit var eventLoopGroup: EventLoopGroup
    private var channel: Channel? = null

    private var channelInitializer: ChannelInitializer<SocketChannel>? = null
    var defaultChannelHandler: BaseChannelInboundHandler<*>? = null
        private set

//    var receivingDataListener: ReceivingDataListener? = null

    @Volatile
    var disconnectManually = false

    @Volatile
    var connectState: ConnectionStatus = ConnectionStatus.UNINITIALIZED
    private val retryThread = HandlerThread("retry-thread").apply { start() }
    private val retryHandler = Handler(retryThread.looper)
    private var retryTimes = 0

//    private val connectFutureListener: ChannelFutureListener = ChannelFutureListener { future ->
//        if (future.isSuccess) {
//            stopRetryHandler()
//            channel = future.syncUninterruptibly().channel()
//            LLog.i(TAG, "===== Connect success =====")
//        } else {
//            LLog.e(TAG, "Retry due to connect failed. Reason: ${future.cause()}")
//            doRetry()
//        }
//    }

    private fun init() {
        bootstrap = Bootstrap()
        eventLoopGroup = NioEventLoopGroup()
        bootstrap.group(eventLoopGroup)
        bootstrap.channel(NioSocketChannel::class.java)
        bootstrap.option(ChannelOption.TCP_NODELAY, true)
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true)
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECTION_TIMEOUT_IN_MILLS)
    }

    fun initHandler(handler: BaseChannelInboundHandler<*>?) {
        defaultChannelHandler = handler
        channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                val pipeline = socketChannel.pipeline()
                addLastToPipeline(pipeline)
                defaultChannelHandler?.let {
                    pipeline.addLast("default-channel-handler", it)
                }
            }
        }
        bootstrap.handler(channelInitializer)
    }

    open fun addLastToPipeline(pipeline: ChannelPipeline) {}

    /**
     * If netty client has already been release, call this method will throw [java.util.concurrent.RejectedExecutionException]: event executor terminated
     */
//    @Throws(RejectedExecutionException::class)
    @Synchronized
    fun connect() {
        LLog.i(TAG, "===== connect() current state=${connectState.name} =====")
        when (connectState) {
            ConnectionStatus.CONNECTING -> {
                LLog.w(TAG, "===== Wait for connecting =====")
                return
            }
            ConnectionStatus.CONNECTED -> {
                LLog.w(TAG, "===== Already connected =====")
                return
            }
            else -> LLog.i(TAG, "===== Prepare to connect to server =====")
        }
        connectState = ConnectionStatus.CONNECTING
        connectionListener.onConnecting(this)
        try {
            // You call connect() with sync() method like this bellow:
            // bootstrap.connect(host, port).sync()
            // you must handle exception by yourself, because of you want to
            // process connection synchronously. And the connection listener will be ignored regardless of whether you add it.
            //
            // If you want your connection listener work, do like this:
            // bootstrap.connect(host, port).addListener(connectFutureListener)
            // In some cases, although you add your connection listener, you still need to catch some exceptions what your listener can not deal with
            // Just like RejectedExecutionException exception. However, I never catch RejectedExecutionException as I expect. Who can tell me why?

            val f = bootstrap.connect(host, port).sync()
            channel = f.syncUninterruptibly().channel()
            retryTimes = 0

            // If I use asynchronous way to do connect, it will cause multiple connections if you click Connect and Disconnect repeatedly in a very quick way.
            // There must be a way to solve the problem. Unfortunately, I don't know how to do that now.
//            bootstrap.connect(host, port).addListener(connectFutureListener)
            LLog.i(TAG, "===== Connect success =====")
        } catch (e: RejectedExecutionException) {
            LLog.e(TAG, "===== RejectedExecutionException: ${e.message} =====")
            LLog.e(TAG, "Netty client had already been released. You must re-initialize it again.")
            // If connection has been connected before, [channelInactive] will be called, so the status and
            // listener will be triggered at that time.
            // However, if netty client had been release, call [connect] again will cause exception.
            // So we handle it here.
            connectState = ConnectionStatus.FAILED
            connectionListener.onFailed(this, NettyConnectionListener.CONNECTION_ERROR_ALREADY_RELEASED, e.message)
        } catch (e: ConnectException) {
            LLog.e(TAG, "===== ConnectException: ${e.message} =====")
            connectState = ConnectionStatus.FAILED
            connectionListener.onFailed(this, NettyConnectionListener.CONNECTION_ERROR_CONNECT_EXCEPTION, e.message)
            doRetry()
        } catch (e: Exception) {
            LLog.e(TAG, "===== Unexpected exception : ${e.message} =====")
            connectState = ConnectionStatus.FAILED
            connectionListener.onFailed(this, NettyConnectionListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION, e.message)
            doRetry()
        }
    }

    /**
     * After calling this method, you can reuse it again by calling [connect].
     * If you don't want to reconnect it anymore, do not forget to call [release].
     *
     * **Remember**, If you call this method, it will not trigger retry process.
     */
    fun disconnectManually() {
        disconnectManually = true
        LLog.w(TAG, "===== disconnect() current state=${connectState.name} =====")
        if (ConnectionStatus.DISCONNECTED == connectState || ConnectionStatus.UNINITIALIZED == connectState) {
            LLog.w(TAG, "Already disconnected or not initialized.")
            return
        }
        // The [DISCONNECTED] status and listener will be assigned and triggered in ChannelHandler if connection has been connected before.
        // However, if connection status is [CONNECTING], it ChannelHandler [channelInactive] will not be triggered.
        // So we just need to assign its status to [DISCONNECTED]. No need to call listener.
        connectState = ConnectionStatus.DISCONNECTED
        stopRetryHandler()
        channel?.disconnect()?.syncUninterruptibly()
        // See above line, we do disconnect with syncUninterruptibly() method, so we can restore disconnectManually flag here.
        disconnectManually = false
    }

    /**
     * Release netty client using **syncUninterruptibly** method.(Full release will cost almost 2s.) So you'd better NOT call this method in main thread.
     *
     * Once you call [release], you can not reconnect it again by calling [connect], you must create netty client again.
     * If you want to reconnect it again, do not call this method, just call [disconnectManually].
     */
    fun release() {
        LLog.w(TAG, "===== release() current state=${connectState.name} =====")
        if (ConnectionStatus.UNINITIALIZED == connectState) {
            LLog.w(TAG, "Already release or not initialized")
            return
        }

        LLog.w(TAG, "Releasing retry handler...")
        stopRetryHandler()
        retryThread.quitSafely()

        LLog.w(TAG, "Releasing default socket handler first...")
        defaultChannelHandler?.release()
        defaultChannelHandler = null
        channelInitializer = null

        channel?.run {
            LLog.w(TAG, "Closing channel...")
            pipeline().removeAll { true }
//            closeFuture().syncUninterruptibly() // It will stuck here. Why???
            closeFuture()
            close().syncUninterruptibly()
            channel = null
        }

        eventLoopGroup.run {
            LLog.w(TAG, "Releasing socket...")
            shutdownGracefully().syncUninterruptibly() // Will not stuck here.
//            shutdownGracefully()
        }

        connectState = ConnectionStatus.UNINITIALIZED
        LLog.w(TAG, "=====> Socket released <=====")
    }

    fun doRetry() {
        retryTimes++
        LLog.w(TAG, "===== reconnect($retryTimes) in ${RETRY_DELAY_IN_MILLS}ms | current state=${connectState.name} =====")
        connectState = ConnectionStatus.FAILED
        connectionListener.onFailed(this, NettyConnectionListener.CONNECTION_ERROR_CAN_NOT_CONNECT_TO_SERVER, "Can't connect to server.")
        retryHandler.postDelayed({
            if (retryTimes >= CONNECT_MAX_RETRY_TIMES) {
                LLog.e(TAG, "===== Connect failed - Exceed max retry times. =====")
                stopRetryHandler()
                connectState = ConnectionStatus.FAILED
                connectionListener.onFailed(
                    this@BaseNettyClient,
                    NettyConnectionListener.CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES,
                    "Exceed max retry times."
                )
            } else {
                connect()
            }
        }, RETRY_DELAY_IN_MILLS)
    }

    private fun stopRetryHandler() {
        retryHandler.removeCallbacksAndMessages(null)
        retryThread.interrupt()
        retryTimes = 0
    }

    // ================================================

    private fun isValidExecuteCommandEnv(): Boolean {
        if (ConnectionStatus.CONNECTED != connectState) {
            LLog.e(TAG, "Socket is not connected. Can not send command.")
            ToastUtil.showDebugToast("Socket is not connected. Can not send command.")
            return false
        }
        if (channel == null) {
            LLog.e(TAG, "Can not execute cmd because of Channel is null.")
            ToastUtil.showDebugToast("Channel is null. Can not send command.")
            return false
        }
        return true
    }

    private fun executeCommandInString(cmd: String?, showLog: Boolean) {
        if (!isValidExecuteCommandEnv()) {
            LLog.e(TAG, "Not invalid execute command evn!")
            return
        }
        if (cmd.isNullOrBlank()) {
            LLog.w(TAG, "Can not execute blank string command.")
            ToastUtil.showDebugErrorToast("Empty string command")
            return
        }
        if (showLog) {
            LLog.i(TAG, "exeCmd s[${cmd.length}]=$cmd")
        }
        channel?.writeAndFlush(cmd + "\n")
    }

    private fun executeCommandInBinary(bytes: ByteArray?, showLog: Boolean) {
        if (!isValidExecuteCommandEnv()) {
            return
        }
        if (bytes == null || bytes.isEmpty()) {
            LLog.w(TAG, "Can not execute blank binary command.")
            ToastUtil.showDebugErrorToast("Command bytes is empty. Can not send command.")
            return
        }
        if (showLog) {
            LLog.i(TAG, "exeCmd HEX[${bytes.size}]=[${bytes.toHexStringLE()}]")
        }
        channel?.writeAndFlush(Unpooled.wrappedBuffer(bytes))
    }

    @JvmOverloads
    fun executeCommand(cmd: String, showLog: Boolean = true) {
        executeCommandInString(cmd, showLog)
    }

    @Suppress("unused")
    @JvmOverloads
    fun executeCommand(cmd: ByteArray?, showLog: Boolean = true) {
        executeCommandInBinary(cmd, showLog)
    }

    // ================================================

    companion object {
        private const val TAG = "BaseNettyClient"

        //        const val HEARTBEAT_INTERVAL_IN_MS = 10_000L
//        const val HEARTBEAT_TIMEOUT_TIMES = 3
        const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
        private const val CONNECT_MAX_RETRY_TIMES = 3

        private const val RETRY_DELAY_IN_MILLS: Long = 2000L
    }
}