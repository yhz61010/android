package com.ho1ho.leoandroidbaseutil.ui.socket

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.ho1ho.androidbase.exts.toJsonString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.socket_sdk.framework.base.BaseChannelInboundHandler
import com.ho1ho.socket_sdk.framework.base.BaseNettyClient
import com.ho1ho.socket_sdk.framework.base.inter.NettyConnectionListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import kotlinx.android.synthetic.main.activity_socket_client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.nio.charset.Charset

class WebSocketActivity : BaseDemonstrationActivity() {
    private val cs = CoroutineScope(Dispatchers.IO)

    private lateinit var webSocketClient: WebSocketClient
    private lateinit var webSocketClientHandler: WebSocketClientHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_websocket_client)

        val connectionListener = object : NettyConnectionListener {
            override fun onConnecting(client: BaseNettyClient) {
                LLog.i(TAG, "onConnecting")
                ToastUtil.showDebugToast("onConnecting")
            }

            override fun onConnected(client: BaseNettyClient) {
                LLog.i(TAG, "onConnected")
                ToastUtil.showDebugToast("onConnected")
            }

            @SuppressLint("SetTextI18n")
            override fun onReceivedData(client: BaseNettyClient, data: Any?) {
                LLog.i(TAG, "onReceivedData: ${data?.toJsonString()}")
                runOnUiThread { txtView.text = txtView.text.toString() + data?.toJsonString() + "\n" }
            }

            override fun onDisconnected(client: BaseNettyClient) {
                LLog.i(TAG, "onDisconnect")
                ToastUtil.showDebugToast("onDisconnect")
            }

            override fun onFailed(client: BaseNettyClient, code: Int, msg: String?) {
                LLog.i(TAG, "${Thread.currentThread().id} onFailed code: $code message: $msg")
                ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            }

            override fun onException(client: BaseNettyClient, cause: Throwable) {
                LLog.i(TAG, "onCaughtException")
                ToastUtil.showDebugToast("onCaughtException")
            }

        }

        webSocketClient = WebSocketClient(
            URI("ws://61010.ml:9090/ws"),
            connectionListener
        )
        webSocketClientHandler = WebSocketClientHandler(webSocketClient)
        webSocketClient.initHandler(webSocketClientHandler)
    }

    override fun onDestroy() {
        cs.launch {
            webSocketClient.release()
        }
        super.onDestroy()
    }

    fun onConnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            repeat(1) {
                webSocketClient.connect()

                // You can also create multiple sockets at the same time like this(It's thread safe so you can create them freely):
                // val socketClient = SocketClient("50d.win", 8080, connectionListener)
                // val socketClientHandler = SocketClientHandler(socketClient)
                // socketClient.initHandler(socketClientHandler)
                // socketClient.connect()
            }
        }
    }

    fun sendMsg(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            val result = webSocketClientHandler.sendMsgToServer(editText.text.toString())
            withContext(Dispatchers.Main) { editText.text.clear();if (!result) ToastUtil.showDebugErrorToast("Send command error") }
        }
    }

    fun onDisconnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            webSocketClient.disconnectManually()
        }
    }

    fun onConnectRelease(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            webSocketClient.release()
        }
    }

    // =====================================================

    class WebSocketClient(webSocketUri: URI, connectionListener: NettyConnectionListener) :
        BaseNettyClient(webSocketUri, connectionListener) {
        override fun addLastToPipeline(pipeline: ChannelPipeline) {
            with(pipeline) {
                addLast(DelimiterBasedFrameDecoder(65535, *Delimiters.lineDelimiter()))
                addLast(StringDecoder())
                addLast(StringEncoder())
            }
        }
    }

    @ChannelHandler.Sharable
    class WebSocketClientHandler(private val client: BaseNettyClient) : BaseChannelInboundHandler<Any>(client) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
            val receivedString: String?
            val frame = msg as WebSocketFrame
            if (frame is TextWebSocketFrame) {
                receivedString = frame.text()
            } else if (frame is PongWebSocketFrame) {
                receivedString = frame.content().toString(Charset.forName("UTF-8"))
            } else {
                receivedString = null
            }
            client.connectionListener.onReceivedData(client, receivedString)
        }

        fun sendMsgToServer(msg: String): Boolean {
            return client.executeCommand(msg)
        }

        override fun release() {
        }
    }

    companion object {
        const val TAG = "WebSocketClient"
    }
}