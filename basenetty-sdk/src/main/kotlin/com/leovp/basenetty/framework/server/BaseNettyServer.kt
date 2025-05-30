package com.leovp.basenetty.framework.server

import com.leovp.basenetty.framework.base.BaseNetty
import com.leovp.basenetty.framework.base.ServerConnectStatus
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
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
    timeout: Int = CONNECTION_TIMEOUT_IN_MILLS,
) : BaseNetty {

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

    private fun isValidExecuteCommandEnv(clientChannel: Channel, cmdTag: String, cmd: Any?): Boolean {
        if (cmd == null) {
            LogContext.log.e(cmdTag, "The command is null. Stop processing.")
            return false
        }
        require(cmd is String || cmd is ByteArray) { "$cmdTag: Command must be either String or ByteArray." }
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
        byteOrder: ByteOrder,
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

            else -> throw IllegalArgumentException("$cmdTag: Command must be either String or ByteArray")
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

    /** For general socket(NOT WebSocket), when send string to server, the `\n` will be appended automatically. */
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
        showContent = showContent, showLog = showLog, fullOutput = fullOutput, byteOrder = byteOrder
    )

    /** This method only works in WebSocket mode. */
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
        showContent = showContent, showLog = showLog, fullOutput = fullOutput, byteOrder = byteOrder
    )

    // ================================================
}
