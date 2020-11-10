package com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender.base

import com.leovp.socket_sdk.framework.client.BaseNettyClient
import com.leovp.socket_sdk.framework.client.ClientConnectListener
import com.leovp.socket_sdk.framework.client.retry_strategy.base.RetryStrategy
import java.net.URI

/**
 * Author: Michael Leo
 * Date: 20-11-10 上午10:30
 */
class AudioSenderWebSocket(webSocketUri: URI, connectionListener: ClientConnectListener<BaseNettyClient>, retryStrategy: RetryStrategy) :
    BaseNettyClient(webSocketUri, connectionListener, retryStrategy) {
    override fun getTagName() = "AudioSenderWS"
}