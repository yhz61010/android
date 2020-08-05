package com.ho1ho.socket_sdk.framework

import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.socket_sdk.framework.inter.NettyConnectionListener
import com.ho1ho.socket_sdk.framework.retry_strategy.ConstantRetry
import com.ho1ho.socket_sdk.framework.retry_strategy.base.RetryStrategy
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.socket.ServerSocketChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.stream.ChunkedWriteHandler
import java.net.URI

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午2:34
 */
abstract class BaseNettyServer protected constructor(
    override val host: String,
    override val port: Int,
    override val connectionListener: NettyConnectionListener,
    override val retryStrategy: RetryStrategy = ConstantRetry()
) : BaseNetty(host, port, connectionListener, retryStrategy) {
    @Suppress("unused")
    protected constructor(
        webSocketUri: URI,
        connectionListener: NettyConnectionListener,
        retryStrategy: RetryStrategy = ConstantRetry()
    ) : this(webSocketUri.host, webSocketUri.port, connectionListener, retryStrategy) {
        this.webSocketUri = webSocketUri
        LLog.w(tag, "WebSocket mode. Uri=${webSocketUri} host=${webSocketUri.host} port=${webSocketUri.port}")
    }

    init {
        bootstrap.option(ChannelOption.SO_REUSEADDR, true)
    }

    fun initHandler(handler: BaseChannelInboundHandler<*>?) {
        defaultInboundHandler = handler
        channelInitializer = object : ChannelInitializer<ServerSocketChannel>() {
            override fun initChannel(serverSocketChannel: ServerSocketChannel) {
                val pipeline = serverSocketChannel.pipeline()
                if (isWebSocket) {
                    if ((webSocketUri?.scheme ?: "").startsWith("wss", ignoreCase = true)) {
                        LLog.w(tag, "Working in wss mode")
                        val sslCtx: SslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                        pipeline.addFirst(sslCtx.newHandler(serverSocketChannel.alloc(), host, port))
                    }
                    pipeline.addLast(HttpClientCodec())
                    pipeline.addLast(HttpObjectAggregator(65536))
                    /** A [ChannelHandler] that adds support for writing a large data stream asynchronously
                     * neither spending a lot of memory nor getting [OutOfMemoryError]. */
                    pipeline.addLast(ChunkedWriteHandler())
                    pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE)
                }
                addLastToPipeline(pipeline)
                defaultInboundHandler?.let {
                    pipeline.addLast("default-inbound-handler", it)
                }
            }
        }
        bootstrap.handler(channelInitializer)
    }
}