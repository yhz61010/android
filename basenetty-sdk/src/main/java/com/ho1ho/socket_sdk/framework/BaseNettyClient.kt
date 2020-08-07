package com.ho1ho.socket_sdk.framework

import android.os.Handler
import android.os.HandlerThread
import com.ho1ho.androidbase.exts.toHexStringLE
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.socket_sdk.framework.inter.ClientConnectListener
import com.ho1ho.socket_sdk.framework.retry_strategy.ConstantRetry
import com.ho1ho.socket_sdk.framework.retry_strategy.base.RetryStrategy
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
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
    val connectionListener: ClientConnectListener<BaseNettyClient>,
    private val retryStrategy: RetryStrategy = ConstantRetry()
) : BaseNetty() {
    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
    }

    private val tag = javaClass.simpleName

    protected constructor(
        webSocketUri: URI,
        connectionListener: ClientConnectListener<BaseNettyClient>,
        retryStrategy: RetryStrategy = ConstantRetry()
    ) : this(webSocketUri.host, webSocketUri.port, connectionListener, retryStrategy) {
        this.webSocketUri = webSocketUri
        LLog.w(tag, "WebSocket mode. Uri=${webSocketUri} host=${webSocketUri.host} port=${webSocketUri.port} retry_strategy=$retryStrategy")
    }

    internal var webSocketUri: URI? = null
    internal val isWebSocket: Boolean by lazy { webSocketUri != null }

    private val workerGroup = NioEventLoopGroup()
    private val bootstrap = Bootstrap()
        .group(workerGroup)
        .channel(NioSocketChannel::class.java)
        .handler(LoggingHandler(LogLevel.INFO))
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECTION_TIMEOUT_IN_MILLS)
    private lateinit var channel: Channel
    private var channelInitializer: ChannelInitializer<*>? = null
    var defaultInboundHandler: BaseChannelInboundHandler<*>? = null
        protected set

    @Volatile
    var disconnectManually = false

    @Volatile
    internal var connectState: AtomicReference<ClientConnectStatus> = AtomicReference(ClientConnectStatus.UNINITIALIZED)
    private val retryThread = HandlerThread("retry-thread").apply { start() }
    private val retryHandler = Handler(retryThread.looper)
    var retryTimes = AtomicInteger(0)

    init {
        LLog.w(tag, "WebSocket mode. host=$host port=$port retry_strategy=$retryStrategy")
    }

    open fun addLastToPipeline(pipeline: ChannelPipeline) {}

    fun initHandler(handler: BaseChannelInboundHandler<*>?) {
        defaultInboundHandler = handler
        channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                with(socketChannel.pipeline()) {
                    if (isWebSocket) {
                        if ((webSocketUri?.scheme ?: "").startsWith("wss", ignoreCase = true)) {
                            LLog.w(tag, "Working in wss mode")
                            val sslCtx: SslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                            addFirst(sslCtx.newHandler(socketChannel.alloc(), host, port))
                        }
                        addLast(HttpClientCodec())
                        addLast(HttpObjectAggregator(65536))
                        /** A [ChannelHandler] that adds support for writing a large data stream asynchronously
                         * neither spending a lot of memory nor getting [OutOfMemoryError]. */
                        addLast(ChunkedWriteHandler())
                        addLast(WebSocketClientCompressionHandler.INSTANCE)
                    } else {
                        addLast(DelimiterBasedFrameDecoder(65535, *Delimiters.lineDelimiter()))
                        addLast(StringDecoder())
                        addLast(StringEncoder())
                    }
                    addLastToPipeline(this)
                    defaultInboundHandler?.let { addLast("default-inbound-handler", it) }
                }
            }
        }
        bootstrap.handler(channelInitializer)
    }

    //    var receivingDataListener: ReceivingDataListener? = null

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

    /**
     * If netty client has already been release, call this method will throw [java.util.concurrent.RejectedExecutionException]: event executor terminated
     */
//    @Throws(RejectedExecutionException::class)
    @Synchronized
    fun connect() {
        LLog.i(tag, "===== connect() current state=${connectState.get().name} =====")
        if (connectState.get() == ClientConnectStatus.CONNECTED) {
            LLog.w(tag, "===== Already connected =====")
            return
        } else {
            LLog.i(tag, "===== Prepare to connect to server =====")
        }
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
            LLog.e(tag, "===== RejectedExecutionException: ${e.message} =====", e)
            LLog.e(tag, "Netty client had already been released. You must re-initialize it again.")
            // If connection has been connected before, [channelInactive] will be called, so the status and
            // listener will be triggered at that time.
            // However, if netty client had been release, call [connect] again will cause exception.
            // So we handle it here.
            connectState.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(this, ClientConnectListener.CONNECTION_ERROR_ALREADY_RELEASED, e.message)
        } catch (e: ConnectException) {
            LLog.e(tag, "===== ConnectException: ${e.message} =====", e)
            connectState.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(this, ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION, e.message)
            doRetry()
        } catch (e: Exception) {
            LLog.e(tag, "===== Exception: ${e.message} =====", e)
            connectState.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(this, ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION, e.message)
            doRetry()
        }
    }

    /**
     * After calling this method, you can reuse it again by calling [connect].
     * If you don't want to reconnect it anymore, do not forget to call [release].
     *
     * If current connect state is [ClientConnectStatus.FAILED], this method will also be run and any exception will be ignored.
     *
     * **Remember**, If you call this method, it will not trigger retry process.
     */
    fun disconnectManually(): Boolean {
        LLog.w(tag, "===== disconnect() current state=${connectState.get().name} =====")
        if (ClientConnectStatus.DISCONNECTED == connectState.get()
            || ClientConnectStatus.UNINITIALIZED == connectState.get()
        ) {
            LLog.w(tag, "Socket is not connected or already disconnected or not initialized.")
            return false
        }
        disconnectManually = true
        // The [DISCONNECTED] status and listener will be assigned and triggered in ChannelHandler if connection has been connected before.
        // However, if connection status is [CONNECTING], it ChannelHandler [channelInactive] will not be triggered.
        // So we just need to assign its status to [DISCONNECTED]. No need to call listener.
        connectState.set(ClientConnectStatus.DISCONNECTED)
        stopRetryHandler()
        kotlin.runCatching { channel.disconnect().syncUninterruptibly() }.onFailure { LLog.e(tag, "disconnectManually error.", it) }
        return true
    }

    fun doRetry() {
        retryTimes.getAndIncrement()
        if (retryTimes.get() > retryStrategy.getMaxTimes()) {
            LLog.e(tag, "===== Connect failed - Exceed max retry times. =====")
            stopRetryHandler()
            connectState.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(
                this@BaseNettyClient,
                ClientConnectListener.CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES,
                "Exceed max retry times."
            )
        } else {
            LLog.w(
                tag,
                "Reconnect($retryTimes) in ${retryStrategy.getDelayInMillSec(retryTimes.get())}ms | current state=${connectState.get().name}"
            )
            retryHandler.postDelayed({ connect() }, retryStrategy.getDelayInMillSec(retryTimes.get()))
        }
    }

    /**
     * Release netty client using **syncUninterruptibly** method.(Full release will cost almost 2s.)
     * So you'd better NOT call this method in main thread.
     *
     * Once you call [release], you can not reconnect it again by calling [connect], you must create netty client again.
     * If you want to reconnect it again, do not call this method, just call [disconnectManually].
     *
     * If current connect state is [ClientConnectStatus.FAILED], this method will also be run and any exception will be ignored.
     */
    fun release(): Boolean {
        LLog.w(tag, "===== release() current state=${connectState.get().name} =====")
        if (!::channel.isInitialized || ClientConnectStatus.UNINITIALIZED == connectState.get()) {
            LLog.w(tag, "Already release or not initialized")
            return false
        }
        connectState.set(ClientConnectStatus.UNINITIALIZED)
        disconnectManually = true
        LLog.w(tag, "Releasing retry handler...")
        stopRetryHandler()
        retryThread.quitSafely()

        LLog.w(tag, "Releasing default socket handler first...")
        defaultInboundHandler?.release()
        defaultInboundHandler = null
        channelInitializer = null

        channel.run {
            LLog.w(tag, "Closing channel...")
            kotlin.runCatching {
                pipeline().removeAll { true }
//            closeFuture().syncUninterruptibly() // It will stuck here. Why???
                closeFuture()
                close().syncUninterruptibly()
            }.onFailure { LLog.e(tag, "Close channel error.", it) }
        }

        workerGroup.run {
            LLog.w(tag, "Releasing socket...")
            shutdownGracefully().syncUninterruptibly() // Will not stuck here.
//            shutdownGracefully()
        }
        LLog.w(tag, "=====> Socket released <=====")
        return true
    }

    private fun stopRetryHandler() {
        retryHandler.removeCallbacksAndMessages(null)
        retryThread.interrupt()
        retryTimes.set(0)
    }

    // ================================================

    private fun isValidExecuteCommandEnv(cmd: Any?): Boolean {
        if (!::channel.isInitialized) {
            LLog.e(tag, "Channel is not initialized. Stop processing.")
            return false
        }
        if (cmd == null) {
            LLog.e(tag, "The command is null. Stop processing.")
            return false
        }
        if (cmd !is String && cmd !is ByteArray) {
            throw IllegalArgumentException("Command must be either String or ByteArray.")
        }
        if (ClientConnectStatus.CONNECTED != connectState.get()) {
            LLog.e(tag, "Socket is not connected. Can not send command.")
            return false
        }
        if (!channel.isActive) {
            LLog.e(tag, "Can not execute cmd because of Channel is not active.")
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
            if (isPing) channel.writeAndFlush(PingWebSocketFrame(if (isStringCmd) Unpooled.wrappedBuffer(stringCmd!!.toByteArray()) else bytesCmd))
            else channel.writeAndFlush(if (isStringCmd) TextWebSocketFrame(stringCmd) else BinaryWebSocketFrame(bytesCmd))
        } else {
            channel.writeAndFlush(if (isStringCmd) "$stringCmd\n" else bytesCmd)
        }
        return true
    }

    @JvmOverloads
    fun executeCommand(cmd: Any?, showLog: Boolean = true) = executeUnifiedCommand(cmd, showLog, false)

    @Suppress("unused")
    @JvmOverloads
    fun executePingCommand(cmd: Any?, showLog: Boolean = true) = executeUnifiedCommand(cmd, showLog, true)

    // ================================================
}