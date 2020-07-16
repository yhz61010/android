package com.ho1ho.leoandroidbaseutil.ui

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
import com.ho1ho.socket_sdk.framework.base.inter.ReceivingDataListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.Delimiters
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import kotlinx.android.synthetic.main.activity_socket_client.*
import java.util.concurrent.RejectedExecutionException

class SocketActivity : BaseDemonstrationActivity() {
    private lateinit var socketClient: SocketClient
    private lateinit var socketClientHandler: SocketClientHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_socket_client)

        val connectionListener = object : NettyConnectionListener {
            override fun onConnecting(client: BaseNettyClient) {
                LLog.i(TAG, "onConnecting")
            }

            override fun onConnected(client: BaseNettyClient) {
                LLog.i(TAG, "onConnected")
            }

            override fun onDisconnected(client: BaseNettyClient) {
                LLog.i(TAG, "onDisconnect")
            }

            override fun onFailed(client: BaseNettyClient) {
                LLog.i(TAG, "onFailed")
            }

            override fun onException(client: BaseNettyClient, cause: Throwable) {
                LLog.i(TAG, "onCaughtException")
            }

        }

        socketClient = SocketClient("54.179.39.129", 8080, connectionListener)
        socketClientHandler = SocketClientHandler(socketClient)
        socketClient.initHandler(socketClientHandler)
        socketClient.receivingDataListener = object : ReceivingDataListener {
            override fun onReceiveData(which: Int, obj: Any?) {
                LLog.i(TAG, "onReceiveData: ${obj?.toJsonString()}")
                runOnUiThread { txtView.text = txtView.text.toString() + obj?.toJsonString() + "\n" }
            }
        }
    }

    fun onConnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        try {
            socketClient.connect()
        } catch (e: RejectedExecutionException) {
            ToastUtil.showDebugToast("Can not reuse netty: ${e.message}")
        } catch (e: Exception) {
            ToastUtil.showDebugToast("Unknown exception: ${e.message}")
        }
    }

    fun sendMsg(@Suppress("UNUSED_PARAMETER") view: View) {
        socketClientHandler.sendMsgToServer(editText.text.toString())
        txtView.text = ""
    }

    fun onDisconnectClick(@Suppress("UNUSED_PARAMETER") view: View) {
        socketClient.disconnect()
    }

    fun onConnectRelease(@Suppress("UNUSED_PARAMETER") view: View) {
        socketClient.release()
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
        @Throws(Exception::class)
        override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
            super.channelRead0(ctx, msg)
            LLog.i(TAG, "Client received: $msg")
            client.receivingDataListener?.onReceiveData(SOCKET_WHICH_CLIENT, msg)
        }

        fun sendMsgToServer(msg: String) {
            client.executeCommand(msg)
        }

        override fun release() {
        }
    }

    companion object {
        const val TAG = "SocketClient"
        private const val SOCKET_WHICH_CLIENT = 1
    }
}