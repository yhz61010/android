package com.leovp.leoandroidbaseutil.basic_components.examples.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.os.SystemClock
import com.leovp.androidbase.exts.toBytesLE
import com.leovp.androidbase.utils.LLog
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.audio.base.AudioCodecInfo
import com.leovp.audio.player.PcmPlayer
import com.leovp.audio.recorder.MicRecorder
import com.leovp.socket_sdk.framework.client.BaseClientChannelInboundHandler
import com.leovp.socket_sdk.framework.client.BaseNettyClient
import com.leovp.socket_sdk.framework.client.ClientConnectListener
import com.leovp.socket_sdk.framework.client.retry_strategy.ConstantRetry
import com.leovp.socket_sdk.framework.client.retry_strategy.base.RetryStrategy
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

    private var ctx: Context? = null

    private lateinit var webSocketClient: WebSocketClient
    private lateinit var webSocketClientHandler: WebSocketClientHandler

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var micRecorder: MicRecorder? = null
    private val audioEncoderCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_IN_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)

    private val audioPlayCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_OUT_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)
    private var pcmPlayer: PcmPlayer? = null

    @Volatile
    private var startPlaying = false

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LLog.i(TAG, "onConnected")
            ToastUtil.showDebugToast("onConnected")
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?) {
//            LLog.i(TAG, "onReceivedData: ${data?.toJsonString()}")
            when (data) {
                is String -> {
                    LLog.i(TAG, "Loopback time=${SystemClock.elapsedRealtime() - data.toLong()} ms")
                }
                is ByteArray -> {
                    if (!startPlaying) {
                        startPlaying = true
                        pcmPlayer = PcmPlayer(ctx!!, audioPlayCodec)
                    }
                    LLog.i(TAG, "onReceivedData PCM[${data.size}]")
                    ioScope.launch {
                        runCatching {
                            ensureActive()
                            pcmPlayer?.play(data)
                        }.onFailure { it.printStackTrace() }
                    }
                }
            }
        }

        override fun onDisconnected(netty: BaseNettyClient) {
            LLog.w(TAG, "onDisconnect")
            ToastUtil.showDebugToast("onDisconnect")
            stopRecordingAndPlaying()
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?) {
            LLog.w(TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            stopRecordingAndPlaying()
        }
    }

    private fun stopRecordingAndPlaying() {
        startPlaying = false
        pcmPlayer?.release()

        micRecorder?.stopRecord()
    }

    fun start(ctx: Context, uri: URI) {
        this.ctx = ctx
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
        micRecorder!!.startRecord()
    }

    fun stop() {
        stopRecordingAndPlaying()
        if (::webSocketClient.isInitialized) {
            webSocketClient.disconnectManually()
            webSocketClient.release()
        }
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

            if (receivedString != null) {
                netty.connectionListener.onReceivedData(netty, receivedString)
            } else {
                val receivedByteBuf = msg.content().retain()
                val dataByteArray = ByteArray(receivedByteBuf.readableBytes())
                receivedByteBuf.readBytes(dataByteArray)
                netty.connectionListener.onReceivedData(netty, dataByteArray)
                receivedByteBuf.release()
            }
        }

        fun sendAudioToServer(audioData: ByteArray): Boolean {
            return netty.executeCommand("AudioPCM", "SendAudio", audioData, false)
        }

        override fun release() {
        }
    }
}