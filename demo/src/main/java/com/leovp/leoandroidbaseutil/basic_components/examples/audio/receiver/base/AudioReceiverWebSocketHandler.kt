package com.leovp.leoandroidbaseutil.basic_components.examples.audio.receiver.base

import com.leovp.androidbase.exts.asByteAndForceToBytes
import com.leovp.androidbase.exts.toBytesLE
import com.leovp.androidbase.utils.ByteUtil
import com.leovp.socket_sdk.framework.server.BaseNettyServer
import com.leovp.socket_sdk.framework.server.BaseServerChannelInboundHandler
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.WebSocketFrame

/**
 * Author: Michael Leo
 * Date: 20-11-10 上午10:15
 */
@ChannelHandler.Sharable
class AudioReceiverWebSocketHandler(private val netty: BaseNettyServer) : BaseServerChannelInboundHandler<Any>(netty) {
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

            netty.connectionListener.onReceivedData(netty, ctx.channel(), bodyBytes)
        }
    }

    fun sendAudioToClient(clientChannel: Channel, audioData: ByteArray): Boolean {
        return runCatching {
            val cmd = 1.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()

            val contentLen = (cmd.size + protoVer.size + audioData.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cmd, protoVer, audioData)
            netty.executeCommand(clientChannel, command, false)
        }.getOrDefault(false)
    }

    override fun release() {
    }
}