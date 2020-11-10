package com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender.base

import com.leovp.socket_sdk.framework.client.BaseClientChannelInboundHandler
import com.leovp.socket_sdk.framework.client.BaseNettyClient
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import java.nio.charset.Charset

/**
 * Author: Michael Leo
 * Date: 20-11-10 上午10:31
 */
@ChannelHandler.Sharable
class AudioSenderWebSocketHandler(private val netty: BaseNettyClient) : BaseClientChannelInboundHandler<Any>(netty) {
    override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
        val receivedString: String?
        val frame = msg as WebSocketFrame
        receivedString = when (frame) {
            is TextWebSocketFrame -> {
                frame.text()
            }
            is PongWebSocketFrame -> {
                frame.content().toString(Charset.forName("UTF-8"))
            }
            else -> {
                null
            }
        }

        if (receivedString != null) {
            netty.connectionListener.onReceivedData(netty, receivedString)
        } else {
            runCatching {
                val receivedByteBuf = msg.content().retain()
                val dataByteArray = ByteArray(receivedByteBuf.readableBytes())
                receivedByteBuf.readBytes(dataByteArray)
                receivedByteBuf.release()
                netty.connectionListener.onReceivedData(netty, dataByteArray)
            }.onFailure { it.printStackTrace() }
        }
    }

    fun sendAudioToServer(audioData: ByteArray): Boolean {
        return runCatching {
            netty.executeCommand("AudioPCM", "SendAudio", audioData, false)
        }.getOrDefault(false)
    }

    override fun release() {
    }
}