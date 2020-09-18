package com.ho1ho.leoandroidbaseutil.basic_components.examples.audio

import android.content.Context
import android.media.AudioFormat
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.audio.base.AudioCodecInfo
import com.ho1ho.audio.player.PcmPlayer
import com.ho1ho.socket_sdk.framework.server.BaseNettyServer
import com.ho1ho.socket_sdk.framework.server.BaseServerChannelInboundHandler
import com.ho1ho.socket_sdk.framework.server.ServerConnectListener
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午6:00
 */
class AudioReceiver {
    companion object {
        private const val TAG = "AudioReceiver"
    }

    private lateinit var webSocketServer: WebSocketServer
    private lateinit var webSocketServerHandler: WebSocketServerHandler

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val audioPlayCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_OUT_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)
    private lateinit var pcmPlayer: PcmPlayer

    private val connectionListener = object : ServerConnectListener<BaseNettyServer> {
        override fun onStarted(netty: BaseNettyServer) {
            LLog.i(TAG, "onStarted")
            ToastUtil.showDebugToast("onStarted")
        }

        override fun onStopped() {
            LLog.i(TAG, "onStop")
            ToastUtil.showDebugToast("onStop")
        }

        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            LLog.i(TAG, "onClientConnected: ${clientChannel.remoteAddress()}")
            ToastUtil.showDebugToast("onClientConnected: ${clientChannel.remoteAddress()}")
        }

        override fun onReceivedData(netty: BaseNettyServer, clientChannel: Channel, data: Any?) {
            val array = data as Array<*>
            val ts = array[0] as Long
            val audioData = data[1] as ByteArray
            LLog.i(TAG, "onReceivedData from ${clientChannel.remoteAddress()} Length=${audioData.size} ts=$ts")
            ioScope.launch {
                pcmPlayer.play(audioData)
            }
            netty.executeCommand(clientChannel, "$ts")
        }

        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LLog.w(TAG, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            ToastUtil.showDebugToast("onClientDisconnected: ${clientChannel.remoteAddress()}")
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LLog.w(TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")
        }
    }

    class WebSocketServer(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) : BaseNettyServer(port, connectionListener, true)

    @ChannelHandler.Sharable
    class WebSocketServerHandler(private val netty: BaseNettyServer) : BaseServerChannelInboundHandler<Any>(netty) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
            val receivedByteBuf = (msg as WebSocketFrame).content().retain()
            val ts = receivedByteBuf.readLongLE()
            val dataByteArray = ByteArray(receivedByteBuf.readableBytes())
            receivedByteBuf.readBytes(dataByteArray)
            netty.connectionListener.onReceivedData(netty, ctx.channel(), arrayOf(ts, dataByteArray))
            receivedByteBuf.release()
        }

        override fun release() {
        }
    }

    fun startServer(ctx: Context) {
        pcmPlayer = PcmPlayer(ctx, audioPlayCodec)

        webSocketServer = WebSocketServer(10020, connectionListener)
        webSocketServerHandler = WebSocketServerHandler(webSocketServer)
        webSocketServer.initHandler(webSocketServerHandler)
        webSocketServer.startServer()
    }

    fun stopServer() {
        if (::pcmPlayer.isInitialized) pcmPlayer.release()
        ioScope.cancel()
        webSocketServer.stopServer()
    }
}