package com.leovp.demo.basiccomponents.examples.socket

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.leovp.android.exts.toast
import com.leovp.basenetty.framework.client.BaseClientChannelInboundHandler
import com.leovp.basenetty.framework.client.BaseNettyClient
import com.leovp.basenetty.framework.client.ClientConnectListener
import com.leovp.basenetty.framework.client.retrystrategy.ExponentRetry
import com.leovp.basenetty.framework.client.retrystrategy.base.RetryStrategy
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivitySocketClientBinding
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SocketClientActivity : BaseDemonstrationActivity<ActivitySocketClientBinding>() {
    override fun getTagName(): String = ITAG

    private val cs = CoroutineScope(Dispatchers.IO)

    private lateinit var socketClient: SocketClient
    private lateinit var socketClientHandler: SocketClientHandler

    override fun getViewBinding(savedInstanceState: Bundle?): ActivitySocketClientBinding {
        return ActivitySocketClientBinding.inflate(layoutInflater)
    }

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LogContext.log.i(TAG, "onConnected")
            toast("onConnected", debug = true)
        }

        @SuppressLint("SetTextI18n")
        override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
            LogContext.log.i(TAG, "onReceivedData: ${data?.toJsonString()}")
            runOnUiThread { binding.txtView.text = binding.txtView.text.toString() + data?.toJsonString() + "\n" }
        }

        override fun onDisconnected(netty: BaseNettyClient, byRemote: Boolean) {
            LogContext.log.w(TAG, "onDisconnect byRemote=$byRemote")
            toast("onDisconnect", debug = true)
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            toast("onFailed code: $code message: $msg", debug = true)
        }
    }

    private fun createSocket(): SocketClient {
        val svrIp = binding.etSvrIp.text.toString().substringBeforeLast(':')
        val svrPort = binding.etSvrIp.text.toString().substringAfterLast(':').toInt()
        socketClient = SocketClient(svrIp, svrPort, connectionListener, ExponentRetry(5, 1))
        socketClientHandler = SocketClientHandler(socketClient)
        socketClient.initHandler(socketClientHandler)

        return socketClient
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
                createSocket().connect()

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
                val result = socketClientHandler.sendMsgToServer(binding.editText.text.toString())
                withContext(Dispatchers.Main) {
                    binding.editText.text.clear()
                    if (!result) toast("Send command error", debug = true, error = true)
                }
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

    class SocketClient(
        host: String,
        port: Int,
        connectionListener: ClientConnectListener<BaseNettyClient>,
        retryStrategy: RetryStrategy
    ) :
        BaseNettyClient(host, port, connectionListener, retryStrategy) {
        override fun getTagName() = "SocketClient"
    }

    @ChannelHandler.Sharable
    class SocketClientHandler(private val netty: BaseNettyClient) : BaseClientChannelInboundHandler<String>(netty) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: String) {
            netty.connectionListener.onReceivedData(netty, msg)
        }

        fun sendMsgToServer(msg: String): Boolean {
            return netty.executeCommand(msg, "Send msg to server", "SocketCmd")
        }

        override fun release() {
        }
    }

    companion object {
        private const val TAG = "SocketClient"
    }
}
