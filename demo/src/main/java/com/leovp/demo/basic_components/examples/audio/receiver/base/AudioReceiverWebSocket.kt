package com.leovp.demo.basic_components.examples.audio.receiver.base

import com.leovp.socket_sdk.framework.base.decoder.CustomSocketByteStreamDecoder
import com.leovp.socket_sdk.framework.server.BaseNettyServer
import com.leovp.socket_sdk.framework.server.ServerConnectListener
import io.netty.channel.ChannelPipeline

/**
 * Author: Michael Leo
 * Date: 20-11-10 上午10:14
 */
class AudioReceiverWebSocket(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) :
    BaseNettyServer(port, connectionListener, true) {
    override fun getTagName() = "ARWS"

    override fun addLastToPipeline(pipeline: ChannelPipeline) {
        pipeline.addLast("messageDecoder", CustomSocketByteStreamDecoder())
    }
}
