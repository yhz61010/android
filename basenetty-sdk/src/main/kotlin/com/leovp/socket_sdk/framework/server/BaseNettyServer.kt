package com.leovp.socket_sdk.framework.server

import com.leovp.lib_bytes.toHexString
import com.leovp.lib_bytes.toHexStringLE
import com.leovp.log_sdk.LogContext
import com.leovp.socket_sdk.framework.base.BaseNetty
import com.leovp.socket_sdk.framework.base.ServerConnectStatus
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
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
import io.netty.handler.stream.ChunkedWriteHandler
import java.nio.ByteOrder
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午2:34
 */
abstract class BaseNettyServer protected constructor(
    private val port: Int,
    val connectionListener: ServerConnectListener<BaseNettyServer>,
    internal var isWebSocket: Boolean = false,
    internal var webSocketPath: String = "/",
    timeout: Int = CONNECTION_TIMEOUT_IN_MILLS
) : BaseNetty() {

    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
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
    private var channelInitializer: ChannelInitializer<*>? = null
    var defaultServerInboundHandler: BaseServerChannelInboundHandler<*>? = null
        protected set

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
                        //   if ((webSocketPath?.scheme ?: "").startsWith("wss", ignoreCase = true)) {
                        //       LogContext.log.w(tag, "Working in wss mode")
                        //       val sslCtx: SslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                        //       // FIXME
                        // //   pipeline.addFirst(sslCtx.newHandler(serverSocketChannel.alloc(), host, port))
                        //   }
                        addLast(HttpServerCodec())
                        addLast(HttpObjectAggregator(65536))
                        /** A [ChannelHandler] that adds support for writing a large data stream asynchronously
                         * neither spending a lot of memory nor getting [OutOfMemoryError]. */
                        addLast(ChunkedWriteHandler())
                        // FIXME If add this, the server can not receive client message.
                        //                        addLast(WebSocketServerCompressionHandler())
                        addLast(WebSocketServerProtocolHandler(webSocketPath))
                    } else {
                        //                        addLast(DelimiterBasedFrameDecoder(65535, *Delimiters.lineDelimiter()))
                        //                        addLast(StringDecoder())
                        //                        addLast(StringEncoder())
                    }
                    addLastToPipeline(this)
                    defaultServerInboundHandler?.let { addLast("default-server-inbound-handler", it) }
                }
            }
        }
        bootstrap.childHandler(channelInitializer)
    }

    /**
     * If server has already been released, call this method will
     * throw [java.util.concurrent.RejectedExecutionException]: event executor terminated
     */
    //    @Throws(RejectedExecutionException::class)
    @Synchronized
    fun startServer() {
        LogContext.log.i(tag, "===== connect() current state=${connectState.get().name} =====")
        if (connectState.get() == ServerConnectStatus.STARTED) {
            LogContext.log.w(tag, "===== Already started or not initialized =====")
            return
        }
        try {
            serverChannel = bootstrap.bind(port).sync().channel()
            connectState.set(ServerConnectStatus.STARTED)
            LogContext.log.i(tag, "===== Start successfully =====")
            connectionListener.onStarted(this)
            // After running this line below, the process will stuck there and waiting client connection
            serverChannel.closeFuture().sync()
            // When serverChannel.close be executed, the process will continue to run.
            LogContext.log.i(tag, "===== Stopping server... =====")
        } catch (e: RejectedExecutionException) {
            LogContext.log.e(tag, "===== RejectedExecutionException: ${e.message} =====", e)
            LogContext.log.e(tag, "Netty server had already been released. You must re-initialize it again.")
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onStartFailed(this, ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED, e.message)
        } catch (e: Exception) {
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onStartFailed(this, ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR, e.message)
        }
    }

    /**
     * Stop and release server using **syncUninterruptibly** method.(Full release will cost almost 4s.) So you'd better NOT call this method in main thread.
     *
     * Once you call this method, you can not start server again simply by calling [startServer] because of the Server Netty object will be released.
     * If you want to start server again, you must recreate the Server Netty object.
     */
    fun stopServer(): Boolean {
        LogContext.log.w(tag, "===== stopServer() current state=${connectState.get().name} =====")
        if (!::serverChannel.isInitialized || ServerConnectStatus.UNINITIALIZED == connectState.get()) {
            LogContext.log.w(tag, "Already released or not initialized")
            return false
        }
        connectState.set(ServerConnectStatus.UNINITIALIZED)

        LogContext.log.w(tag, "Releasing default server inbound handler first...")
        defaultServerInboundHandler?.release()
        defaultServerInboundHandler = null
        channelInitializer = null

        serverChannel.run {
            LogContext.log.w(tag, "Closing channel...")
            runCatching {
                pipeline().removeAll { true }
                closeFuture()
                close().syncUninterruptibly()
            }.onFailure { LogContext.log.e(tag, "Close channel error.", it) }
        }

        runCatching {
            LogContext.log.w(tag, "Releasing bossGroup...")
            bossGroup.shutdownGracefully().syncUninterruptibly() // Will not stuck here.
        }.onFailure { LogContext.log.e(tag, "Shutdown bossGroup error.", it) }

        runCatching {
            LogContext.log.w(tag, "Releasing workerGroup...")
            workerGroup.shutdownGracefully().syncUninterruptibly() // Will not stuck here.
        }.onFailure { LogContext.log.e(tag, "Shutdown workerGroup error.", it) }

        LogContext.log.w(tag, "=====> Server released <=====")
        connectState.set(ServerConnectStatus.STOPPED)
        connectionListener.onStopped()
        return true
    }

    // ================================================

    private fun isValidExecuteCommandEnv(clientChannel: Channel, cmdTypeAndId: String, cmd: Any?): Boolean {
        if (cmd == null) {
            LogContext.log.e(cmdTypeAndId, "The command is null. Stop processing.")
            return false
        }
        if (cmd !is String && cmd !is ByteArray) {
            throw IllegalArgumentException("Command must be either String or ByteArray.")
        }
        if (!clientChannel.isActive) {
            LogContext.log.e(cmdTypeAndId, "Client channel is not active. Can not send command.")
            return false
        }
        return true
    }

    /**
     * @param isPing Only works in WebSocket mode
     */
    private fun executeUnifiedCommand(
        clientChannel: Channel,
        cmdTypeAndId: String,
        cmdDesc: String?,
        cmd: Any?,
        isPing: Boolean,
        showContent: Boolean,
        showLog: Boolean,
        byteOrder: ByteOrder
    ): Boolean {
        if (!isValidExecuteCommandEnv(clientChannel, cmdTypeAndId, cmd)) {
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
                    if (showContent) LogContext.log.i(cmdTypeAndId, "$logPrefix[${cmd.length}]=$cmd")
                    else LogContext.log.i(cmdTypeAndId, "$logPrefix[${cmd.length}]")
                }
            }
            is ByteArray -> {
                isStringCmd = false
                stringCmd = null
                bytesCmd = Unpooled.wrappedBuffer(cmd)
                if (showLog) {
                    if (showContent) {
                        val bytesContent = if (ByteOrder.BIG_ENDIAN == byteOrder) cmd.toHexString() else cmd.toHexStringLE()
                        LogContext.log.i(cmdTypeAndId, "$logPrefix[${cmd.size}]=HEX[$bytesContent]")
                    } else LogContext.log.i(cmdTypeAndId, "$logPrefix[${cmd.size}]")
                }
            }
            else -> throw IllegalArgumentException("Command must be either String or ByteArray")
        }

        if (isWebSocket) {
            if (isPing) clientChannel.writeAndFlush(
                PingWebSocketFrame(if (isStringCmd) Unpooled.wrappedBuffer(stringCmd!!.toByteArray()) else bytesCmd)
            ) else clientChannel.writeAndFlush(if (isStringCmd) TextWebSocketFrame(stringCmd) else BinaryWebSocketFrame(bytesCmd))
        } else {
            clientChannel.writeAndFlush(if (isStringCmd) "$stringCmd\n" else bytesCmd)
        }
        return true
    }

    @JvmOverloads
    fun executeCommand(
        clientChannel: Channel,
        cmd: Any?,
        cmdDesc: String? = null,
        cmdTypeAndId: String = tag,
        showContent: Boolean = true,
        showLog: Boolean = true,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) =
        executeUnifiedCommand(
            clientChannel, cmdTypeAndId, cmdDesc, cmd, isPing = false,
            showContent = showContent, showLog = showLog, byteOrder = byteOrder
        )

    @Suppress("unused")
    @JvmOverloads
    fun executePingCommand(
        clientChannel: Channel,
        cmd: Any?,
        cmdDesc: String? = null,
        cmdTypeAndId: String = tag,
        showContent: Boolean = true,
        showLog: Boolean = true,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) =
        executeUnifiedCommand(
            clientChannel, cmdTypeAndId, cmdDesc, cmd, isPing = true,
            showContent = showContent, showLog = showLog, byteOrder = byteOrder
        )

    // ================================================
}
