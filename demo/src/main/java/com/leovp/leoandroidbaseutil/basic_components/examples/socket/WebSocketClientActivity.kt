package com.leovp.leoandroidbaseutil.basic_components.examples.socket

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
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
import kotlinx.android.synthetic.main.activity_socket_client.editText
import kotlinx.android.synthetic.main.activity_socket_client.txtView
import kotlinx.android.synthetic.main.activity_websocket_client.*
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

class WebSocketClientActivity : BaseDemonstrationActivity() {
    companion object {
        private const val TAG = "WebSocketClient"
    }

    private val cs = CoroutineScope(Dispatchers.IO)

    private lateinit var webSocketClient: WebSocketClientDemo
    private lateinit var webSocketClientHandler: WebSocketClientHandlerDemo
    private val constantRetry = ConstantRetry(10, 2000)

    private val retryTimes = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_websocket_client)
        webSocketClient = createSocket()
    }

    private fun createSocket(): WebSocketClientDemo {
        val webSocketClient = WebSocketClientDemo(
//            URI("wss://www.qvdv.com:443/Websocket"),
//            URI("ws://123.207.136.134:9010/ajaxchattest"),
            URI(etSvrIp.text.toString()),
            connectionListener,
            constantRetry
        )
        webSocketClientHandler = WebSocketClientHandlerDemo(webSocketClient)
        webSocketClient.initHandler(webSocketClientHandler)
        return webSocketClient
    }

    fun onConnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        LogContext.log.i(TAG, "onConnectClick at ${SystemClock.elapsedRealtime()}")

        cs.launch {
            repeat(1) {
                if (::webSocketClient.isInitialized) {
                    LogContext.log.i(TAG, "do connect at ${SystemClock.elapsedRealtime()}")
                    webSocketClient.connect()
                }

                // You can also create multiple sockets at the same time like this(It's thread safe so you can create them freely):
                // val socketClient = SocketClient("50d.win", 8080, connectionListener)
                // val socketClientHandler = SocketClientHandler(socketClient)
                // socketClient.initHandler(socketClientHandler)
                // socketClient.connect()
            }
        }
    }

    override fun onDestroy() {
        cs.launch {
            if (::webSocketClient.isInitialized) webSocketClient.release()
        }
        super.onDestroy()
    }

// =====================================================

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LogContext.log.i(TAG, "onConnected")
            ToastUtil.showDebugToast("onConnected")

            // Reset retry counter
            retryTimes.set(0)
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
            LogContext.log.i(TAG, "onReceivedData: ${data?.toJsonString()}")
            runOnUiThread { txtView.text = txtView.text.toString() + data?.toJsonString() + "\n" }
        }

        override fun onDisconnected(netty: BaseNettyClient) {
            LogContext.log.w(TAG, "onDisconnect")
            ToastUtil.showDebugToast("onDisconnect")
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")

            if (code == ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION
                || code == ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION
                || code == ClientConnectListener.CONNECTION_ERROR_SOCKET_EXCEPTION
                || code == ClientConnectListener.CONNECTION_ERROR_NETWORK_LOST
            ) {
                retryTimes.incrementAndGet()
                if (retryTimes.get() > constantRetry.getMaxTimes()) {
                    LogContext.log.e(TAG, "===== Connect failed - Exceed max retry times. =====")
                    ToastUtil.showDebugToast("Exceed max retry times.")

                    // Reset retry counter
                    retryTimes.set(0)
                } else {
                    LogContext.log.w(TAG, "Reconnect(${retryTimes.get()}) in ${constantRetry.getDelayInMillSec(retryTimes.get())}ms")
                    cs.launch {
                        runCatching {
                            delay(constantRetry.getDelayInMillSec(retryTimes.get()))
                            ensureActive()
                            webSocketClient.release()
                            webSocketClient = createSocket()
                            webSocketClient.connect()
                        }.onFailure { LogContext.log.e(TAG, "Do retry failed.", it) }
                    } // launch
                } // else
            } // if for exception
        }
    }

    class WebSocketClientDemo(webSocketUri: URI, connectionListener: ClientConnectListener<BaseNettyClient>, retryStrategy: RetryStrategy, certInputStream: InputStream? = null) :
        BaseNettyClient(webSocketUri, connectionListener, retryStrategy, certInputStream) {
        override fun getTagName() = "WebSocketClient"

        override fun retryProcess(): Boolean {
            return true
        }
    }

    @ChannelHandler.Sharable
    class WebSocketClientHandlerDemo(private val netty: BaseNettyClient) : BaseClientChannelInboundHandler<Any>(netty) {
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
            return netty.executeCommand("WebSocketCmd", "Send msg to server", msg)
        }

        override fun release() {
        }
    }

// =================================================

    fun sendMsg(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            if (::webSocketClientHandler.isInitialized) {
                val result = webSocketClientHandler.sendMsgToServer(editText.text.toString())
                withContext(Dispatchers.Main) { editText.text.clear();if (!result) ToastUtil.showDebugErrorToast("Send command error") }
            }
        }
    }

    fun onDisconnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            if (::webSocketClient.isInitialized) webSocketClient.disconnectManually()
        }
    }

    fun onConnectRelease(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            if (::webSocketClient.isInitialized) webSocketClient.release()
        }
    }
}