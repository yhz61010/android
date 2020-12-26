package com.leovp.socket_sdk.framework.client

import com.leovp.androidbase.exts.kotlin.toHexStringLE
import com.leovp.androidbase.http.SslUtils
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.socket_sdk.framework.base.BaseNetty
import com.leovp.socket_sdk.framework.base.ClientConnectStatus
import com.leovp.socket_sdk.framework.client.retry_strategy.ConstantRetry
import com.leovp.socket_sdk.framework.client.retry_strategy.base.RetryStrategy
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
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.stream.ChunkedWriteHandler
import kotlinx.coroutines.*
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

    val tag: String by lazy { getTagName() }
    abstract fun getTagName(): String

    private val retryScope = CoroutineScope(Dispatchers.IO + Job())

    protected constructor(
        webSocketUri: URI,
        connectionListener: ClientConnectListener<BaseNettyClient>,
        retryStrategy: RetryStrategy = ConstantRetry()
    ) : this(webSocketUri.host, if (webSocketUri.port == -1) 443 else webSocketUri.port, connectionListener, retryStrategy) {
        this.webSocketUri = webSocketUri
        LogContext.log.w(
            tag,
            "WebSocket mode. Uri=${webSocketUri} host=${webSocketUri.host} port=${if (webSocketUri.port == -1) 443 else webSocketUri.port} retry_strategy=${retryStrategy::class.simpleName}"
        )
    }

    internal var webSocketUri: URI? = null
    internal val isWebSocket: Boolean by lazy { webSocketUri != null }

    private val workerGroup = NioEventLoopGroup()
    private val bootstrap = Bootstrap()
        .group(workerGroup)
        .channel(NioSocketChannel::class.java)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(
            ChannelOption.CONNECT_TIMEOUT_MILLIS,
            CONNECTION_TIMEOUT_IN_MILLS
        )
    private lateinit var channel: Channel
    private var channelInitializer: ChannelInitializer<*>? = null
    var defaultInboundHandler: BaseClientChannelInboundHandler<*>? = null
        protected set

    @Volatile
    var disconnectManually = false
        protected set

    @Volatile
    internal var connectState: AtomicReference<ClientConnectStatus> = AtomicReference(
        ClientConnectStatus.UNINITIALIZED
    )

    //    private val retryThread = HandlerThread("retry-thread").apply { start() }
//    private val retryHandler = Handler(retryThread.looper)
    private var retryTimes = AtomicInteger(0)

    init {
        LogContext.log.w(tag, "WebSocket mode. host=$host port=$port retry_strategy=${retryStrategy::class.simpleName}")
    }

    open fun addLastToPipeline(pipeline: ChannelPipeline) {}

    fun initHandler(handler: BaseClientChannelInboundHandler<*>?) {
        defaultInboundHandler = handler
        channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                with(socketChannel.pipeline()) {
                    if (isWebSocket) {
                        if ((webSocketUri?.scheme ?: "").startsWith("wss", ignoreCase = true)) {
                            if (SslUtils.certificateInputStream == null) {
                                LogContext.log.w(tag, "Working in wss INSECURE mode")
                                val sslCtx: SslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                                addFirst("ssl", sslCtx.newHandler(socketChannel.alloc(), host, port))
                            } else {
                                LogContext.log.w(tag, "Working in wss SECURE mode")
                                requireNotNull("In WSS Secure mode, you must set server certificate by calling SslUtils.certificateInputStream.")
                                val sslContextPair = SslUtils.getSSLContext(SslUtils.certificateInputStream!!)
                                val sslEngine = sslContextPair.first.createSSLEngine(host, port).apply {
                                    useClientMode = true
                                }
                                addFirst("ssl", SslHandler(sslEngine))
                            }
                        }
                        addLast(HttpClientCodec())
                        addLast(HttpObjectAggregator(65536))
                        /** A [ChannelHandler] that adds support for writing a large data stream asynchronously
                         * neither spending a lot of memory nor getting [OutOfMemoryError]. */
                        addLast(ChunkedWriteHandler())
                        addLast(WebSocketClientCompressionHandler.INSTANCE)
//                        if (BuildConfig.DEBUG) {
//                            addLast(LoggingHandler(LogLevel.INFO))
//                        }
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
//            LogContext.log.i(TAG, "===== Connect success =====")
//        } else {
//            LogContext.log.e(TAG, "Retry due to connect failed. Reason: ${future.cause()}")
//            doRetry()
//        }
//    }

    /**
     * If netty client has already been released, call this method will throw [java.util.concurrent.RejectedExecutionException]: event executor terminated
     */
    fun connect() {
        LogContext.log.i(tag, "===== connect() current state=${connectState.get().name} =====")
        synchronized(this) {
            if (connectState.get() == ClientConnectStatus.CONNECTING || connectState.get() == ClientConnectStatus.CONNECTED) {
                LogContext.log.w(tag, "===== Connecting or already connected =====")
                return
            } else {
                LogContext.log.i(tag, "===== Prepare to connect to server =====")
            }
            connectState.set(ClientConnectStatus.CONNECTING)
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
            retryTimes.set(0)
            disconnectManually = false
            if (isWebSocket) {
                defaultInboundHandler?.channelPromise?.addListener { _ ->
                    LogContext.log.i(tag, "===== Connect success =====")
                    connectState.set(ClientConnectStatus.CONNECTED)
                    connectionListener.onConnected(this@BaseNettyClient)
                }
            } else {
                // If I use asynchronous way to do connect, it will cause multiple connections if you click Connect and Disconnect repeatedly in a very quick way.
                // There must be a way to solve the problem. Unfortunately, I don't know how to do that now.
//            bootstrap.connect(host, port).addListener(connectFutureListener)
                LogContext.log.i(tag, "===== Connect success =====")
                connectState.set(ClientConnectStatus.CONNECTED)
                connectionListener.onConnected(this@BaseNettyClient)
            }
        } catch (e: RejectedExecutionException) {
            LogContext.log.e(tag, "===== RejectedExecutionException: ${e.message} =====")
//            e.printStackTrace()
            LogContext.log.e(tag, "Netty client had already been released. You must re-initialize it again.")
            // If connection has been connected before, [channelInactive] will be called, so the status and
            // listener will be triggered at that time.
            // However, if netty client had been release, call [connect] again will cause exception.
            // So we handle it here.
            connectState.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(this, ClientConnectListener.CONNECTION_ERROR_ALREADY_RELEASED, e.message)
        } catch (e: ConnectException) {
            LogContext.log.e(tag, "===== ConnectException: ${e.message} =====")
//            e.printStackTrace()
            connectState.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(this, ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION, e.message)
            doRetry()
        } catch (e: Exception) {
            LogContext.log.e(tag, "===== Exception: ${e.message} =====", e)
//            e.printStackTrace()
            connectState.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(this, ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION, e.message, e)
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
        LogContext.log.w(tag, "===== disconnectManually() current state=${connectState.get().name} =====")
        if (ClientConnectStatus.DISCONNECTED == connectState.get() || ClientConnectStatus.UNINITIALIZED == connectState.get()) {
            LogContext.log.w(tag, "Socket is not connected or already disconnected or not initialized.")
            return false
        }
        disconnectManually = true

        // The [DISCONNECTED] status and listener will be assigned and triggered in ChannelHandler if connection has been connected before.
        // However, if connection status is [CONNECTING], it ChannelHandler [channelInactive] will not be triggered.
        // In this case, we do not change the connect status.

        stopRetryHandler()
        defaultInboundHandler?.release()
        runCatching { if (::channel.isInitialized) channel.disconnect().syncUninterruptibly() }.onFailure { LogContext.log.e(tag, "disconnectManually error.", it) }
        LogContext.log.w(tag, "===== disconnectManually() done =====")
        return true
    }

    fun doRetry() {
        retryTimes.getAndIncrement()
        if (retryTimes.get() > retryStrategy.getMaxTimes()) {
            LogContext.log.e(tag, "===== Connect failed - Exceed max retry times. =====")
            stopRetryHandler()
            connectState.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(
                this@BaseNettyClient,
                ClientConnectListener.CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES,
                "Exceed max retry times."
            )
        } else {
            LogContext.log.w(
                tag,
                "Reconnect($retryTimes) in ${retryStrategy.getDelayInMillSec(retryTimes.get())}ms | current state=${connectState.get().name}"
            )
//            retryHandler.postDelayed({ connect() }, retryStrategy.getDelayInMillSec(retryTimes.get()))
            retryScope.launch {
                runCatching {
                    delay(retryStrategy.getDelayInMillSec(retryTimes.get()))
                    ensureActive()
                    connect()
                }.onFailure { LogContext.log.e(tag, "Do retry failed.", it) }
            }
        }
    }

    /**
     * Release netty client using **syncUninterruptibly** method.(Full release will cost almost 2s.)
     * So you'd better NOT call this method in main thread.
     *
     * Once you call [release], you can not reconnect it again by calling [connect] simply, you must recreate netty client again.
     * If you want to reconnect it again, do not call this method, just call [disconnectManually].
     *
     * If current connect state is [ClientConnectStatus.FAILED], this method will also be run and any exception will be ignored.
     */
    fun release(): Boolean {
        LogContext.log.w(tag, "===== release() current state=${connectState.get().name} =====")
        synchronized(this) {
            if (!::channel.isInitialized || ClientConnectStatus.UNINITIALIZED == connectState.get()) {
                LogContext.log.w(tag, "Already release or not initialized")
                return false
            }
            connectState.set(ClientConnectStatus.UNINITIALIZED)
        }
        disconnectManually = true
        LogContext.log.w(tag, "Releasing retry handler...")
        stopRetryHandler()
//        retryThread.quitSafely()

        LogContext.log.w(tag, "Releasing default socket handler first...")
        defaultInboundHandler?.release()
        defaultInboundHandler = null
        channelInitializer = null

        if (::channel.isInitialized) {
            channel.run {
                LogContext.log.w(tag, "Closing channel...")
                runCatching {
                    pipeline().removeAll { true }
//            closeFuture().syncUninterruptibly() // It will stuck here. Why???
                    closeFuture()
                    close().syncUninterruptibly()
                }.onFailure { LogContext.log.e(tag, "Close channel error.", it) }
            }
        }

        runCatching {
            LogContext.log.w(tag, "Releasing socket...")
            workerGroup.shutdownGracefully().syncUninterruptibly() // Will not stuck here.
            LogContext.log.w(tag, "Release socket done!!!")
        }.onFailure { LogContext.log.e(tag, "Release socket error.", it) }
        LogContext.log.w(tag, "=====> Socket released <=====")
        return true
    }

    private fun stopRetryHandler() {
        LogContext.log.i(tag, "stopRetryHandler()")
//        retryHandler.removeCallbacksAndMessages(null)
//        retryThread.interrupt()
        runCatching {
            retryScope.cancel()
        }.onFailure { LogContext.log.e(tag, "Cancel retry coroutine error.", it) }
        retryTimes.set(0)
    }

    // ================================================

    private fun isValidExecuteCommandEnv(cmdTypeAndId: String, cmd: Any?): Boolean {
        if (!::channel.isInitialized) {
            LogContext.log.e(tag, "$cmdTypeAndId: Channel is not initialized. Stop processing.")
            return false
        }
        if (cmd == null) {
            LogContext.log.e(tag, "$cmdTypeAndId: The command is null. Stop processing.")
            return false
        }
        if (cmd !is String && cmd !is ByteArray) {
            throw IllegalArgumentException("$cmdTypeAndId: Command must be either String or ByteArray.")
        }
        if (ClientConnectStatus.CONNECTED != connectState.get()) {
            LogContext.log.e(tag, "$cmdTypeAndId: Socket is not connected. Can not send command.")
            return false
        }
        if (::channel.isInitialized && !channel.isActive) {
            LogContext.log.e(tag, "$cmdTypeAndId: Can not execute cmd because of Channel is not active.")
            return false
        }
        return true
    }

    /**
     * @param isPing Only works in WebSocket mode
     */
    private fun executeUnifiedCommand(
        cmdTypeAndId: String,
        cmdDesc: String,
        cmd: Any?,
        showContent: Boolean,
        isPing: Boolean,
        showLog: Boolean = true
    ): Boolean {
        if (!isValidExecuteCommandEnv(cmdTypeAndId, cmd)) {
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
                if (showLog) {
                    if (showContent) LogContext.log.i(cmdTypeAndId, "exe[$cmdDesc][${cmd.length}]=$cmd")
                    else LogContext.log.i(cmdTypeAndId, "exe[$cmdDesc][${cmd.length}]")
                }
            }
            is ByteArray -> {
                isStringCmd = false
                stringCmd = null
                bytesCmd = Unpooled.wrappedBuffer(cmd)
                if (showLog) {
                    if (showContent) LogContext.log.i(cmdTypeAndId, "exe[$cmdDesc][${cmd.size}]=HEX[${cmd.toHexStringLE()}]")
                    else LogContext.log.i(cmdTypeAndId, "exe[$cmdDesc][${cmd.size}]")
                }
            }
            else -> throw IllegalArgumentException("Command must be either String or ByteArray")
        }

        if (::channel.isInitialized) {
            if (isWebSocket) {
                if (isPing) channel.writeAndFlush(PingWebSocketFrame(if (isStringCmd) Unpooled.wrappedBuffer(stringCmd!!.toByteArray()) else bytesCmd))
                else channel.writeAndFlush(if (isStringCmd) TextWebSocketFrame(stringCmd) else BinaryWebSocketFrame(bytesCmd))
            } else {
                channel.writeAndFlush(if (isStringCmd) "$stringCmd\n" else bytesCmd)
            }
        } else {
            LogContext.log.e(tag, "Property 'channel' is not initialized.")
            return false
        }
        return true
    }

    @JvmOverloads
    fun executeCommand(cmdTypeAndId: String, cmdDesc: String, cmd: Any?, showContent: Boolean = true, showLog: Boolean = true) =
        executeUnifiedCommand(cmdTypeAndId, cmdDesc, cmd, showContent, false, showLog)

    @Suppress("unused")
    @JvmOverloads
    fun executePingCommand(cmdTypeAndId: String, cmdDesc: String, cmd: Any?, showContent: Boolean = true, showLog: Boolean = true) =
        executeUnifiedCommand(cmdTypeAndId, cmdDesc, cmd, showContent, true, showLog)

    // ================================================
}