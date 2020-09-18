package com.ho1ho.leoandroidbaseutil.basic_components.examples.socket

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.ho1ho.socket_sdk.framework.server.BaseNettyServer
import com.ho1ho.socket_sdk.framework.server.BaseServerChannelInboundHandler
import com.ho1ho.socket_sdk.framework.server.ServerConnectListener
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import kotlinx.android.synthetic.main.activity_web_socket_server.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.Charset

@SuppressLint("SetTextI18n")
class WebSocketServerActivity : BaseDemonstrationActivity() {

    companion object {
        private const val TAG = "WebSocketServerActivity"
    }

    private val cs = CoroutineScope(Dispatchers.IO)

    private lateinit var webSocketServer: WebSocketServer
    private lateinit var webSocketServerHandler: WebSocketServerHandler

    private val connectionListener = object : ServerConnectListener<BaseNettyServer> {
        override fun onStarted(netty: BaseNettyServer) {
            LLog.i(TAG, "onStarted")
            ToastUtil.showDebugToast("onStarted")
            runOnUiThread { txtResponse.text = "Server started";sv.fullScroll(View.FOCUS_DOWN) }
        }

        override fun onStopped() {
            LLog.i(TAG, "onStop")
            ToastUtil.showDebugToast("onStop")
            runOnUiThread { txtResponse.text = "${txtResponse.text}\nServer stopped";sv.fullScroll(View.FOCUS_DOWN) }
        }

        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            LLog.i(TAG, "onClientConnected: ${clientChannel.remoteAddress()}")
            ToastUtil.showDebugToast("onClientConnected: ${clientChannel.remoteAddress()}")
            runOnUiThread {
                txtResponse.text = "${txtResponse.text}\nClient connected: ${clientChannel.remoteAddress()}"
                sv.fullScroll(View.FOCUS_DOWN)
            }
        }

        override fun onReceivedData(netty: BaseNettyServer, clientChannel: Channel, data: Any?) {
            LLog.i(TAG, "onReceivedData from ${clientChannel.remoteAddress()}: $data")
            runOnUiThread { txtResponse.text = "${txtResponse.text}\n${clientChannel.remoteAddress()}: $data";sv.fullScroll(View.FOCUS_DOWN) }
            webSocketServerHandler.responseClientMsg(clientChannel, "Server received: $data")
        }

        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LLog.w(TAG, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            ToastUtil.showDebugToast("onClientDisconnected: ${clientChannel.remoteAddress()}")
            runOnUiThread {
                txtResponse.text = "${txtResponse.text}\nClient disconnected: ${clientChannel.remoteAddress()}"
                sv.fullScroll(View.FOCUS_DOWN)
            }
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LLog.w(TAG, "onFailed code: $code message: $msg")
            ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            runOnUiThread { txtResponse.text = "${txtResponse.text}\nStart failed $code $msg";sv.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_socket_server)
    }

    fun onStartServerClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            repeat(1) {
                webSocketServer = WebSocketServer(10010, connectionListener)
                webSocketServerHandler = WebSocketServerHandler(webSocketServer)
                webSocketServer.initHandler(webSocketServerHandler)
                webSocketServer.startServer()
            }
        }
    }

    fun onStopServerClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            if (::webSocketServer.isInitialized) webSocketServer.stopServer()
        }
    }

    override fun onDestroy() {
        cs.launch {
            if (::webSocketServer.isInitialized) webSocketServer.stopServer()
        }
        super.onDestroy()
    }

    // =====================================================

    class WebSocketServer(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) : BaseNettyServer(port, connectionListener, true)

    @ChannelHandler.Sharable
    class WebSocketServerHandler(private val netty: BaseNettyServer) : BaseServerChannelInboundHandler<Any>(netty) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
            val receivedString: String?
            val frame = msg as WebSocketFrame
            receivedString = when (frame) {
                is TextWebSocketFrame -> frame.text()
                is PongWebSocketFrame -> frame.content().toString(Charset.forName("UTF-8"))
                else -> null
            }
            netty.connectionListener.onReceivedData(netty, ctx.channel(), receivedString)
        }

        fun responseClientMsg(clientChannel: Channel, msg: String): Boolean {
            return netty.executeCommand(clientChannel, msg)
        }

        override fun release() {
        }
    }
}