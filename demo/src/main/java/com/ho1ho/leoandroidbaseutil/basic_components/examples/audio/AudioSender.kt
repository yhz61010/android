package com.ho1ho.leoandroidbaseutil.basic_components.examples.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.toJsonString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.audio.base.AudioCodecInfo
import com.ho1ho.audio.recorder.MicRecorder
import com.ho1ho.leoandroidbaseutil.basic_components.examples.socket.WebSocketClientActivity
import com.ho1ho.socket_sdk.framework.client.BaseClientChannelInboundHandler
import com.ho1ho.socket_sdk.framework.client.BaseNettyClient
import com.ho1ho.socket_sdk.framework.client.ClientConnectListener
import com.ho1ho.socket_sdk.framework.client.retry_strategy.ConstantRetry
import com.ho1ho.socket_sdk.framework.client.retry_strategy.base.RetryStrategy
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.nio.charset.Charset

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午6:00
 */
class AudioSender {
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var webSocketClientHandler: WebSocketClientHandler

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var micRecorder: MicRecorder
    private val audioEncoderCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_IN_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LLog.i(WebSocketClientActivity.TAG, "onConnected")
            ToastUtil.showDebugToast("onConnected")
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?) {
            LLog.i(WebSocketClientActivity.TAG, "onReceivedData: ${data?.toJsonString()}")
        }

        override fun onDisconnected(netty: BaseNettyClient) {
            LLog.w(WebSocketClientActivity.TAG, "onDisconnect")
            ToastUtil.showDebugToast("onDisconnect")
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?) {
            LLog.w(WebSocketClientActivity.TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")
        }
    }

    fun start(uri: URI) {
        webSocketClient = WebSocketClient(uri, connectionListener, ConstantRetry(10, 2000))
        webSocketClientHandler = WebSocketClientHandler(webSocketClient)
        webSocketClient.initHandler(webSocketClientHandler)

        webSocketClient.connect()

        micRecorder = MicRecorder(audioEncoderCodec, object : MicRecorder.RecordCallback {
            override fun onRecording(pcmData: ByteArray) {
                ioScope.launch {
                    LLog.i(ITAG, "PCM[${pcmData.size}] to be sent.")
                    webSocketClientHandler.sendAudioToServer(pcmData)
                }
            }

            override fun onStop(stopResult: Boolean) {
            }

        })
        micRecorder.startRecord()
    }

    class WebSocketClient(webSocketUri: URI, connectionListener: ClientConnectListener<BaseNettyClient>, retryStrategy: RetryStrategy) :
        BaseNettyClient(webSocketUri, connectionListener, retryStrategy) {
        override fun getTagName() = "WebSocketClient"
    }

    @ChannelHandler.Sharable
    class WebSocketClientHandler(private val netty: BaseNettyClient) : BaseClientChannelInboundHandler<Any>(netty) {
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
            netty.connectionListener.onReceivedData(netty, receivedString)
        }

        fun sendAudioToServer(audioData: ByteArray): Boolean {
            return netty.executeCommand("AudioPCM", "SendAudio", audioData, false)
        }

        override fun release() {
        }
    }
}