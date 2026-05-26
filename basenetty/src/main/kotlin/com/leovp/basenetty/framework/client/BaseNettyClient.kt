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
import java.io.InputStream
import java.net.ConnectException
import java.net.URI
import java.nio.ByteOrder
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A thread-safe class
 *
 * For none-ssl WebSocket or trust all certificates WebSocket, you can create just one socket
 * object,
 * then disconnect it and connect it again for many times as you wish.
 *
 * However, for self-signed certificate, once you disconnect the socket,
 * you must recreate the socket object again then connect it or else
 * you can **NOT** connect it anymore.
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
    private val retryDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseNetty {
    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
        private const val DISCONNECT_TIMEOUT_IN_MILLS = 5_000L
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
        this.certificateBytes = certInputStream.readBytes()
        LogContext.log.w(
            tag,
            "WebSocket mode. Uri=$webSocketUri host=$host port=$port " +
                "retry_strategy=${retryStrategy::class.simpleName}"
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
        LogContext.log.i(
            tag,
            "Socket host=$host port=$port retry_strategy=${retryStrategy::class.simpleName}"
        )
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

    private var certificateBytes: ByteArray? = null
    private var trustAllServers: Boolean = false

    fun getCertificateInputStream(): InputStream? =
        certificateBytes?.let { ByteArrayInputStream(it) }

    private val retryScope = CoroutineScope(SupervisorJob() + retryDispatcher)

    private val retryLock = Any()
    private var retryJob: Job? = null

    @Volatile private var released = false

    internal var disconnectTimeoutInMillis: Long = DISCONNECT_TIMEOUT_IN_MILLS

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
    var connectStatus: AtomicReference<ClientConnectStatus> =
        AtomicReference(ClientConnectStatus.UNINITIALIZED)
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
                                addFirst(
                                    "ssl",
                                    sslCtx.newHandler(socketChannel.alloc(), host, port)
                                )
                            } else {
                                if (certificateBytes == null) {
                                    LogContext.log.w(tag, "Working in wss CA SECURE mode")
                                    val sslCtx: SslContext = SslContextBuilder.forClient().build()
                                    // val sslCtx: SslContext =
                                    // SslContextBuilder.forClient().trustManager(InsecureTrustManag
                                    // erFactory.INSTANCE).build()
                                    addFirst(
                                        "ssl",
                                        sslCtx.newHandler(socketChannel.alloc(), host, port)
                                    )
                                } else {
                                    LogContext.log.w(tag, "Working in wss self-signed SECURE mode")
                                    requireNotNull(certificateBytes) {
                                        "In WSS Secure mode, you must set server certificate by " +
                                            "calling " +
                                            "SslUtils.certificateInputStream."
                                    }

                                    val sslContextPair =
                                        SslUtils.getSSLContext(getCertificateInputStream()!!)

                                    // val sslEngine = sslContextPair.first.createSSLEngine(host,
                                    // port).apply {
                                    //                                    useClientMode = true
                                    //                                }
                                    // addFirst("ssl", SslHandler(sslEngine))

                                    val sslCtx: SslContext =
                                        SslContextBuilder.forClient().trustManager(
                                            sslContextPair.second
                                        ).build()
                                    addFirst(
                                        "ssl",
                                        sslCtx.newHandler(socketChannel.alloc(), host, port)
                                    )

                                    // val sslEngine =
                                    // SSLContext.getDefault().createSSLEngine().apply {
                                    // useClientMode = true }
                                    // addFirst("ssl", SslHandler(sslEngine))
                                }
                            }
                        }
                        addLast(HttpClientCodec())
                        addLast(HttpObjectAggregator(1 shl 20))
                        /**
                         * A [ChannelHandler] that adds support for writing a large data stream
                         * asynchronously neither spending a lot of memory nor getting
                         * [OutOfMemoryError].
                         */
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
     * If netty client has already been released, call this method will throw
     * [java.util.concurrent.RejectedExecutionException]: event executor terminated
     */
    suspend fun connect(): ClientConnectStatus = suspendCancellableCoroutine { cont ->
        LogContext.log.i(tag, "===== connect() current state=${connectStatus.get().name} =====")
        val resumed = AtomicBoolean(false)
        fun resumeOnce(status: ClientConnectStatus) {
            if (cont.isActive && resumed.compareAndSet(false, true)) {
                cont.resume(status)
            }
        }

        fun handleConnectFailure(
            code: Int,
            msg: String?,
            cause: Throwable? = null,
            retry: Boolean = true
        ) {
            connectStatus.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(this@BaseNettyClient, code, msg, cause)
            resumeOnce(connectStatus.get())
            if (retry) doRetry()
        }

        synchronized(this) {
            when (connectStatus.get()) {
                ClientConnectStatus.CONNECTING, ClientConnectStatus.CONNECTED -> {
                    LogContext.log.w(tag, "===== Connecting or already connected =====")
                    resumeOnce(connectStatus.get())
                    return@suspendCancellableCoroutine
                }

                ClientConnectStatus.RELEASING -> {
                    LogContext.log.w(
                        tag,
                        "===== Releasing now. DO NOT connect and stop processing. ====="
                    )
                    resumeOnce(connectStatus.get())
                    return@suspendCancellableCoroutine
                }

                ClientConnectStatus.DISCONNECTING -> {
                    LogContext.log.w(
                        tag,
                        "===== Disconnecting now. DO NOT connect and stop processing. ====="
                    )
                    resumeOnce(connectStatus.get())
                    return@suspendCancellableCoroutine
                }

                else -> LogContext.log.i(tag, "===== Prepare to connect to server =====")
            }
            connectStatus.set(ClientConnectStatus.CONNECTING)
        }
        try { // You call connect() with sync() method like this bellow:
            // bootstrap.connect(host, port).sync()
            // you must handle exception by yourself, because of you want to
            // process connection synchronously. And the connection listener will be ignored
            // regardless of whether you add it.
            //
            // If you want your connection listener work, do like this:
            // bootstrap.connect(host, port).addListener(connectFutureListener)
            // In some cases, although you add your connection listener, you still need to catch
            // some exceptions what your listener can not deal with
            // Just like RejectedExecutionException exception. However, I never catch
            // RejectedExecutionException as I expect. Who can tell me why?

            // Note: sync() blocks the current thread for up to CONNECTION_TIMEOUT_IN_MILLS.
            // This is acceptable on Dispatchers.IO but could starve threads under heavy load.
            // TODO: Consider migrating to fully async addListener-based connection in the future.
            val f = bootstrap.connect(host, port).sync()
            channel = f.channel()
            retryTimes.set(0)
            disconnectManually = false
            if (isWebSocket) {
                val promise = defaultInboundHandler?.channelPromise
                if (promise == null) {
                    LogContext.log.e(
                        tag,
                        "WebSocket channelPromise is null. " +
                            "Handler not initialized properly."
                    )
                    channel.close()
                    handleConnectFailure(
                        ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION,
                        "WebSocket channelPromise not initialized",
                        retry = false
                    )
                    return@suspendCancellableCoroutine
                }
                promise.addListener {
                    if (it.isSuccess) {
                        LogContext.log.i(tag, "=====> WebSocket Connect success <=====")
                        connectStatus.set(ClientConnectStatus.CONNECTED)
                        connectionListener.onConnected(this@BaseNettyClient)
                        resumeOnce(connectStatus.get())
                    } else {
                        LogContext.log.i(tag, "=====> WebSocket Connect failed <=====")
                        handleConnectFailure(
                            ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION,
                            "WebSocket Connect failed",
                            it.cause()
                        )
                    }
                }
            } else {
                // If I use asynchronous way to do connect, it will cause multiple connections
                // if you click Connect and Disconnect repeatedly in a very quick way.

                // There must be a way to solve the problem. Unfortunately, I don't know how to do
                // that now.
                //            bootstrap.connect(host, port).addListener(connectFutureListener)
                f.addListener {
                    if (it.isSuccess) {
                        LogContext.log.i(tag, "=====> Connect success <=====")
                        connectStatus.set(ClientConnectStatus.CONNECTED)
                        connectionListener.onConnected(this@BaseNettyClient)
                        resumeOnce(connectStatus.get())
                    } else {
                        LogContext.log.i(tag, "=====> Connect failed <=====")
                        handleConnectFailure(
                            ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION,
                            "Connect failed"
                        )
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            LogContext.log.e(
                tag,
                "===== RejectedExecutionException. Netty client had already been released. " +
                    "You must re-initialize it again.: ${e.message} ====="
                // If connection has been connected before, [channelInactive] will be called, so the
                // status and
            )
            // listener will be triggered at that time.
            // However, if netty client had been release, call [connect] again will cause exception.
            // So we handle it here.
            handleConnectFailure(
                ClientConnectListener.CONNECTION_ERROR_ALREADY_RELEASED,
                e.message,
                retry = false
            )
        } catch (e: ConnectException) {
            LogContext.log.e(tag, "===== ConnectException: ${e.message} =====")
            handleConnectFailure(
                ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION,
                e.message,
                e
            )
        } catch (e: Exception) {
            LogContext.log.e(tag, "===== Exception: ${e.message} =====", e)
            handleConnectFailure(
                ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION,
                e.message,
                e
            )
        }
    }

    /**
     * After calling this method, you can reuse it again by calling [connect].
     * If you don't want to reconnect it anymore, do not forget to call [release].
     *
     * If current connect state is [ClientConnectStatus.FAILED], this method will also be run and
     * any exception will be ignored.
     *
     * **Remember**, If you call this method, it will not trigger retry process.
     */
    suspend fun disconnectManually(): ClientConnectStatus {
        LogContext.log.w(
            tag,
            "===== disconnectManually() current state=${connectStatus.get().name} ====="
        )
        synchronized(this) {
            val connStatus = connectStatus.get()
            if (ClientConnectStatus.DISCONNECTED == connStatus ||
                ClientConnectStatus.UNINITIALIZED == connStatus
            ) {
                LogContext.log.w(
                    tag,
                    "Socket is not connected or already disconnected or not initialized."
                )
                return connectStatus.get()
            } else if (ClientConnectStatus.DISCONNECTING == connectStatus.get()) {
                LogContext.log.w(tag, "Socket is disconnecting. Stop processing.")
                return connectStatus.get()
            } else if (ClientConnectStatus.CONNECTING == connectStatus.get()) {
                LogContext.log.w(
                    tag,
                    "Socket is connecting. Cannot disconnect during connection attempt."
                )
                return connectStatus.get()
            }
            connectStatus.set(ClientConnectStatus.DISCONNECTING)
            disconnectManually = true
        }

        stopRetryHandler()
        defaultInboundHandler?.release()

        if (!::channel.isInitialized) {
            LogContext.log.w(tag, "Channel not initialized. Set DISCONNECTED.")
            connectStatus.set(ClientConnectStatus.DISCONNECTED)
            connectionListener.onDisconnected(this@BaseNettyClient, byRemote = false)
            return connectStatus.get()
        }

        return withTimeoutOrNull(disconnectTimeoutInMillis) {
            suspendCancellableCoroutine { cont ->
                val resumed = AtomicBoolean(false)

                fun complete(status: ClientConnectStatus, notify: () -> Unit) {
                    if (!cont.isActive || !resumed.compareAndSet(false, true)) return
                    notify()
                    cont.resume(status)
                }

                runCatching {
                    val future = channel.disconnect()
                    cont.invokeOnCancellation { future.cancel(false) }
                    future.addListener { f ->
                        if (f.isSuccess) {
                            LogContext.log.w(tag, "===== disconnectManually() done =====")
                            complete(ClientConnectStatus.DISCONNECTED) {
                                connectStatus.set(ClientConnectStatus.DISCONNECTED)
                                connectionListener.onDisconnected(
                                    this@BaseNettyClient,
                                    byRemote = false
                                )
                            }
                        } else {
                            LogContext.log.w(tag, "===== disconnectManually() failed =====")
                            complete(ClientConnectStatus.FAILED) {
                                connectStatus.set(ClientConnectStatus.FAILED)
                                connectionListener.onFailed(
                                    this@BaseNettyClient,
                                    ClientConnectListener.DISCONNECT_MANUALLY_ERROR,
                                    "Disconnect manually failed"
                                )
                            }
                        }
                    }
                }.onFailure {
                    LogContext.log.e(tag, "disconnectManually error.", it)
                    complete(ClientConnectStatus.FAILED) {
                        connectStatus.set(ClientConnectStatus.FAILED)
                        connectionListener.onFailed(
                            this@BaseNettyClient,
                            ClientConnectListener.DISCONNECT_MANUALLY_EXCEPTION,
                            "Disconnect manually exception"
                        )
                    }
                }
            }
        } ?: run {
            LogContext.log.e(tag, "disconnectManually timeout.")
            connectStatus.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(
                this@BaseNettyClient,
                ClientConnectListener.DISCONNECT_MANUALLY_ERROR,
                "Disconnect manually timeout"
            )
            connectStatus.get()
        }
    }

    fun doRetry() {
        if (released) return
        if (retryProcess()) return

        synchronized(retryLock) {
            if (released || disconnectManually) return
            retryTimes.getAndIncrement()
            if (retryTimes.get() > retryStrategy.getMaxTimes()) {
                LogContext.log.e(
                    tag,
                    "===== Connect failed in doRetry() " +
                        "- Exceed max retry times. ====="
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
                    "Reconnect($retryTimes) in " +
                        "${retryStrategy.getDelayInMillSec(retryTimes.get())}ms" +
                        " | current state=${connectStatus.get().name}"
                )
                retryJob?.cancel()
                retryJob = retryScope.launch {
                    runCatching {
                        delay(
                            retryStrategy.getDelayInMillSec(retryTimes.get())
                        )
                        ensureActive()
                        connect()
                    }.onFailure {
                        LogContext.log.e(tag, "Do retry failed.", it)
                    }
                }
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
     * Release netty client using **syncUninterruptibly** method.(Full release will cost almost
     * 2200ms.)
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
                ClientConnectStatus.RELEASING == connectStatus.get() ||
                ClientConnectStatus.DISCONNECTING == connectStatus.get()
            ) {
                LogContext.log.w(tag, "Releasing now or already released or disconnecting or not initialized")
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            connectStatus.set(ClientConnectStatus.RELEASING)
        }
        released = true
        disconnectManually = true
        LogContext.log.w(tag, "Releasing retry handler...")
        stopRetryHandler()
        retryScope.cancel()
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
                    // closeFuture().syncUninterruptibly() // syncUninterruptibly() will stuck here.
                    // Why???
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
        synchronized(retryLock) {
            retryJob?.cancel()
            retryJob = null
            retryTimes.set(0)
        }
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
        require(cmd is String || cmd is ByteArray) {
            "$cmdTag: Command must be either String or ByteArray."
        }
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
                    LogContext.log.i(
                        cmdTag,
                        if (showContent) "$cmdMsg=$cmd" else cmdMsg,
                        fullOutput = fullOutput
                    )
                }
            }

            is ByteArray -> {
                isStringCmd = false
                stringCmd = null
                bytesCmd = Unpooled.wrappedBuffer(cmd)
                if (showLog) {
                    val cmdMsg = "$logPrefix[${cmd.size}]"
                    val hex: String? =
                        if (showContent) cmd.toHexString() else null
                    LogContext.log.i(
                        cmdTag,
                        if (hex ==
                            null
                        ) {
                            cmdMsg
                        } else {
                            "$cmdMsg=HEX[$hex]"
                        },
                        fullOutput = fullOutput
                    )
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

    /**
     * For general socket(NOT WebSocket), when send string to server,
     * the `\n` will be appended automatically.
     *
     * @param byteOrder **Deprecated — has no effect.** The byte array
     *   is sent as-is regardless of byte order.
     */
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
        fullOutput = fullOutput
    )

    /**
     * This method only works in WebSocket mode.
     *
     * @param byteOrder **Deprecated — has no effect.** The byte array
     *   is sent as-is regardless of byte order.
     */
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
        fullOutput = fullOutput
    )

    // ================================================
}
