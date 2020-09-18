package com.ho1ho.leoandroidbaseutil.basic_components.examples.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.os.SystemClock
import com.ho1ho.androidbase.exts.toBytesLE
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.audio.base.AudioCodecInfo
import com.ho1ho.audio.recorder.MicRecorder
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
import kotlinx.coroutines.*
import java.net.URI
import java.nio.charset.Charset

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午6:00
 */
class AudioSender {
    companion object {
        private const val TAG = "AudioSender"
    }

    private lateinit var webSocketClient: WebSocketClient
    private lateinit var webSocketClientHandler: WebSocketClientHandler

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var micRecorder: MicRecorder
    private val audioEncoderCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_IN_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LLog.i(TAG, "onConnected")
            ToastUtil.showDebugToast("onConnected")
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?) {
//            LLog.i(TAG, "onReceivedData: ${data?.toJsonString()}")
            val rcvTs = (data as String).toLong()
            LLog.i(TAG, "Loopback time=${SystemClock.elapsedRealtime() - rcvTs} ms")
        }

        override fun onDisconnected(netty: BaseNettyClient) {
            LLog.w(TAG, "onDisconnect")
            ToastUtil.showDebugToast("onDisconnect")
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?) {
            LLog.w(TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")
        }
    }

    fun start(uri: URI) {
        webSocketClient = WebSocketClient(uri, connectionListener, ConstantRetry(10, 2000))
        webSocketClientHandler = WebSocketClientHandler(webSocketClient)
        webSocketClient.initHandler(webSocketClientHandler)
        webSocketClient.connect()

        micRecorder = MicRecorder(audioEncoderCodec, object : MicRecorder.RecordCallback {
            override fun onRecording(pcmData: ByteArray, st: Long, ed: Long) {
                ioScope.launch {
                    runCatching {
                        ensureActive()
//                    LLog.i(ITAG, "PCM[${pcmData.size}] to be sent.")
                        val tsArray = st.toBytesLE()
                        val finalArray = ByteArray(pcmData.size + tsArray.size)
                        System.arraycopy(tsArray, 0, finalArray, 0, tsArray.size)
                        System.arraycopy(pcmData, 0, finalArray, tsArray.size, pcmData.size)
                        webSocketClientHandler.sendAudioToServer(finalArray)
                    }.onFailure { it.printStackTrace() }
                }
            }

            override fun onStop(stopResult: Boolean) {
            }

        })
        micRecorder.startRecord()
    }

    fun stop() {
        if (::micRecorder.isInitialized) micRecorder.stopRecord()
        if (::webSocketClient.isInitialized) webSocketClient.release()
        ioScope.cancel()
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