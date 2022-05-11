package com.leovp.demo.basic_components.examples.audio.sender.base

import com.leovp.socket_sdk.framework.base.decoder.CustomSocketByteStreamDecoder
import com.leovp.socket_sdk.framework.client.BaseNettyClient
import com.leovp.socket_sdk.framework.client.ClientConnectListener
import com.leovp.socket_sdk.framework.client.retry_strategy.base.RetryStrategy
import io.netty.channel.ChannelPipeline
import java.net.URI

/**
 * Author: Michael Leo
 * Date: 20-11-10 上午10:30
 */
class AudioSenderWebSocket(webSocketUri: URI, connectionListener: ClientConnectListener<BaseNettyClient>, trustAllServers: Boolean, retryStrategy: RetryStrategy) :
    BaseNettyClient(webSocketUri, connectionListener, trustAllServers, retryStrategy) {
    override fun getTagName() = "AudioSenderWS"

    override fun addLastToPipeline(pipeline: ChannelPipeline) {
        pipeline.addLast("messageDecoder", CustomSocketByteStreamDecoder())
    }
}