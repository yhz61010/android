package com.leovp.demo.basiccomponents.examples.socket.websocket

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.android.exts.toast
import com.leovp.android.utils.NetworkUtil
import com.leovp.basenetty.framework.server.BaseNettyServer
import com.leovp.basenetty.framework.server.BaseServerChannelInboundHandler
import com.leovp.basenetty.framework.server.ServerConnectListener
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityWebsocketServerBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
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
class WebSocketServerActivity :
    BaseDemonstrationActivity<ActivityWebsocketServerBinding>(R.layout.activity_websocket_server) {

    override fun getTagName(): String = ITAG

    companion object {
        private const val PORT = 10010
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityWebsocketServerBinding =
        ActivityWebsocketServerBinding.inflate(layoutInflater)

    private val cs = CoroutineScope(Dispatchers.IO)

    /**
     * Holds the single active server instance, or `null` when no server is running.
     *
     * A [BaseNettyServer] instance is single-use: once [BaseNettyServer.stopServer] is
     * called it cannot be restarted, so each start must create a fresh instance. This
     * reference is the guard that prevents starting a second instance while one is still
     * bound to the port — overwriting it would orphan a running server that keeps holding
     * the port, which is exactly what caused the "Address already in use" failures.
     */
    @Volatile
    private var webSocketServer: WebSocketServer? = null

    @Volatile
    private var webSocketServerHandler: WebSocketServerHandler? = null

    private val connectionListener = object : ServerConnectListener<BaseNettyServer> {
        override fun onStarted(netty: BaseNettyServer) {
            LogContext.log.i(tag, "onStarted on port: $PORT")
            toast("onStarted on port: $PORT", debug = true)
            runOnUiThread {
                binding.btnStop.isEnabled = true
                binding.txtResponse.text = "Server started on port: $PORT"
                binding.sv.fullScroll(View.FOCUS_DOWN)
            }
        }

        override fun onStopped() {
            LogContext.log.i(tag, "onStop")
            toast("onStop", debug = true)
            // Release the reference so the next start creates a fresh instance.
            webSocketServer = null
            webSocketServerHandler = null
            runOnUiThread {
                binding.btnStartServer.isEnabled = true
                binding.btnStop.isEnabled = false
                binding.txtResponse.text = "${binding.txtResponse.text}\nServer stopped"
                binding.sv.fullScroll(View.FOCUS_DOWN)
            }
        }

        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.i(tag, "onClientConnected: ${clientChannel.remoteAddress()}")
            toast("onClientConnected: ${clientChannel.remoteAddress()}", debug = true)
            runOnUiThread {
                binding.txtResponse.text =
                    "${binding.txtResponse.text}\nClient connected: " +
                    "${clientChannel.remoteAddress()}"
                binding.sv.fullScroll(View.FOCUS_DOWN)
            }
        }

        override fun onReceivedData(
            netty: BaseNettyServer,
            clientChannel: Channel,
            data: Any?,
            action: Int
        ) {
            LogContext.log.i(tag, "onReceivedData from ${clientChannel.remoteAddress()}: $data")
            runOnUiThread {
                binding.txtResponse.text =
                    "${binding.txtResponse.text}\n${clientChannel.remoteAddress()}: $data"
                binding.sv.fullScroll(View.FOCUS_DOWN)
            }
            webSocketServerHandler?.responseClientMsg(clientChannel, "Server received: $data")
        }

        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.w(tag, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            toast("onClientDisconnected: ${clientChannel.remoteAddress()}", debug = true)
            runOnUiThread {
                binding.txtResponse.text =
                    "${binding.txtResponse.text}\nClient disconnected: " +
                    "${clientChannel.remoteAddress()}"
                binding.sv.fullScroll(View.FOCUS_DOWN)
            }
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LogContext.log.w(tag, "onFailed code: $code message: $msg")
            toast("onFailed code: $code message: $msg", debug = true)
            // Release the reference so the user can retry once the port is free again.
            webSocketServer = null
            webSocketServerHandler = null
            runOnUiThread {
                binding.btnStartServer.isEnabled = true
                binding.btnStop.isEnabled = false
                binding.txtResponse.text =
                    "${binding.txtResponse.text}\nStart failed $code $msg"
                binding.sv.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.tvServerIp.text = NetworkUtil.getIp()[0]
        binding.btnStop.isEnabled = false

        binding.btnStop.setOnSingleClickListener {
            LogContext.log.d(tag, "Stop button clicked.")
            val server = webSocketServer
            if (server == null) {
                toast("Server is not running", debug = true)
                return@setOnSingleClickListener
            }
            binding.btnStop.isEnabled = false
            cs.launch { server.stopServer() }
        }
    }

    fun onStartServerClick(@Suppress("UNUSED_PARAMETER") view: View) {
        // Guard against starting a second server while one is already running. Each
        // BaseNettyServer is single-use, so overwriting the reference here would orphan
        // the running instance that still holds the port -> "Address already in use".
        if (webSocketServer != null) {
            toast("Server is already running on port: $PORT", debug = true)
            return
        }
        val server = WebSocketServer(PORT, connectionListener)
        val handler = WebSocketServerHandler(server)
        server.initHandler(handler)
        webSocketServer = server
        webSocketServerHandler = handler
        binding.btnStartServer.isEnabled = false
        // startServer() blocks at closeFuture().sync() for the server's whole lifetime,
        // so it must run off the main thread.
        cs.launch { server.startServer() }
    }

    override fun onDestroy() {
        webSocketServer?.let { server -> cs.launch { server.stopServer() } }
        super.onDestroy()
    }

    // =====================================================

    class WebSocketServer(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) :
        BaseNettyServer(
            port,
            connectionListener,
            true
        ) {
        override fun getTagName() = "WSSA-WS"
    }

    @ChannelHandler.Sharable
    class WebSocketServerHandler(private val netty: BaseNettyServer) :
        BaseServerChannelInboundHandler<Any>(netty) {
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

        fun responseClientMsg(clientChannel: Channel, msg: String): Boolean =
            netty.executeCommand(clientChannel, msg, "responseClientMsg")

        override fun release() {
        }
    }
}
