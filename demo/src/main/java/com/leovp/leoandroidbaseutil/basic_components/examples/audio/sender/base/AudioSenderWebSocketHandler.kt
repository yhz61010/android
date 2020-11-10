package com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender.base

import com.leovp.socket_sdk.framework.client.BaseClientChannelInboundHandler
import com.leovp.socket_sdk.framework.client.BaseNettyClient
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.WebSocketFrame

/**
 * Author: Michael Leo
 * Date: 20-11-10 上午10:31
 */
@ChannelHandler.Sharable
class AudioSenderWebSocketHandler(private val netty: BaseNettyClient) : BaseClientChannelInboundHandler<Any>(netty) {
    override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
        runCatching {
            val receivedByteBuf = (msg as WebSocketFrame).content().retain()
            val dataByteArray = ByteArray(receivedByteBuf.readableBytes())
            receivedByteBuf.readBytes(dataByteArray)
            receivedByteBuf.release()
            netty.connectionListener.onReceivedData(netty, dataByteArray)
        }.onFailure { it.printStackTrace() }
    }

    fun sendAudioToServer(audioData: ByteArray): Boolean {
        return runCatching {
            netty.executeCommand("AudioPCM", "SendAudio", audioData, false)
        }.getOrDefault(false)
    }

    override fun release() {
    }
}