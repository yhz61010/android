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
import com.ho1ho.socket_sdk.framework.BaseNettyClient
import com.ho1ho.socket_sdk.framework.BaseNettyServer
import com.ho1ho.socket_sdk.framework.inter.NettyConnectionListener
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
import java.nio.charset.Charset

class WebSocketServerActivity : BaseDemonstrationActivity() {

    companion object {
        const val TAG = "WebSocketServerActivity"
    }

    private val cs = CoroutineScope(Dispatchers.IO)

    private lateinit var webSocketServer: WebSocketServer
    private lateinit var webSocketServerHandler: WebSocketServerHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_socket_server)

        val connectionListener = object : NettyConnectionListener {
            override fun onConnected(netty: BaseNettyClient) {
                LLog.i(TAG, "onConnected")
                ToastUtil.showDebugToast("onConnected")
            }

            @SuppressLint("SetTextI18n")
            override fun onReceivedData(netty: BaseNettyClient, data: Any?) {
                LLog.i(TAG, "onReceivedData: ${data?.toJsonString()}")
                runOnUiThread { txtView.text = txtView.text.toString() + data?.toJsonString() + "\n" }
            }

            override fun onDisconnected(netty: BaseNettyClient) {
                LLog.w(TAG, "onDisconnect")
                ToastUtil.showDebugToast("onDisconnect")
            }

            override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?) {
                LLog.w(TAG, "onFailed code: $code message: $msg")
                ToastUtil.showDebugToast("onFailed code: $code message: $msg")
            }

            override fun onException(netty: BaseNettyClient, cause: Throwable) {
                LLog.e(TAG, "onCaughtException reason: ${cause.message}")
                ToastUtil.showDebugToast("onCaughtException")
            }
        }

//        webSocketServer = WebSocketServer(10086, connectionListener, ConstantRetry(10, 2000))
//        webSocketServerHandler = WebSocketServerHandler(webSocketServer)
//        webSocketServer.initHandler(webSocketServerHandler)
    }

    fun onStartServerClick(@Suppress("UNUSED_PARAMETER") view: View) {
        cs.launch {
            repeat(1) {
//                webSocketServer.connect()
            }
        }
    }

    override fun onDestroy() {
        cs.launch {
//            webSocketServer.release()
        }
        super.onDestroy()
    }

    // =====================================================

    class WebSocketServer(port: Int, connectionListener: NettyConnectionListener, retryStrategy: RetryStrategy) :
        BaseNettyServer(port, connectionListener, retryStrategy) {
//        override fun addLastToPipeline(pipeline: ChannelPipeline) {
//            with(pipeline) {
//                addLast(DelimiterBasedFrameDecoder(65535, *Delimiters.lineDelimiter()))
//                addLast(StringDecoder())
//                addLast(StringEncoder())
//            }
//        }
    }

    @ChannelHandler.Sharable
    class WebSocketServerHandler(private val netty: BaseNettyClient) : BaseChannelInboundHandler<Any>(netty) {
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
}