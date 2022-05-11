package com.leovp.demo.basic_components.examples.audio.sender.base

import com.leovp.androidbase.utils.ByteUtil
import com.leovp.lib_bytes.asByteAndForceToBytes
import com.leovp.lib_bytes.toBytesLE
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
        val receivedByteBuf = (msg as WebSocketFrame).content().retain()
        // Data Length
        receivedByteBuf.readIntLE()
        // Command ID
        receivedByteBuf.readByte()
        // Protocol version
        receivedByteBuf.readByte()

        runCatching {
            val bodyBytes = ByteArray(receivedByteBuf.readableBytes())
            receivedByteBuf.getBytes(6, bodyBytes)
            receivedByteBuf.release()

            netty.connectionListener.onReceivedData(netty, bodyBytes)
        }
    }

    fun sendAudioToServer(audioData: ByteArray): Boolean {
        return runCatching {
            val cmd = 1.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()

            val contentLen = (cmd.size + protoVer.size + audioData.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cmd, protoVer, audioData)
            netty.executeCommand(command, "AudioPCM", "1", false)
        }.getOrDefault(false)
    }

    override fun release() {
    }
}