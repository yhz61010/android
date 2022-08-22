package com.leovp.demo.basiccomponents.examples.audio.receiver.base

import com.leovp.basenetty.framework.base.decoder.CustomSocketByteStreamDecoder
import com.leovp.basenetty.framework.server.BaseNettyServer
import com.leovp.basenetty.framework.server.ServerConnectListener
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
