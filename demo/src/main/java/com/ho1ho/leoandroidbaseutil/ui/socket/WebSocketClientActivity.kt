package com.ho1ho.leoandroidbaseutil.ui.socket

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.ho1ho.androidbase.exts.toJsonString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.socket_sdk.framework.BaseChannelInboundHandler
import com.ho1ho.socket_sdk.framework.BaseNetty
import com.ho1ho.socket_sdk.framework.BaseNettyClient
import com.ho1ho.socket_sdk.framework.inter.ClientConnectListener
import com.ho1ho.socket_sdk.framework.retry_strategy.ConstantRetry
import com.ho1ho.socket_sdk.framework.retry_strategy.base.RetryStrategy
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import kotlinx.android.synthetic.main.activity_socket_client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.nio.charset.Charset

class WebSocketClientActivity : BaseDemonstrationActivity() {
    private val cs = CoroutineScope(Dispatchers.IO)

    private lateinit var webSocketClient: WebSocketClient
    private lateinit var webSocketClientHandler: WebSocketClientHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_websocket_client)

        val connectionListener = object : ClientConnectListener {
            override fun onConnected(netty: BaseNetty) {
                LLog.i(TAG, "onConnected")
                ToastUtil.showDebugToast("onConnected")
            }

            @SuppressLint("SetTextI18n")
            override fun onReceivedData(netty: BaseNetty, data: Any?) {
                LLog.i(TAG, "onReceivedData: ${data?.toJsonString()}")
                runOnUiThread { txtView.text = txtView.text.toString() + data?.toJsonString() + "\n" }
            }

            override fun onDisconnected(netty: BaseNetty) {
                LLog.w(TAG, "onDisconnect")
                ToastUtil.showDebugToast("onDisconnect")
            }

            override fun onFailed(netty: BaseNetty, code: Int, msg: String?) {
                LLog.w(TAG, "onFailed code: $code message: $msg")
                ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            }
        }

        webSocketClient = WebSocketClient(URI("ws://10.10.9.64:10010/ws"), connectionListener, ConstantRetry(10, 2000))
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

    class WebSocketClient(webSocketUri: URI, connectionListener: ClientConnectListener, retryStrategy: RetryStrategy) :
        BaseNettyClient(webSocketUri, connectionListener, retryStrategy)

    @ChannelHandler.Sharable
    class WebSocketClientHandler(private val netty: BaseNettyClient) : BaseChannelInboundHandler<Any>(netty) {
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

        fun sendMsgToServer(msg: String): Boolean {
            return netty.executeCommand(msg)
        }

        override fun release() {
        }
    }

    companion object {
        const val TAG = "WebSocketClient"
    }
}