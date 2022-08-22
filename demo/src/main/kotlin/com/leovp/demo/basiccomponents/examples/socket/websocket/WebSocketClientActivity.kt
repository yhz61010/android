package com.leovp.demo.basiccomponents.examples.socket.websocket

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import com.leovp.android.exts.toast
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityWebsocketClientBinding
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.basenetty.framework.client.BaseClientChannelInboundHandler
import com.leovp.basenetty.framework.client.BaseNettyClient
import com.leovp.basenetty.framework.client.ClientConnectListener
import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import com.leovp.basenetty.framework.client.retrystrategy.base.RetryStrategy
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import kotlinx.coroutines.*
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

class WebSocketClientActivity : BaseDemonstrationActivity<ActivityWebsocketClientBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityWebsocketClientBinding {
        return ActivityWebsocketClientBinding.inflate(layoutInflater)
    }

    private val cs = CoroutineScope(Dispatchers.IO)

    private var webSocketClient: WebSocketClientDemo? = null
    private lateinit var webSocketClientHandler: WebSocketClientHandlerDemo
    private val constantRetry = ConstantRetry(10, 2000)

    private val retryTimes = AtomicInteger(0)

    private fun createSocket(): WebSocketClientDemo {
        val webSocketClient = WebSocketClientDemo(
            URI(binding.etSvrIp.text.toString()),
            connectionListener,
            false, // assets.open("cert/websocket.org.crt")
            constantRetry,
        )
        webSocketClientHandler = WebSocketClientHandlerDemo(webSocketClient)
        webSocketClient.initHandler(webSocketClientHandler)
        return webSocketClient
    }

    fun onConnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        LogContext.log.i(tag, "onConnectClick at ${SystemClock.elapsedRealtime()}")

        // For none-ssl websocket or trust all certificates websocket, you can create just one socket object,
        // then disconnect it and connect it again for many times as you wish.
        // However, for self-signed certificate, once you disconnect the socket,
        // you must recreate the socket object again then connect it or else you can **NOT** connect it any more.
        // Example:
        // For none-ssl websocket or trust all certificates:
        // create socket ──> connect() ──> disconnectManually()
        //                      ↑                   ↓
        //                      └───────────────────┘
        // Note that, in this case, your socket handler must be @Sharable
        //
        // For self-signed certificate:
        // create socket ──> connect() ──> (optional)disconnectManually()  ──> release()
        //        ↑                                                               ↓
        //        └───────────────────────────────────────────────────────────────┘
        cs.launch {
            for (i in 1..1) {
                ensureActive()
                webSocketClient = createSocket()
                LogContext.log.i(tag, "[$i] do connect at ${SystemClock.elapsedRealtime()}")
                webSocketClient?.connect()
                //                webSocketClient?.disconnectManually()
                //                webSocketClient?.release()
                //                LogContext.log.i(tag, "= released ================================================================================")

                // You can also create multiple sockets at the same time like this(It's thread safe so you can create them freely):
                // val socketClient = SocketClient("50d.win", 8080, connectionListener)
                // val socketClientHandler = SocketClientHandler(socketClient)
                // socketClient.initHandler(socketClientHandler)
                // socketClient.connect()
            }
        }
    }

    fun onDisconnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch { webSocketClient?.disconnectManually() }
    }

    fun onConnectRelease(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch { webSocketClient?.release() }
    }

    override fun onDestroy() {
        cs.launch { webSocketClient?.release() }
        cs.cancel()
        super.onDestroy()
    }

    // =====================================================

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LogContext.log.w(tag, "onConnected")
            LogContext.log.i(tag, "- connected -------------------------------------------------")
            toast("onConnected", debug = true)

            // Reset retry counter
            retryTimes.set(0)
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
            LogContext.log.i(tag, "onReceivedData: ${data?.toJsonString()}")
            runOnUiThread { binding.txtView.text = binding.txtView.text.toString() + data?.toJsonString() + "\n" }
        }

        override fun onDisconnected(netty: BaseNettyClient, byRemote: Boolean) {
            LogContext.log.w(tag, "onDisconnected byRemote=$byRemote")
            LogContext.log.i(tag, "~ disconnectManually done ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
            toast("onDisconnected byRemote=$byRemote", debug = true)
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
            LogContext.log.w(tag, "onFailed code: $code e=$e message: $msg")
            toast("onFailed code: $code message: $msg", debug = true)

            if (code == ClientConnectListener.CONNECTION_ERROR_CONNECT_EXCEPTION ||
                code == ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION ||
                code == ClientConnectListener.CONNECTION_ERROR_SOCKET_EXCEPTION ||
                code == ClientConnectListener.CONNECTION_ERROR_NETWORK_LOST
            ) {
                if (retryTimes.incrementAndGet() > constantRetry.getMaxTimes()) {
                    LogContext.log.e(tag, "===== Connect failed - Exceed max retry times. =====")
                    toast("Exceed max retry times.", debug = true)

                    // Reset retry counter
                    retryTimes.set(0)
                } else {
                    LogContext.log.w(tag, "Reconnect(${retryTimes.get()}) in ${constantRetry.getDelayInMillSec(retryTimes.get())}ms")
                    cs.launch {
                        runCatching {
                            delay(constantRetry.getDelayInMillSec(retryTimes.get()))
                            ensureActive()
                            netty.release()
                            LogContext.log.w(tag, "= Start Reconnecting ===============================================================")
                            webSocketClient = createSocket().apply { connect() }
                        }.onFailure { LogContext.log.e(tag, "Do retry failed.", it) }
                    } // launch
                } // else
            } // if for exception
        }
    }

    class WebSocketClientDemo(
        webSocketUri: URI,
        connectionListener: ClientConnectListener<BaseNettyClient>,
        trustAllServers: Boolean,
        //        certInputStream: InputStream? = null,
        retryStrategy: RetryStrategy
    ) :
        BaseNettyClient(webSocketUri, connectionListener, trustAllServers, /* trustAllServers,*/ retryStrategy) {
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
            return netty.executeCommand(msg, "Send msg to server", "WebSocketCmd")
        }

        override fun release() {
        }
    }

    // =================================================

    fun sendMsg(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            if (::webSocketClientHandler.isInitialized) {
                val result = webSocketClientHandler.sendMsgToServer(binding.editText.text.toString())
                withContext(Dispatchers.Main) {
                    binding.editText.text.clear(); if (!result) toast(
                        "Send command error",
                        debug = true,
                        error = true
                    )
                }
            }
        }
    }
}
