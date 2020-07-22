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
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import kotlinx.android.synthetic.main.activity_socket_client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SocketActivity : BaseDemonstrationActivity() {
    private val cs = CoroutineScope(Dispatchers.IO)

    private lateinit var socketClient: SocketClient
    private lateinit var socketClientHandler: SocketClientHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_socket_client)

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
                LLog.i(TAG, "onFailed code: $code message: $msg")
                ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            }

            override fun onException(client: BaseNettyClient, cause: Throwable) {
                LLog.i(TAG, "onCaughtException")
                ToastUtil.showDebugToast("onCaughtException")
            }

        }

        socketClient = SocketClient("61010.ml", 9443, connectionListener)
        socketClientHandler = SocketClientHandler(socketClient)
        socketClient.initHandler(socketClientHandler)
    }

    override fun onDestroy() {
        cs.launch {
            socketClient.release()
        }
        super.onDestroy()
    }

    fun onConnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            repeat(1) {
                socketClient.connect()

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
            val result = socketClientHandler.sendMsgToServer(editText.text.toString())
            withContext(Dispatchers.Main) { editText.text.clear(); if (!result) ToastUtil.showDebugErrorToast("Send command error") }
        }
    }

    fun onDisconnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            socketClient.disconnectManually()
        }
    }

    fun onConnectRelease(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            socketClient.release()
        }
    }

    // =====================================================

    class SocketClient(host: String, port: Int, connectionListener: NettyConnectionListener) : BaseNettyClient(host, port, connectionListener) {
        override fun addLastToPipeline(pipeline: ChannelPipeline) {
            with(pipeline) {
                addLast(DelimiterBasedFrameDecoder(65535, *Delimiters.lineDelimiter()))
                addLast(StringDecoder())
                addLast(StringEncoder())
            }
        }
    }

    @ChannelHandler.Sharable
    class SocketClientHandler(private val client: BaseNettyClient) : BaseChannelInboundHandler<String>(client) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: String) {
            client.connectionListener.onReceivedData(client, msg)
        }

        fun sendMsgToServer(msg: String): Boolean {
            return client.executeCommand(msg)
        }

        override fun release() {
        }
    }

    companion object {
        const val TAG = "SocketClient"
    }
}