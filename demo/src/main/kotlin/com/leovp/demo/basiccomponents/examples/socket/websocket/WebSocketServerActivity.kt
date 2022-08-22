package com.leovp.demo.basiccomponents.examples.socket.websocket

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityWebsocketServerBinding
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.android.exts.toast
import com.leovp.android.utils.NetworkUtil
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.basenetty.framework.server.BaseNettyServer
import com.leovp.basenetty.framework.server.BaseServerChannelInboundHandler
import com.leovp.basenetty.framework.server.ServerConnectListener
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("SetTextI18n")
class WebSocketServerActivity : BaseDemonstrationActivity<ActivityWebsocketServerBinding>() {

    override fun getTagName(): String = ITAG

    companion object {
        private const val PORT = 10010
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityWebsocketServerBinding {
        return ActivityWebsocketServerBinding.inflate(layoutInflater)
    }

    private val cs = CoroutineScope(Dispatchers.IO)

    private lateinit var webSocketServer: WebSocketServer
    private lateinit var webSocketServerHandler: WebSocketServerHandler

    private val connectionListener = object : ServerConnectListener<BaseNettyServer> {
        override fun onStarted(netty: BaseNettyServer) {
            LogContext.log.i(tag, "onStarted on port: $PORT")
            toast("onStarted on port: $PORT", debug = true)
            runOnUiThread { binding.txtResponse.text = "Server started on port: $PORT"; binding.sv.fullScroll(View.FOCUS_DOWN) }
        }

        override fun onStopped() {
            LogContext.log.i(tag, "onStop")
            toast("onStop", debug = true)
            runOnUiThread { binding.txtResponse.text = "${binding.txtResponse.text}\nServer stopped"; binding.sv.fullScroll(View.FOCUS_DOWN) }
        }

        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.i(tag, "onClientConnected: ${clientChannel.remoteAddress()}")
            toast("onClientConnected: ${clientChannel.remoteAddress()}", debug = true)
            runOnUiThread {
                binding.txtResponse.text = "${binding.txtResponse.text}\nClient connected: ${clientChannel.remoteAddress()}"
                binding.sv.fullScroll(View.FOCUS_DOWN)
            }
        }

        override fun onReceivedData(netty: BaseNettyServer, clientChannel: Channel, data: Any?, action: Int) {
            LogContext.log.i(tag, "onReceivedData from ${clientChannel.remoteAddress()}: $data")
            runOnUiThread {
                binding.txtResponse.text =
                    "${binding.txtResponse.text}\n${clientChannel.remoteAddress()}: $data"; binding.sv.fullScroll(View.FOCUS_DOWN)
            }
            webSocketServerHandler.responseClientMsg(clientChannel, "Server received: $data")
        }

        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.w(tag, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            toast("onClientDisconnected: ${clientChannel.remoteAddress()}", debug = true)
            runOnUiThread {
                binding.txtResponse.text = "${binding.txtResponse.text}\nClient disconnected: ${clientChannel.remoteAddress()}"
                binding.sv.fullScroll(View.FOCUS_DOWN)
            }
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LogContext.log.w(tag, "onFailed code: $code message: $msg")
            toast("onFailed code: $code message: $msg", debug = true)
            runOnUiThread {
                binding.txtResponse.text =
                    "${binding.txtResponse.text}\nStart failed $code $msg"; binding.sv.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.tvServerIp.text = NetworkUtil.getIp()[0]

        binding.btnStop.setOnSingleClickListener {
            LogContext.log.d(tag, "Stop button clicked.")
            cs.launch {
                if (::webSocketServer.isInitialized) webSocketServer.stopServer()
            }
        }
    }

    fun onStartServerClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            repeat(1) {
                webSocketServer = WebSocketServer(PORT, connectionListener)
                webSocketServerHandler = WebSocketServerHandler(webSocketServer)
                webSocketServer.initHandler(webSocketServerHandler)
                webSocketServer.startServer()
            }
        }
    }

    override fun onDestroy() {
        cs.launch {
            if (::webSocketServer.isInitialized) webSocketServer.stopServer()
        }
        super.onDestroy()
    }

    // =====================================================

    class WebSocketServer(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) : BaseNettyServer(port, connectionListener, true) {
        override fun getTagName() = "WSSA-WS"
    }

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
            return netty.executeCommand(clientChannel, msg, "responseClientMsg")
        }

        override fun release() {
        }
    }
}
