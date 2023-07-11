package com.leovp.demo.basiccomponents.examples.socket

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
import com.leovp.demo.databinding.ActivitySocketServerBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 21-1-25 上午11:43
 */
class SocketServerActivity : BaseDemonstrationActivity<ActivitySocketServerBinding>(R.layout.activity_socket_server) {
    override fun getTagName(): String = ITAG

    companion object {
        private const val PORT = 10020
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivitySocketServerBinding {
        return ActivitySocketServerBinding.inflate(layoutInflater)
    }

    private val cs = CoroutineScope(Dispatchers.IO)

    private lateinit var socketServer: SocketServer
    private lateinit var socketServerHandler: SocketServerHandler

    @SuppressLint("SetTextI18n")
    private val connectionListener = object : ServerConnectListener<BaseNettyServer> {
        override fun onStarted(netty: BaseNettyServer) {
            LogContext.log.i(tag, "onStarted on port: $PORT")
            toast("onStarted on port: $PORT", debug = true)
            runOnUiThread { binding.txtResponse.text = "Server started on port: $PORT"; binding.sv.fullScroll(View.FOCUS_DOWN) }
        }

        override fun onStopped() {
            LogContext.log.i(tag, "onStop")
            toast("onStop", debug = true)
            runOnUiThread {
                binding.txtResponse.text = "${binding.txtResponse.text}\nServer stopped"
                binding.sv.fullScroll(View.FOCUS_DOWN)
            }
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
            socketServerHandler.responseClientMsg(clientChannel, "Server received: $data")
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
                if (::socketServer.isInitialized) socketServer.stopServer()
            }
        }
    }

    fun onStartServerClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            repeat(1) {
                socketServer = SocketServer(PORT, connectionListener)
                socketServerHandler = SocketServerHandler(socketServer)
                socketServer.initHandler(socketServerHandler)
                socketServer.startServer()
            }
        }
    }

    override fun onDestroy() {
        cs.launch {
            if (::socketServer.isInitialized) socketServer.stopServer()
        }
        super.onDestroy()
    }

    // =====================================================

    class SocketServer(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) : BaseNettyServer(
        port,
        connectionListener,
        false
    ) {
        override fun getTagName() = "SSA-S"
        override fun addLastToPipeline(pipeline: ChannelPipeline) {
            super.addLastToPipeline(pipeline)
            with(pipeline) {
                addLast(DelimiterBasedFrameDecoder(65535, *Delimiters.lineDelimiter()))
                addLast(StringDecoder())
                addLast(StringEncoder())
            }
        }
    }

    @ChannelHandler.Sharable
    class SocketServerHandler(private val netty: BaseNettyServer) : BaseServerChannelInboundHandler<Any>(netty) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
            netty.connectionListener.onReceivedData(netty, ctx.channel(), msg)
        }

        fun responseClientMsg(clientChannel: Channel, msg: String): Boolean {
            return netty.executeCommand(clientChannel, msg)
        }

        override fun release() {
        }
    }
}
