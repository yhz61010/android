package com.leovp.demo.basiccomponents.examples.audio.sender.base

import com.leovp.basenetty.framework.base.decoder.CustomSocketByteStreamDecoder
import com.leovp.basenetty.framework.client.BaseNettyClient
import com.leovp.basenetty.framework.client.ClientConnectListener
import com.leovp.basenetty.framework.client.retrystrategy.base.RetryStrategy
import io.netty.channel.ChannelPipeline
import java.net.URI

/**
 * Author: Michael Leo
 * Date: 20-11-10 上午10:30
 */
class AudioSenderWebSocket(
    webSocketUri: URI,
    connectionListener: ClientConnectListener<BaseNettyClient>,
    trustAllServers: Boolean,
    retryStrategy: RetryStrategy
) :
    BaseNettyClient(webSocketUri, connectionListener, trustAllServers, retryStrategy) {
    override fun getTagName() = "AudioSenderWS"

    override fun addLastToPipeline(pipeline: ChannelPipeline) {
        pipeline.addLast("messageDecoder", CustomSocketByteStreamDecoder())
    }
}
