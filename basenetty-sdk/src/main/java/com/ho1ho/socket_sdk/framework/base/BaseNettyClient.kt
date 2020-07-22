package com.ho1ho.socket_sdk.framework.base

import android.os.Handler
import android.os.HandlerThread
import com.ho1ho.androidbase.exts.toHexStringLE
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.socket_sdk.framework.base.inter.ConnectionStatus
import com.ho1ho.socket_sdk.framework.base.inter.NettyConnectionListener
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler
import io.netty.handler.stream.ChunkedWriteHandler
import java.net.ConnectException
import java.net.URI
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * A thread-safe class
 *
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseNettyClient protected constructor(
    private val host: String,
    private val port: Int,
    val connectionListener: NettyConnectionListener,
    private val isSecured: Boolean = false
) {
    internal var webSocketUri: URI? = null
    internal var isWebSocket: Boolean

    init {
        isWebSocket = false
        init()
    }

    protected constructor(
        webSocketUri: URI,
        connectionListener: NettyConnectionListener,
        isSecured: Boolean = false
    ) : this(webSocketUri.host, webSocketUri.port, connectionListener, isSecured) {
        this.isWebSocket = true
        this.webSocketUri = webSocketUri
        LLog.w(tag, "WebSocket mode. Uri=${webSocketUri} host=${webSocketUri.host} port=${webSocketUri.port}")
    }

    private val tag = javaClass.simpleName

    private lateinit var bootstrap: Bootstrap
    private lateinit var eventLoopGroup: EventLoopGroup
    private var channel: Channel? = null

    private var channelInitializer: ChannelInitializer<SocketChannel>? = null
    var defaultChannelHandler: BaseChannelInboundHandler<*>? = null
        private set

//    var receivingDataListener: ReceivingDataListener? = null

    @Volatile
    internal var disconnectManually = false

    @Volatile
    internal var connectState: AtomicReference<ConnectionStatus> = AtomicReference(ConnectionStatus.UNINITIALIZED)
    private val retryThread = HandlerThread("retry-thread").apply { start() }
    private val retryHandler = Handler(retryThread.looper)
    internal var retryTimes = AtomicInteger(0)

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
                if (isWebSocket) {
                    pipeline.addLast(HttpClientCodec())
                    pipeline.addLast(HttpObjectAggregator(65536))
                    /** A [ChannelHandler] that adds support for writing a large data stream asynchronously
                     * neither spending a lot of memory nor getting [OutOfMemoryError]. */
                    pipeline.addLast(ChunkedWriteHandler())
                    pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE)
                }
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
        LLog.i(tag, "===== connect() current state=${connectState.get().name} =====")
        when (connectState.get()) {
            ConnectionStatus.CONNECTING -> {
                LLog.w(tag, "===== Wait for connecting =====")
                return
            }
            ConnectionStatus.CONNECTED -> {
                LLog.w(tag, "===== Already connected =====")
                return
            }
            else -> LLog.i(tag, "===== Prepare to connect to server =====")
        }
        connectState.set(ConnectionStatus.CONNECTING)
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

            // If I use asynchronous way to do connect, it will cause multiple connections if you click Connect and Disconnect repeatedly in a very quick way.
            // There must be a way to solve the problem. Unfortunately, I don't know how to do that now.
//            bootstrap.connect(host, port).addListener(connectFutureListener)
            LLog.i(tag, "===== Connect success =====")
        } catch (e: RejectedExecutionException) {
            LLog.e(tag, "===== RejectedExecutionException: ${e.message} =====")
            LLog.e(tag, "Netty client had already been released. You must re-initialize it again.")
            // If connection has been connected before, [channelInactive] will be called, so the status and
            // listener will be triggered at that time.
            // However, if netty client had been release, call [connect] again will cause exception.
            // So we handle it here.
            connectState.set(ConnectionStatus.FAILED)
            connectionListener.onFailed(this, NettyConnectionListener.CONNECTION_ERROR_ALREADY_RELEASED, e.message)
        } catch (e: ConnectException) {
            connectState.set(ConnectionStatus.FAILED)
            connectionListener.onFailed(this, NettyConnectionListener.CONNECTION_ERROR_CONNECT_EXCEPTION, e.message)
            doRetry()
        } catch (e: Exception) {
            connectState.set(ConnectionStatus.FAILED)
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
        LLog.w(tag, "===== disconnect() current state=${connectState.get().name} =====")
        if (ConnectionStatus.DISCONNECTED == connectState.get() || ConnectionStatus.UNINITIALIZED == connectState.get()) {
            LLog.w(tag, "Already disconnected or not initialized.")
            return
        }
        disconnectManually = true
        // The [DISCONNECTED] status and listener will be assigned and triggered in ChannelHandler if connection has been connected before.
        // However, if connection status is [CONNECTING], it ChannelHandler [channelInactive] will not be triggered.
        // So we just need to assign its status to [DISCONNECTED]. No need to call listener.
        connectState.set(ConnectionStatus.DISCONNECTED)
        stopRetryHandler()
        channel?.disconnect()?.syncUninterruptibly()
    }

    /**
     * Release netty client using **syncUninterruptibly** method.(Full release will cost almost 2s.) So you'd better NOT call this method in main thread.
     *
     * Once you call [release], you can not reconnect it again by calling [connect], you must create netty client again.
     * If you want to reconnect it again, do not call this method, just call [disconnectManually].
     */
    fun release() {
        LLog.w(tag, "===== release() current state=${connectState.get().name} =====")
        if (ConnectionStatus.UNINITIALIZED == connectState.get()) {
            LLog.w(tag, "Already release or not initialized")
            return
        }
        disconnectManually = true
        LLog.w(tag, "Releasing retry handler...")
        stopRetryHandler()
        retryThread.quitSafely()

        LLog.w(tag, "Releasing default socket handler first...")
        defaultChannelHandler?.release()
        defaultChannelHandler = null
        channelInitializer = null

        channel?.run {
            LLog.w(tag, "Closing channel...")
            pipeline().removeAll { true }
//            closeFuture().syncUninterruptibly() // It will stuck here. Why???
            closeFuture()
            close().syncUninterruptibly()
            channel = null
        }

        eventLoopGroup.run {
            LLog.w(tag, "Releasing socket...")
            shutdownGracefully().syncUninterruptibly() // Will not stuck here.
//            shutdownGracefully()
        }

        connectState.set(ConnectionStatus.UNINITIALIZED)
        LLog.w(tag, "=====> Socket released <=====")
    }

    fun doRetry() {
        retryTimes.getAndIncrement()
        if (retryTimes.get() > CONNECT_MAX_RETRY_TIMES) {
            LLog.e(tag, "===== Connect failed - Exceed max retry times. =====")
            stopRetryHandler()
            connectState.set(ConnectionStatus.FAILED)
            connectionListener.onFailed(
                this@BaseNettyClient,
                NettyConnectionListener.CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES,
                "Exceed max retry times."
            )
        } else {
            LLog.w(tag, "===== reconnect($retryTimes) in ${RETRY_DELAY_IN_MILLS}ms | current state=${connectState.get().name} =====")
            retryHandler.postDelayed({ connect() }, RETRY_DELAY_IN_MILLS)
        }
    }

    private fun stopRetryHandler() {
        retryHandler.removeCallbacksAndMessages(null)
        retryThread.interrupt()
        retryTimes.set(0)
    }

    // ================================================

    private fun isValidExecuteCommandEnv(cmd: Any?): Boolean {
        if (cmd == null) {
            LLog.e(tag, "The command is null. Stop processing.")
            return false
        }
        if (cmd !is String && cmd !is ByteArray) {
            throw IllegalArgumentException("Command must be either String or ByteArray.")
        }
        if (ConnectionStatus.CONNECTED != connectState.get()) {
            LLog.e(tag, "Socket is not connected. Can not send command.")
            return false
        }
        if (channel == null) {
            LLog.e(tag, "Can not execute cmd because of Channel is null.")
            return false
        }
        return true
    }

    /**
     * @param isPing Only works in WebSocket mode
     */
    private fun executeUnifiedCommand(cmd: Any?, showLog: Boolean, isPing: Boolean): Boolean {
        if (!isValidExecuteCommandEnv(cmd)) {
            return false
        }
        val stringCmd: String?
        val bytesCmd: ByteBuf?
        val isStringCmd: Boolean
        when (cmd) {
            is String -> {
                isStringCmd = true
                stringCmd = cmd
                bytesCmd = null
                if (showLog) LLog.i(tag, "exeCmd[${cmd.length}]=$cmd")
            }
            is ByteArray -> {
                isStringCmd = false
                stringCmd = null
                bytesCmd = Unpooled.wrappedBuffer(cmd)
                if (showLog) LLog.i(tag, "exeCmd HEX[${cmd.size}]=[${cmd.toHexStringLE()}]")
            }
            else -> throw IllegalArgumentException("Command must be either String or ByteArray")
        }

        if (isWebSocket) {
            if (isPing) channel?.writeAndFlush(PingWebSocketFrame(if (isStringCmd) Unpooled.wrappedBuffer(stringCmd!!.toByteArray()) else bytesCmd))
            else channel?.writeAndFlush(if (isStringCmd) TextWebSocketFrame(stringCmd) else BinaryWebSocketFrame(bytesCmd))
        } else {
            channel?.writeAndFlush(if (isStringCmd) "$stringCmd\n" else bytesCmd)
        }
        return true
    }

    @JvmOverloads
    fun executeCommand(cmd: Any?, showLog: Boolean = true) = executeUnifiedCommand(cmd, showLog, false)

    @Suppress("unused")
    @JvmOverloads
    fun executePingCommand(cmd: Any?, showLog: Boolean = true) = executeUnifiedCommand(cmd, showLog, true)

    // ================================================

    companion object {
        //        const val HEARTBEAT_INTERVAL_IN_MS = 10_000L
        //        const val HEARTBEAT_TIMEOUT_TIMES = 3
        const val CONNECTION_TIMEOUT_IN_MILLS = 30_000

        private const val CONNECT_MAX_RETRY_TIMES = 10
        private const val RETRY_DELAY_IN_MILLS: Long = 2000L
    }
}