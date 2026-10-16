package com.leovp.basenetty.framework.server

import com.leovp.basenetty.framework.base.BaseNetty
import com.leovp.basenetty.framework.base.ServerConnectStatus
import com.leovp.basenetty.framework.base.awaitCompletion
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GlobalEventExecutor
import io.netty.handler.stream.ChunkedWriteHandler
import java.nio.ByteOrder
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午2:34
 */
abstract class BaseNettyServer protected constructor(
    private val port: Int,
    val connectionListener: ServerConnectListener<BaseNettyServer>,
    internal var isWebSocket: Boolean = false,
    internal var webSocketPath: String = "/",
    timeout: Int = CONNECTION_TIMEOUT_IN_MILLS,
) : BaseNetty {

    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000

        /**
         * Hard limit each event loop group gets to drain. The quiet period is passed as 0, so
         * this is the only wait that remains.
         */
        private const val SHUTDOWN_TIMEOUT_IN_SECONDS = 2L

        /**
         * Upper bound for the whole netty shutdown. See [stopServer].
         *
         * Budgeted above [SHUTDOWN_TIMEOUT_IN_SECONDS] on purpose: the two groups overlap, so the
         * shutdown itself costs at most one of them, and the remainder is headroom for closing
         * the channel first.
         */
        private const val NETTY_SHUTDOWN_TIMEOUT_IN_MILLS = 3_000L

        /**
         * Upper bound for the bind in [startServer].
         *
         * A bind is a local socket operation handed to the boss loop, so this is not a budget
         * for a slow network. It is what keeps a wedged boss loop from parking the caller here
         * for good while it holds [lifecycleMutex] - the same mutex [stopServer] needs before it
         * can release anything, so an unbounded wait here would stall every teardown queued
         * behind it as well.
         */
        private const val NETTY_BIND_TIMEOUT_IN_MILLS = 3_000L
    }

    val tag: String by lazy { getTagName() }
    abstract fun getTagName(): String

    private val bossGroup = NioEventLoopGroup()
    private val workerGroup = NioEventLoopGroup()
    private val bootstrap = ServerBootstrap()
        .group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel::class.java)
        .handler(LoggingHandler(LogLevel.INFO))
        .option(ChannelOption.SO_BACKLOG, 1024)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(
            ChannelOption.CONNECT_TIMEOUT_MILLIS,
            timeout
        )

    private lateinit var serverChannel: Channel

    /**
     * Serializes [startServer] against [stopServer].
     *
     * It replaces the `@Synchronized` that used to guard start alone - Kotlin does not allow that
     * annotation on a suspend function, and a monitor would block a dispatcher thread here anyway.
     *
     * Stop has to share it. Now that start returns as soon as the port is bound, a stop that
     * arrives while the bind is still in flight would find [serverChannel] uninitialized, return
     * false, and leave the server it was asked to stop listening on the port for good.
     */
    private val lifecycleMutex = Mutex()
    private var channelInitializer: ChannelInitializer<*>? = null
    var defaultServerInboundHandler: BaseServerChannelInboundHandler<*>? = null
        protected set

    internal val clients: ChannelGroup =
        DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

    @Volatile
    internal var connectState: AtomicReference<ServerConnectStatus> = AtomicReference(
        ServerConnectStatus.UNINITIALIZED
    )

    open fun addLastToPipeline(pipeline: ChannelPipeline) {}

    fun initHandler(handler: BaseServerChannelInboundHandler<*>?) {
        defaultServerInboundHandler = handler
        channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                with(socketChannel.pipeline()) {
                    if (isWebSocket) {
                        // if ((webSocketPath?.scheme ?: "").startsWith("wss", ignoreCase = true)) {
                        //       LogContext.log.w(tag, "Working in wss mode")
                        // val sslCtx: SslContext =
                        // SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.IN
                        // STANCE).build()
                        //       // FIXME
                        // // pipeline.addFirst(sslCtx.newHandler(serverSocketChannel.alloc(), host,
                        // port))
                        //   }
                        addLast(HttpServerCodec())
                        addLast(HttpObjectAggregator(65536))
                        /**
                         * A [ChannelHandler] that adds support for writing a large data stream
                         * asynchronously neither spending a lot of memory nor getting
                         * [OutOfMemoryError].
                         */
                        addLast(ChunkedWriteHandler())
                        // FIXME If add this, the server can not receive client message.
                        //                        addLast(WebSocketServerCompressionHandler())
                        addLast(WebSocketServerProtocolHandler(webSocketPath))
                    } else {
                        // addLast(DelimiterBasedFrameDecoder(65535, *Delimiters.lineDelimiter()))
                        //                        addLast(StringDecoder())
                        //                        addLast(StringEncoder())
                    }
                    addLastToPipeline(this)
                    defaultServerInboundHandler?.let {
                        addLast("default-server-inbound-handler", it)
                    }
                }
            }
        }
        bootstrap.childHandler(channelInitializer)
    }

    /**
     * Binds the server port and returns. It does **not** block for the server's lifetime.
     *
     * The previous implementation parked the caller in `serverChannel.closeFuture().sync()` until
     * the server stopped, which had two consequences that could not be worked around by callers:
     * a caller writing `server = Server(...).also { it.startServer() }` never reached the
     * assignment while the server was up, and the `@Synchronized` monitor was held for the whole
     * session. To learn when the server is gone, use [ServerConnectListener.onStopped] or the
     * completion of [stopServer].
     *
     * A server that [stopServer] has already released - including one that was stopped before it
     * was ever started - is refused up front and reported through
     * [ServerConnectListener.onStartFailed] with
     * [ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED]. A bind that fails with
     * [java.util.concurrent.RejectedExecutionException] (event executor terminated) is reported
     * the same way rather than thrown.
     *
     * The bind itself is bounded by [NETTY_BIND_TIMEOUT_IN_MILLS] and reported through
     * [ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR] when it runs out. That bound
     * belongs here because this wait holds [lifecycleMutex].
     */
    suspend fun startServer() {
        lifecycleMutex.withLock {
            LogContext.log.i(
                tag,
                "===== startServer() current state=${connectState.get().name} ====="
            )
            if (connectState.get() == ServerConnectStatus.STARTED) {
                LogContext.log.w(tag, "===== Already started or not initialized =====")
                return@withLock
            }
            if (connectState.get() == ServerConnectStatus.STOPPED) {
                // Decided by state, not by netty thread timing: a stop that got the mutex first
                // has released the groups (see stopServer), and binding on them would only fail
                // the same way - or, if their shutdown ran out of budget, hang here holding the
                // mutex. The ALREADY_RELEASED branch below stays for a group terminated by
                // other means.
                //
                // The state stays STOPPED. Moving it to FAILED would let a later stopServer()
                // past its guard - serverChannel may well be initialised from an earlier start -
                // and run the whole teardown a second time, onStopped() included.
                LogContext.log.e(tag, "Netty server had already been released. Re-create it.")
                connectionListener.onStartFailed(
                    this,
                    ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED,
                    "Server already released"
                )
                return@withLock
            }
            var bindFuture: ChannelFuture? = null
            try {
                val future = bootstrap.bind(port)
                bindFuture = future
                // Bounded like the shutdown is, and for a stronger reason: this wait holds
                // lifecycleMutex, so a bind that never completes takes stopServer() down with
                // it. Held in a local val rather than read back through the nullable var, which
                // a lambda cannot smart-cast.
                val bound = withTimeoutOrNull(NETTY_BIND_TIMEOUT_IN_MILLS) {
                    future.awaitCompletion()
                }
                if (bound == null) {
                    LogContext.log.e(
                        tag,
                        "===== Bind did not finish within " +
                            "${NETTY_BIND_TIMEOUT_IN_MILLS}ms ====="
                    )
                    // Close it: the bind may still land inside the boss loop, and serverChannel
                    // is never assigned on this path, so stopServer() would find nothing to
                    // close and the port would stay held for good.
                    future.channel().close()
                    // FAILED, not STOPPED: the two event loop groups are still alive, and a
                    // later stopServer() must stay free to release them.
                    connectState.set(ServerConnectStatus.FAILED)
                    connectionListener.onStartFailed(
                        this,
                        ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR,
                        "Bind timed out after ${NETTY_BIND_TIMEOUT_IN_MILLS}ms"
                    )
                    return@withLock
                }
                serverChannel = future.channel()
                connectState.set(ServerConnectStatus.STARTED)
                LogContext.log.i(tag, "===== Start successfully =====")
                // Observe the close instead of blocking on it. The listener costs nothing and
                // keeps the log line the old closeFuture().sync() produced when it returned.
                serverChannel.closeFuture().addListener {
                    LogContext.log.i(tag, "===== Server channel closed =====")
                }
                connectionListener.onStarted(this)
            } catch (e: RejectedExecutionException) {
                LogContext.log.e(tag, "===== RejectedExecutionException: ${e.message} =====", e)
                LogContext.log.e(
                    tag,
                    "Netty server had already been released. You must re-initialize it again."
                )
                connectState.set(ServerConnectStatus.FAILED)
                connectionListener.onStartFailed(
                    this,
                    ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED,
                    e.message
                )
            } catch (e: CancellationException) {
                // Cancelling the caller is not a start failure. Letting it fall through to the
                // Exception branch below would hand the listener an onStartFailed() nobody asked
                // for - CancellationException is an ordinary Exception subclass on the JVM.
                //
                // The bind itself carries on inside the boss loop and may well succeed. Close the
                // channel, or a cancelled start leaves a bound server nothing can ever stop:
                // serverChannel is never assigned, so stopServer() would return false for good.
                // Only the port is freed here; the two event loop groups outlive a cancelled start
                // just as they outlive a failed one, and need a new instance to be left behind.
                bindFuture?.channel()?.close()
                throw e
            } catch (e: Exception) {
                connectState.set(ServerConnectStatus.FAILED)
                connectionListener.onStartFailed(
                    this,
                    ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR,
                    e.message
                )
            }
        }
    }

    /**
     * Stops and releases the server, suspending instead of blocking the caller's thread.
     *
     * Once you call this method, you can not start server again simply by calling [startServer]
     * because of the Server Netty object will be released.
     * If you want to start server again, you must recreate the Server Netty object.
     *
     * The netty part is bounded by [NETTY_SHUTDOWN_TIMEOUT_IN_MILLS]. That bound belongs here, at
     * the resource boundary, rather than in the caller: a shutdown that cannot finish must not
     * hold a teardown sequence forever, yet the state transition and [ServerConnectListener]
     * notification after it still have to run.
     *
     * Once past the guard, the teardown runs under [NonCancellable]: cancelling the caller cannot
     * shorten a resource release, it can only abandon it half way with the state left on
     * "stopping", after which every later call returns false and the event loops live for good.
     * The timeout above is the only thing that cuts it short, and it still works inside
     * [NonCancellable]. A caller cancelled while still waiting for [lifecycleMutex] returns at
     * once: it has touched nothing yet.
     *
     * Serialized against [startServer] through [lifecycleMutex], so a stop issued while the port
     * is still being bound waits for that bind rather than returning false and orphaning it.
     *
     * The state guard rejects a call that overlaps another one and a call on a server that is
     * already stopped, so a repeated stop returns false without touching netty again and
     * [ServerConnectListener.onStopped] fires once per server.
     *
     * A stop that arrives before the server was ever started also returns false - nothing was
     * running, so [ServerConnectListener.onStopped] is not fired - but it is not a no-op: it
     * releases both event loop groups, which otherwise outlive a never-started server for good,
     * and leaves the state STOPPED. That settles the race with a [startServer] still waiting for
     * [lifecycleMutex]: it sees STOPPED and reports
     * [ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED] through
     * [ServerConnectListener.onStartFailed] instead of bringing up a server nothing will stop.
     * The mutex alone only serializes the two; it does not make a stop that got there first win.
     *
     * [ServerConnectListener] callbacks run while [lifecycleMutex] is held. They are not suspend
     * and the mutex is not reentrant, so a listener that wants to call [startServer] or
     * [stopServer] in response must launch that call on another coroutine.
     */
    suspend fun stopServer(): Boolean = lifecycleMutex.withLock {
        LogContext.log.w(tag, "===== stopServer() current state=${connectState.get().name} =====")
        val state = connectState.get()
        if (ServerConnectStatus.STOPPED == state) {
            LogContext.log.w(tag, "Already released")
            return@withLock false
        }
        if (!::serverChannel.isInitialized) {
            releaseUnstartedServer()
            return@withLock false
        }
        if (ServerConnectStatus.UNINITIALIZED == state) {
            // Defence in depth. The mutex serialises stops and the finally below always lands on
            // STOPPED, so no caller should observe UNINITIALIZED with serverChannel initialised.
            LogContext.log.w(tag, "Already stopping")
            return@withLock false
        }
        connectState.set(ServerConnectStatus.UNINITIALIZED)
        withContext(NonCancellable) {
            try {
                // Inside the try as well: releaseInboundHandler() only catches Exception, and an
                // Error out of subclass code must not leave the state on UNINITIALIZED either.
                releaseInboundHandler()
                shutdownBounded { shutdownNetty() }
            } finally {
                // In a finally, because the KDoc above promises these two run regardless.
                // awaitOrLog() only guards the awaits; a throw from close() or
                // shutdownGracefully() itself would escape withTimeoutOrNull and this
                // withContext, and the state would stay UNINITIALIZED - where every later
                // stopServer() returns false at the "Already stopping" guard and onStopped()
                // never arrives at all.
                LogContext.log.w(tag, "=====> Server released <=====")
                connectState.set(ServerConnectStatus.STOPPED)
                // Guarded like releaseInboundHandler(): subclass code, and a throw from this
                // finally would also discard whatever the teardown above was already raising.
                try {
                    connectionListener.onStopped()
                } catch (e: Exception) {
                    LogContext.log.e(tag, "onStopped() error.", e)
                }
            }
        }
        return@withLock true
    }

    /**
     * Stop before start. There is no channel to close, but the two event loop groups were built
     * with this object and would otherwise live for good. Terminating them here, inside the
     * mutex, is also what decides the race this call just won: see [stopServer].
     */
    private suspend fun releaseUnstartedServer() = withContext(NonCancellable) {
        LogContext.log.w(tag, "Not started; releasing the event loops so a later start is refused")
        try {
            releaseInboundHandler()
            shutdownBounded {
                requestEventLoopShutdown()
                awaitEventLoopShutdown()
            }
        } finally {
            // In a finally for the same reason as in stopServer(): this state is what refuses a
            // later startServer(), and leaving it unset would bring up a server that the race
            // this call just won was meant to prevent.
            connectState.set(ServerConnectStatus.STOPPED)
        }
    }

    private fun releaseInboundHandler() {
        // Guarded like every other step: this hands control to subclass code, and letting it
        // throw here would skip the channel and both event loop groups entirely.
        LogContext.log.w(tag, "Releasing default server inbound handler first...")
        try {
            defaultServerInboundHandler?.release()
        } catch (e: Exception) {
            LogContext.log.e(tag, "Release inbound handler error.", e)
        }
        defaultServerInboundHandler = null
        channelInitializer = null
    }

    private suspend fun shutdownBounded(block: suspend () -> Unit) {
        if (withTimeoutOrNull(NETTY_SHUTDOWN_TIMEOUT_IN_MILLS) { block() } == null) {
            LogContext.log.e(
                tag,
                "Netty shutdown did not finish within ${NETTY_SHUTDOWN_TIMEOUT_IN_MILLS}ms. " +
                    "Check whether closing the channel, not the event loops, used up the budget."
            )
        }
    }

    private suspend fun shutdownNetty() {
        LogContext.log.w(tag, "Closing channel...")
        try {
            serverChannel.close().awaitOrLog("Close channel")
        } finally {
            // Reached on the timeout too, as the cancellation it carries passes through here. The
            // request does not suspend, so a close that used up the whole budget still leaves both
            // groups shutting down instead of alive for good - which is the case the budget exists
            // for: a boss loop wedged in a handler is exactly what makes the close hang.
            requestEventLoopShutdown()
        }
        awaitEventLoopShutdown()
    }

    /**
     * Asks both groups to shut down without waiting. Idempotent: a group already shutting down
     * just returns its termination future again.
     *
     * Both groups are asked BEFORE either one is awaited. They are independent, so awaiting them
     * one after the other only adds their quiet periods together instead of letting them
     * overlap - that serial wait is where the old "almost 4s" came from.
     *
     * Quiet period 0: it exists to let in-flight work settle before the loop dies, which buys
     * nothing for a server that is being torn down and whose channel is already closed.
     */
    private fun requestEventLoopShutdown() {
        LogContext.log.w(tag, "Releasing bossGroup and workerGroup...")
        bossGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
        workerGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
    }

    private suspend fun awaitEventLoopShutdown() {
        bossGroup.terminationFuture().awaitOrLog("Shutdown bossGroup")
        workerGroup.terminationFuture().awaitOrLog("Shutdown workerGroup")
    }

    /**
     * Awaits this future and logs an ordinary failure instead of aborting the rest of the
     * teardown. Cancellation is rethrown: it carries the timeout from [stopServer] and must not
     * be downgraded into a logged failure.
     */
    private suspend fun <V> Future<V>.awaitOrLog(what: String) {
        try {
            awaitCompletion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogContext.log.e(tag, "$what error.", e)
        }
    }

    // ================================================

    private fun isValidExecuteCommandEnv(
        clientChannel: Channel,
        cmdTag: String,
        cmd: Any?
    ): Boolean {
        if (cmd == null) {
            LogContext.log.e(cmdTag, "The command is null. Stop processing.")
            return false
        }
        require(cmd is String || cmd is ByteArray) {
            "$cmdTag: Command must be either String or ByteArray."
        }
        return if (!clientChannel.isActive) {
            LogContext.log.e(cmdTag, "Client channel is not active. Can not send command.")
            false
        } else {
            true
        }
    }

    /**
     * For general socket(NOT WebSocket), when send string to server,
     * the `\n` will be appended automatically.
     *
     * @param isPing Only works in WebSocket mode.
     */
    private fun executeUnifiedCommand(
        clientChannel: Channel,
        cmdTag: String,
        cmdDesc: String?,
        cmd: Any?,
        isPing: Boolean,
        showContent: Boolean,
        showLog: Boolean,
        fullOutput: Boolean,
    ): Boolean {
        if (!isValidExecuteCommandEnv(clientChannel, cmdTag, cmd)) {
            return false
        }
        val stringCmd: String?
        val bytesCmd: ByteBuf?
        val isStringCmd: Boolean
        val logPrefix = if (cmdDesc.isNullOrBlank()) "exe" else "exe[$cmdDesc]"
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
                        if (hex == null) cmdMsg else "$cmdMsg=HEX[$hex]",
                        fullOutput = fullOutput
                    )
                }
            }

            else -> throw IllegalArgumentException(
                "$cmdTag: Command must be either String or ByteArray"
            )
        }

        if (isWebSocket) {
            if (isPing) {
                val pingByteBuf = if (isStringCmd) {
                    requireNotNull(stringCmd)
                    Unpooled.wrappedBuffer(stringCmd.toByteArray())
                } else {
                    bytesCmd
                }
                clientChannel.writeAndFlush(PingWebSocketFrame(pingByteBuf))
            } else {
                clientChannel.writeAndFlush(
                    if (isStringCmd) {
                        TextWebSocketFrame(stringCmd)
                    } else {
                        BinaryWebSocketFrame(bytesCmd)
                    }
                )
            }
        } else {
            clientChannel.writeAndFlush(if (isStringCmd) "$stringCmd\n" else bytesCmd)
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
        clientChannel: Channel,
        cmd: Any?,
        cmdDesc: String? = null,
        cmdTag: String = tag,
        showContent: Boolean = true,
        showLog: Boolean = true,
        fullOutput: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    ) = executeUnifiedCommand(
        clientChannel, cmdTag, cmdDesc, cmd, isPing = false,
        showContent = showContent, showLog = showLog, fullOutput = fullOutput
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
        clientChannel: Channel,
        cmd: Any?,
        cmdDesc: String? = null,
        cmdTag: String = tag,
        showContent: Boolean = true,
        showLog: Boolean = true,
        fullOutput: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    ) = executeUnifiedCommand(
        clientChannel, cmdTag, cmdDesc, cmd, isPing = true,
        showContent = showContent, showLog = showLog, fullOutput = fullOutput
    )

    // ================================================
}
