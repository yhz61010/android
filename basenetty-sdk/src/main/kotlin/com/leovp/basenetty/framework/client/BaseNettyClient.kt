@file:Suppress("unused")

package com.leovp.basenetty.framework.client

import com.leovp.basenetty.framework.base.BaseNetty
import com.leovp.basenetty.framework.base.ClientConnectStatus
import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import com.leovp.basenetty.framework.client.retrystrategy.base.RetryStrategy
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import com.leovp.log.base.LogOutType
import com.leovp.network.SslUtils
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.http.DefaultHttpHeaders
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
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.stream.ChunkedWriteHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ConnectException
import java.net.URI
import java.nio.ByteOrder
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A thread-safe class
 *
 * For none-ssl WebSocket or trust all certificates WebSocket, you can create just one socket object,
 * then disconnect it and connect it again for many times as you wish.
 *
 * However, for self-signed certificate, once you disconnect the socket,
 * you must recreate the socket object again then connect it or else you can **NOT** connect it any more.
 *
 * Example:
 * For none-ssl WebSocket or trust all certificates:
 * create socket ──> connect() ──> disconnectManually()
 *                       ↑                   ↓
 *                       └───────────────────┘
 * Note that, in this case, your socket handler must be @Sharable
 *
 * For self-signed certificate:
 * create socket ──> connect() ──> (optional)disconnectManually()  ──> release()
 *        ↑                                                               ↓
 *        └───────────────────────────────────────────────────────────────┘
 *
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
abstract class BaseNettyClient protected constructor(
    private val host: String,
    private val port: Int,
    val connectionListener: ClientConnectListener<BaseNettyClient>,
    private val retryStrategy: RetryStrategy = ConstantRetry(),
    private val headers: Map<String, String>? = null,
    timeout: Int = CONNECTION_TIMEOUT_IN_MILLS,
) : BaseNetty {
    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
    }

    protected constructor(
        webSocketUri: URI,
        connectionListener: ClientConnectListener<BaseNettyClient>,
        certInputStream: InputStream,
        retryStrategy: RetryStrategy = ConstantRetry(),
        headers: Map<String, String>? = null,
        timeout: Int = CONNECTION_TIMEOUT_IN_MILLS,
    ) : this(
        webSocketUri.host,
        if (webSocketUri.port == -1) {
            when {
                "ws".equals(webSocketUri.scheme, true) -> 80
                "wss".equals(webSocketUri.scheme, true) -> 443
                else -> -1
            }
        } else {
            webSocketUri.port
        },
        connectionListener,
        retryStrategy,
        headers,
        timeout
    ) {
        this.webSocketUri = webSocketUri
        this.certificateInputStream = certInputStream
        LogContext.log.w(
            tag,
            "WebSocket mode. Uri=$webSocketUri host=$host port=$port retry_strategy=${retryStrategy::class.simpleName}"
        )
    }

    protected constructor(
        webSocketUri: URI,
        connectionListener: ClientConnectListener<BaseNettyClient>,
        trustAllServers: Boolean,
        retryStrategy: RetryStrategy = ConstantRetry(),
        headers: Map<String, String>? = null,
        timeout: Int = CONNECTION_TIMEOUT_IN_MILLS,
    ) : this(
        webSocketUri.host,
        if (webSocketUri.port == -1) {
            when {
                "ws".equals(webSocketUri.scheme, true) -> 80
                "wss".equals(webSocketUri.scheme, true) -> 443
                else -> -1
            }
        } else {
            webSocketUri.port
        },
        connectionListener,
        retryStrategy,
        headers,
        timeout
    ) {
        this.webSocketUri = webSocketUri
        this.trustAllServers = trustAllServers
        LogContext.log.w(
            tag,
            "WebSocket mode. Secure: ${!trustAllServers}. Uri=$webSocketUri " +
                "host=$host port=$port retry_strategy=${retryStrategy::class.simpleName}"
        )
    }

    val tag: String by lazy { getTagName() }
    abstract fun getTagName(): String

    init {
        LogContext.log.i(tag, "Socket host=$host port=$port retry_strategy=${retryStrategy::class.simpleName}")
    }

    internal fun setHeaders(headers: DefaultHttpHeaders) {
        this.headers?.let {
            LogContext.log.i(tag, "Prepare to set headers...")
            for ((k, v) in it) {
                LogContext.log.i(tag, "Cookie: $k=$v", outputType = LogOutType.HTTP_HEADER)
                headers.add(k, v)
            }
        }
    }

    private var certificateInputStream: InputStream? = null
    private var trustAllServers: Boolean = false

    fun getCertificateInputStream(): InputStream? {
        if (certificateInputStream == null) {
            return null
        }
        return runCatching {
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(8 shl 10)
            var len: Int
            while (certificateInputStream!!.read(buffer).also { len = it } > -1) {
                baos.write(buffer, 0, len)
            }
            baos.flush() // DO NOT close certificateInputStream stream or else we can not clone it anymore
            ByteArrayInputStream(baos.toByteArray())
        }.getOrNull()
    }

    private val retryScope = CoroutineScope(Dispatchers.IO + Job())

    internal var webSocketUri: URI? = null
    internal val isWebSocket: Boolean by lazy { webSocketUri != null }

    private val workerGroup = NioEventLoopGroup()
    private val bootstrap = Bootstrap().group(workerGroup)
        .channel(NioSocketChannel::class.java)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
    private lateinit var channel: Channel
    private var channelInitializer: ChannelInitializer<*>? = null
    var defaultInboundHandler: BaseClientChannelInboundHandler<*>? = null
        protected set

    @Volatile
    var disconnectManually = false
        protected set

    @Volatile
    var connectStatus: AtomicReference<ClientConnectStatus> = AtomicReference(ClientConnectStatus.UNINITIALIZED)
        private set

    private var retryTimes = AtomicInteger(0)

    open fun addLastToPipeline(pipeline: ChannelPipeline) {}

    fun initHandler(handler: BaseClientChannelInboundHandler<*>?) {
        defaultInboundHandler = handler
        channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                with(socketChannel.pipeline()) {
                    if (isWebSocket) {
                        if ((webSocketUri?.scheme ?: "").startsWith("wss", ignoreCase = true)) {
                            if (trustAllServers) {
                                LogContext.log.w(tag, "Working in wss INSECURE mode")
                                val sslCtx: SslContext =
                                    SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .build()
                                addFirst("ssl", sslCtx.newHandler(socketChannel.alloc(), host, port))
                            } else {
                                if (certificateInputStream == null) {
                                    LogContext.log.w(tag, "Working in wss CA SECURE mode")
                                    val sslCtx: SslContext = SslContextBuilder.forClient().build()
                                    // val sslCtx: SslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                                    addFirst("ssl", sslCtx.newHandler(socketChannel.alloc(), host, port))
                                } else {
                                    LogContext.log.w(tag, "Working in wss self-signed SECURE mode")
                                    requireNotNull(certificateInputStream) {
                                        "In WSS Secure mode, you must set server certificate by calling " +
                                            "SslUtils.certificateInputStream."
                                    }

                                    val sslContextPair = SslUtils.getSSLContext(getCertificateInputStream()!!)

                                    //                                val sslEngine = sslContextPair.first.createSSLEngine(host, port).apply {
                                    //                                    useClientMode = true
                                    //                                }
                                    //                                addFirst("ssl", SslHandler(sslEngine))

                                    val sslCtx: SslContext =
                                        SslContextBuilder.forClient().trustManager(sslContextPair.second).build()
                                    addFirst("ssl", sslCtx.newHandler(socketChannel.alloc(), host, port))

                                    //                                val sslEngine = SSLContext.getDefault().createSSLEngine().apply { useClientMode = true }
                                    //                                addFirst("ssl", SslHandler(sslEngine))
                                }
                            }
                        }
                        addLast(HttpClientCodec())
                        addLast(HttpObjectAggregator(1 shl 20))
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

    /**
     * If netty client has already been released, call this method will throw [java.util.concurrent.RejectedExecutionException]: event executor terminated
     */
    suspend fun connect(): ClientConnectStatus = suspendCancellableCoroutine { cont ->
        LogContext.log.i(tag, "===== connect() current state=${connectStatus.get().name} =====")
        synchronized(this) {
            when (connectStatus.get()) {
                ClientConnectStatus.CONNECTING, ClientConnectStatus.CONNECTED -> {
                    LogContext.log.w(tag, "===== Connecting or already connected =====")
                    cont.resume(connectStatus.get())
                    return@suspendCancellableCoroutine
                }

                ClientConnectStatus.RELEASING -> {
                    LogContext.log.w(tag, "===== Releasing now. DO NOT connect and stop processing. =====")
                    cont.resume(connectStatus.get())
                    return@suspendCancellableCoroutine
                }

                ClientConnectStatus.DISCONNECTING -> {
                    LogContext.log.w(tag, "===== Disconnecting now. DO NOT connect and stop processing. =====")
                    cont.resume(connectStatus.get())
                    return@suspendCancellableCoroutine
                }

                else -> LogContext.log.i(tag, "===== Prepare to connect to server =====")
            }
            connectStatus.set(ClientConnectStatus.CONNECTING)
        }
        try { // You call connect() with sync() method like this bellow:
            // bootstrap.connect(host, port).sync()
            // you must handle exception by yourself, because of you want to
            // process connection synchronously. And the connection listener will be ignored regardless of whether you add it.
            //
            // If you want your connection listener work, do like this:
            // bootstrap.connect(host, port).addListener(connectFutureListener)
            // In some cases, although you add your connection listener, you still need to catch some exceptions what your listener can not deal with
            // Just like RejectedExecutionException exception. However, I never catch RejectedExecutionException as I expect. Who can tell me why?

            // Add sync() here, so that the listener of ChannelPromise will be triggered here.
            val f = bootstrap.connect(host, port).sync()
            channel = f.channel()
            retryTimes.set(0)
            disconnectManually = false
            if (isWebSocket) {
                defaultInboundHandler?.channelPromise?.addListener {
                    if (it.isSuccess) {
                        LogContext.log.i(tag, "=====> WebSocket Connect success <=====")
                        connectStatus.set(ClientConnectStatus.CONNECTED)
                        connectionListener.onConnected(this@BaseNettyClient)
                        cont.resume(connectStatus.get())
                    } else {
                        LogContext.log.i(tag, "=====> WebSocket Connect failed <=====")
                        connectStatus.set(ClientConnectStatus.FAILED)
                        connectionListener.onFailed(
                            this,
                            ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION,
                            "WebSocket Connect failed",
                            it.cause()
                        )
                        cont.resume(connectStatus.get()) // Do NOT know how to reproduce this case
                        //                        LogContext.log.e(tag, "=====> CHK1 <=====")
                        doRetry()
                    }
                }
            } else {
                // If I use asynchronous way to do connect, it will cause multiple connections
                // if you click Connect and Disconnect repeatedly in a very quick way.

                // There must be a way to solve the problem. Unfortunately, I don't know how to do that now.
                //            bootstrap.connect(host, port).addListener(connectFutureListener)
                f.addListener {
                    if (it.isSuccess) {
                        LogContext.log.i(tag, "=====> Connect success <=====")
                        connectStatus.set(ClientConnectStatus.CONNECTED)
                        connectionListener.onConnected(this@BaseNettyClient)
                        cont.resume(connectStatus.get())
                    } else {
                        LogContext.log.i(tag, "=====> Connect failed <=====")
                        connectStatus.set(ClientConnectStatus.FAILED)
                        connectionListener.onFailed(
                            this,
                            ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION,
                            "Connect failed"
                        )
                        cont.resume(connectStatus.get()) // Do NOT know how to reproduce this case
                        //                        LogContext.log.e(tag, "=====> CHK2 <=====")
                        doRetry()
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            LogContext.log.e(
                tag,
                "===== RejectedExecutionException. Netty client had already been released. " +
                    "You must re-initialize it again.: ${e.message} ====="
            ) // If connection has been connected before, [channelInactive] will be called, so the status and
            // listener will be triggered at that time.
            // However, if netty client had been release, call [connect] again will cause exception.
            // So we handle it here.
            connectStatus.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(this, ClientConnectListener.CONNECTION_ERROR_ALREADY_RELEASED, e.message)
            cont.resume(connectStatus.get())
        } catch (e: ConnectException) {
            LogContext.log.e(tag, "===== ConnectException: ${e.message} =====")
            //            connectStatus.set(ClientConnectStatus.FAILED)
            //            connectionListener.onFailed(this, ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION, e.message)
            // This exception will trigger handlerRemoved(), so we retry at that time.
            cont.resume(ClientConnectStatus.FAILED)

            //            LogContext.log.e(tag, "=====> CHK3 <=====")
            //            doRetry()
        } catch (e: Exception) {
            LogContext.log.e(tag, "===== Exception: ${e.message} =====", e)
            //            connectStatus.set(ClientConnectStatus.FAILED)
            //            connectionListener.onFailed(this, ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION, e.message, e)
            // This exception will trigger handlerRemoved(), so we retry at that time.
            cont.resume(ClientConnectStatus.FAILED)

            //            LogContext.log.e(tag, "=====> CHK4 <=====")
            //            doRetry()
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
    suspend fun disconnectManually(): ClientConnectStatus = suspendCancellableCoroutine { cont ->
        LogContext.log.w(tag, "===== disconnectManually() current state=${connectStatus.get().name} =====")
        synchronized(this) {
            val connStatus = connectStatus.get()
            if (ClientConnectStatus.DISCONNECTED == connStatus || ClientConnectStatus.UNINITIALIZED == connStatus) {
                LogContext.log.w(tag, "Socket is not connected or already disconnected or not initialized.")
                cont.resume(connectStatus.get())
                return@suspendCancellableCoroutine
            } else if (ClientConnectStatus.DISCONNECTING == connectStatus.get()) {
                LogContext.log.w(tag, "Socket is disconnecting now. Stop processing.")
                cont.resume(connectStatus.get())
                return@suspendCancellableCoroutine
            }
            connectStatus.set(ClientConnectStatus.DISCONNECTING)
        }
        disconnectManually = true

        // The [DISCONNECTED] status and listener will be assigned and triggered in ChannelHandler
        // if connection has been connected before.
        // However, if connection status is [CONNECTING],
        // it ChannelHandler [channelInactive] will not be triggered.
        // In this case, we do not change the connect status.

        stopRetryHandler()
        defaultInboundHandler?.release()
        runCatching {
            // Add sync() here to make sure
            // the listener of channel disconnect method will be triggered here.
            if (::channel.isInitialized) {
                channel.disconnect().sync().addListener { f ->
                    if (f.isSuccess) {
                        LogContext.log.w(tag, "===== disconnectManually() done =====")
                        connectStatus.set(ClientConnectStatus.DISCONNECTED)
                        connectionListener.onDisconnected(this, byRemote = false)
                        cont.resume(connectStatus.get())
                    } else {
                        LogContext.log.w(tag, "===== disconnectManually() failed =====")
                        connectStatus.set(ClientConnectStatus.FAILED)
                        connectionListener.onFailed(
                            this,
                            ClientConnectListener.DISCONNECT_MANUALLY_ERROR,
                            "Disconnect manually failed"
                        )
                        cont.resume(connectStatus.get())
                    }
                }
            }
        }.onFailure {
            LogContext.log.e(tag, "disconnectManually error.", it)
            connectStatus.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(
                this,
                ClientConnectListener.DISCONNECT_MANUALLY_EXCEPTION,
                "Disconnect manually exception"
            )
            cont.resume(connectStatus.get())
        }
    }

    fun doRetry() {
        if (retryProcess()) return

        retryTimes.getAndIncrement()
        if (retryTimes.get() > retryStrategy.getMaxTimes()) {
            LogContext.log.e(
                tag,
                "===== Connect failed in doRetry() - Exceed max retry times. ====="
            )
            stopRetryHandler()
            connectStatus.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(
                this@BaseNettyClient,
                ClientConnectListener.CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES,
                "Exceed max retry times."
            )
        } else {
            LogContext.log.w(
                tag,
                "Reconnect($retryTimes) in ${retryStrategy.getDelayInMillSec(retryTimes.get())}ms | " +
                    "current state=${connectStatus.get().name}"
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
     * Return `true` to consume retry operation. In this case,
     * you must process reconnecting by yourself, otherwise
     * it won't reconnect automatically.
     * Return `false` indicates retry will be triggered automatically.
     */
    open fun retryProcess() = false

    /**
     * Release netty client using **syncUninterruptibly** method.(Full release will cost almost 2200ms.)
     * So you'd better NOT call this method in main thread.
     *
     * Once you call [release], you can not reconnect it again by calling [connect] simply,
     * you must recreate netty client again.
     * If you want to reconnect it again, do not call this method, just call [disconnectManually].
     *
     * If current connect state is [ClientConnectStatus.FAILED], this method will also be run and
     * any exception will be ignored.
     */
    suspend fun release(): Boolean = suspendCancellableCoroutine { cont ->
        LogContext.log.w(tag, "===== release() current state=${connectStatus.get().name} =====")
        synchronized(this) {
            if (ClientConnectStatus.UNINITIALIZED == connectStatus.get() ||
                ClientConnectStatus.RELEASING == connectStatus.get()
            ) {
                LogContext.log.w(tag, "Releasing now or already released or not initialized")
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            connectStatus.set(ClientConnectStatus.RELEASING)
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
                    // closeFuture().syncUninterruptibly() // syncUninterruptibly() will stuck here. Why???
                    //         closeFuture()
                    close().syncUninterruptibly()
                }.onFailure { LogContext.log.e(tag, "Close channel error.", it) }
            }
        }

        runCatching {
            LogContext.log.w(tag, "Releasing socket...")
            workerGroup.shutdownGracefully() // syncUninterruptibly() will not stuck here.
                .addListener { f ->
                    if (f.isSuccess) {
                        connectStatus.set(ClientConnectStatus.UNINITIALIZED)
                        LogContext.log.w(tag, "=====> Socket released <=====")
                        cont.resume(true)
                    } else {
                        LogContext.log.w(tag, "Release socket failed!!!")
                        cont.resume(false)
                    }
                }
        }.onFailure {
            LogContext.log.e(tag, "Release socket error.", it)
            cont.resume(false)
        }
    }

    private fun stopRetryHandler() {
        LogContext.log.i(tag, "stopRetryHandler()")
        //        retryHandler.removeCallbacksAndMessages(null)
        //        retryThread.interrupt()
        runCatching { retryScope.cancel() }.onFailure {
            LogContext.log.e(tag, "Cancel retry coroutine error.", it)
        }
        retryTimes.set(0)
    }

    // ================================================

    private fun isValidExecuteCommandEnv(cmdTag: String, cmd: Any?): Boolean {
        if (!::channel.isInitialized) {
            LogContext.log.e(cmdTag, "Channel is not initialized. Stop processing.")
            return false
        }
        if (cmd == null) {
            LogContext.log.e(
                cmdTag,
                "The command is null. Stop processing.",
                outputType = LogOutType.CLIENT_COMMAND
            )
            return false
        }
        require(cmd is String || cmd is ByteArray) { "$cmdTag: Command must be either String or ByteArray." }
        if (ClientConnectStatus.CONNECTED != connectStatus.get()) {
            LogContext.log.e(
                cmdTag,
                "Socket is not connected. Can not send command.",
                outputType = LogOutType.CLIENT_COMMAND
            )
            return false
        }
        if (::channel.isInitialized && !channel.isActive) {
            LogContext.log.e(
                cmdTag,
                "Can not execute cmd because of Channel is not active.",
                outputType = LogOutType.CLIENT_COMMAND
            )
            return false
        }
        return true
    }

    /**
     * For general socket(NOT WebSocket), when send string to server,
     * the `\n` will be appended automatically.
     *
     * @param isPing Only works in WebSocket mode.
     */
    private fun executeUnifiedCommand(
        cmdTag: String,
        cmdDesc: String?,
        cmd: Any?,
        isPing: Boolean,
        showContent: Boolean,
        showLog: Boolean = true,
        fullOutput: Boolean = false,
        byteOrder: ByteOrder,
    ): Boolean {
        if (!isValidExecuteCommandEnv(cmdTag, cmd)) {
            return false
        }
        val logPrefix = if (cmdDesc.isNullOrBlank()) "exe" else "exe[$cmdDesc]"
        val stringCmd: String?
        val bytesCmd: ByteBuf?
        val isStringCmd: Boolean
        when (cmd) {
            is String -> {
                isStringCmd = true
                stringCmd = cmd
                bytesCmd = null
                if (showLog) {
                    val cmdMsg = "$logPrefix[${cmd.length}]"
                    LogContext.log.i(cmdTag, if (showContent) "$cmdMsg=$cmd" else cmdMsg, fullOutput = fullOutput)
                }
            }

            is ByteArray -> {
                isStringCmd = false
                stringCmd = null
                bytesCmd = Unpooled.wrappedBuffer(cmd)
                if (showLog) {
                    val cmdMsg = "$logPrefix[${cmd.size}]"
                    val hex: String? = if (showContent) {
                        if (ByteOrder.BIG_ENDIAN == byteOrder) cmd.toHexString() else cmd.toHexString()
                    } else {
                        null
                    }
                    LogContext.log.i(cmdTag, if (hex == null) cmdMsg else "$cmdMsg=HEX[$hex]", fullOutput = fullOutput)
                }
            }

            else -> throw IllegalArgumentException("Command must be either String or ByteArray")
        }

        if (!::channel.isInitialized) {
            LogContext.log.e(cmdTag, "Property 'channel' is not initialized.")
            return false
        }
        if (isWebSocket) {
            if (isPing) {
                val pingByteBuf = if (isStringCmd) {
                    requireNotNull(stringCmd)
                    Unpooled.wrappedBuffer(stringCmd.toByteArray())
                } else {
                    bytesCmd
                }
                channel.writeAndFlush(PingWebSocketFrame(pingByteBuf))
            } else {
                channel.writeAndFlush(
                    if (isStringCmd) {
                        TextWebSocketFrame(stringCmd)
                    } else {
                        BinaryWebSocketFrame(bytesCmd)
                    }
                )
            }
        } else {
            channel.writeAndFlush(if (isStringCmd) "$stringCmd\n" else bytesCmd)
        }
        return true
    }

    /** For general socket(NOT WebSocket), when send string to server, the `\n` will be appended automatically. */
    @JvmOverloads
    fun executeCommand(
        cmd: Any?,
        cmdDesc: String? = null,
        cmdTag: String = tag,
        showContent: Boolean = true,
        showLog: Boolean = true,
        fullOutput: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    ) = executeUnifiedCommand(
        cmdTag,
        cmdDesc,
        cmd,
        isPing = false,
        showContent = showContent,
        showLog = showLog,
        fullOutput = fullOutput,
        byteOrder = byteOrder
    )

    /** This method only works in WebSocket mode. */
    @Suppress("unused")
    @JvmOverloads
    fun executePingCommand(
        cmd: Any?,
        cmdDesc: String? = null,
        cmdTag: String = tag,
        showContent: Boolean = true,
        showLog: Boolean = true,
        fullOutput: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    ) = executeUnifiedCommand(
        cmdTag,
        cmdDesc,
        cmd,
        isPing = true,
        showContent = showContent,
        showLog = showLog,
        fullOutput = fullOutput,
        byteOrder = byteOrder
    )

    // ================================================
}
