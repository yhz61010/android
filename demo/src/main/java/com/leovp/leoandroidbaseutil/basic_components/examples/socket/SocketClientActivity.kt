package com.leovp.leoandroidbaseutil.basic_components.examples.socket

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.socket_sdk.framework.client.BaseClientChannelInboundHandler
import com.leovp.socket_sdk.framework.client.BaseNettyClient
import com.leovp.socket_sdk.framework.client.ClientConnectListener
import com.leovp.socket_sdk.framework.client.retry_strategy.ExponentRetry
import com.leovp.socket_sdk.framework.client.retry_strategy.base.RetryStrategy
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
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

        val connectionListener = object : ClientConnectListener<BaseNettyClient> {
            override fun onConnected(netty: BaseNettyClient) {
                LogContext.log.i(TAG, "onConnected")
                ToastUtil.showDebugToast("onConnected")
            }

            @SuppressLint("SetTextI18n")
            override fun onReceivedData(netty: BaseNettyClient, data: Any?) {
                LogContext.log.i(TAG, "onReceivedData: ${data?.toJsonString()}")
                runOnUiThread { txtView.text = txtView.text.toString() + data?.toJsonString() + "\n" }
            }

            override fun onDisconnected(netty: BaseNettyClient) {
                LogContext.log.w(TAG, "onDisconnect")
                ToastUtil.showDebugToast("onDisconnect")
            }

            override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?) {
                LogContext.log.w(TAG, "onFailed code: $code message: $msg")
                ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            }
        }

        socketClient = SocketClient("61010.ml", 9443, connectionListener, ExponentRetry(5, 1))
        socketClientHandler = SocketClientHandler(socketClient)
        socketClient.initHandler(socketClientHandler)
    }

    override fun onDestroy() {
        cs.launch {
            if (::socketClient.isInitialized) socketClient.release()
        }
        super.onDestroy()
    }

    fun onConnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            repeat(1) {
                if (::socketClient.isInitialized) socketClient.connect()

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
            if (::socketClientHandler.isInitialized) {
                val result = socketClientHandler.sendMsgToServer(editText.text.toString())
                withContext(Dispatchers.Main) { editText.text.clear(); if (!result) ToastUtil.showDebugErrorToast("Send command error") }
            }
        }
    }

    fun onDisconnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            if (::socketClient.isInitialized) socketClient.disconnectManually()
        }
    }

    fun onConnectRelease(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            if (::socketClient.isInitialized) socketClient.release()
        }
    }

    // =====================================================

    class SocketClient(host: String, port: Int, connectionListener: ClientConnectListener<BaseNettyClient>, retryStrategy: RetryStrategy) :
        BaseNettyClient(host, port, connectionListener, retryStrategy) {
        override fun getTagName() = "SocketClient"
    }

    @ChannelHandler.Sharable
    class SocketClientHandler(private val netty: BaseNettyClient) : BaseClientChannelInboundHandler<String>(netty) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: String) {
            netty.connectionListener.onReceivedData(netty, msg)
        }

        fun sendMsgToServer(msg: String): Boolean {
            return netty.executeCommand("SocketCmd", "Send msg to server", msg)
        }

        override fun release() {
        }
    }

    companion object {
        private const val TAG = "SocketClient"
    }
}