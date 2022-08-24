package com.leovp.demo.basiccomponents.examples.socket.eventbusbridge

import android.os.Bundle
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.leovp.androidbase.utils.ByteUtil
import com.leovp.basenetty.eventbus.handler.EventBusHandler
import com.leovp.basenetty.eventbus.util.EventBus
import com.leovp.basenetty.framework.client.BaseClientChannelInboundHandler
import com.leovp.basenetty.framework.client.BaseNettyClient
import com.leovp.basenetty.framework.client.ClientConnectListener
import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import com.leovp.basenetty.framework.client.retrystrategy.base.RetryStrategy
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityEventBusBridgeClientBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import de.undercouch.bson4jackson.BsonFactory
import de.undercouch.bson4jackson.BsonModule
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EventBusBridgeClientActivity : BaseDemonstrationActivity<ActivityEventBusBridgeClientBinding>() {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityEventBusBridgeClientBinding {
        return ActivityEventBusBridgeClientBinding.inflate(layoutInflater)
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private var webSocket: EventBusBridgeSocketClient? = null
    private var webSocketHandler: EventBusBridgeSocketHandler? = null

    private val connectionListener = object : ClientConnectListener<BaseNettyClient> {
        override fun onConnected(netty: BaseNettyClient) {
            LogContext.log.w("===== onConnected =====")
            webSocketHandler?.sendTest(netty)
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
            LogContext.log.e("===== onFailed =====")
        }

        override fun onDisconnected(netty: BaseNettyClient, byRemote: Boolean) {
            LogContext.log.w("===== onDisconnected =====")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wsUrl = "wss://your websocket address"
        val cookies = mapOf(
            HttpHeaderNames.ORIGIN.toString() to "your origin",
            HttpHeaderNames.COOKIE.toString() to "your cookie",
            HttpHeaderNames.REFERER.toString() to "your referer"
        )
        webSocket =
            EventBusBridgeSocketClient(
                URI(wsUrl),
                connectionListener,
                ConstantRetry(),
                cookies
            ).apply {
                webSocketHandler = EventBusBridgeSocketHandler(this)
                initHandler(webSocketHandler)
                ioScope.launch { this@apply.connect() }
            }
    }

    override fun onDestroy() {
        ioScope.launch { webSocket?.release() }
        ioScope.cancel()
        super.onDestroy()
    }

    class EventBusBridgeSocketClient(
        webSocketUri: URI,
        connectionListener: ClientConnectListener<BaseNettyClient>,
        retryStrategy: RetryStrategy,
        headers: Map<String, String>? = null,
    ) : BaseNettyClient(webSocketUri, connectionListener, false, retryStrategy, headers) {

        override fun getTagName() = "ebr-s"
    }

    @ChannelHandler.Sharable
    class EventBusBridgeSocketHandler(val client: BaseNettyClient) :
        BaseClientChannelInboundHandler<Any>(client) {

        companion object {
            private const val TAG = "ebr-h"
        }

        private val totalFrameData = AtomicReference(ByteArray(0))

        override fun release() {
        }

        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
            when (msg) {
                is BinaryWebSocketFrame,
                is ContinuationWebSocketFrame -> {
                    val receivedByteBuf = (msg as WebSocketFrame).content().retain()

                    val receivedBytes = ByteBufUtil.getBytes(receivedByteBuf)
                    // val showDebugData = if (receivedBytes.size >= 80) receivedBytes.toHexStringLE().substring(0, 80) else receivedBytes.toHexStringLE()
                    // if (LogContext.enableLog && GlobalConstants.DEBUG) LogContext.log.e(LogTag.TAG_PT, "ORI_VID[${receivedBytes.size}][${msg.isFinalFragment}]=$showDebugData")

                    val savedByteArray = totalFrameData.get()
                    var totalByteArray = ByteUtil.mergeBytes(savedByteArray, receivedBytes)
                    totalFrameData.compareAndSet(savedByteArray, totalByteArray)

                    if (msg.isFinalFragment) {
                        //                    if (LogContext.enableLog) LogContext.log.w(TAG, "Found FinalFragment.")
                        totalByteArray = totalFrameData.get()
                        totalFrameData.set(ByteArray(0))
                        receivedByteBuf.release()
                    } else {
                        //                    if (LogContext.enableLog) LogContext.log.d(TAG, "Found ContinuationWebSocketFrame.")
                        receivedByteBuf.release()
                        return
                    }

                    if (LogContext.enableLog) LogContext.log.i(
                        TAG,
                        "totalByteArray=${totalByteArray.decodeToString()}"
                    )

                    // TODO process your eventbus handler/replyHandler with address/replyAddress. For example:
                    //   replyAddress?.let { replyAddress ->
                    // //       LogContext.log.i(TAG, "ReplyAddress[$replyAddress] Processing replyAddress...")
                    //       EventBus.processReplyHandler(replyAddress) { h ->
                    //           h.handle(totalFrameData)
                    //       }
                    //   }
                    //   address?.let { address ->
                    //       EventBus.processHandlers(address) { idx, h ->
                    // //           LogContext.log.i(TAG, "Address[$address] Processing handler[$idx]...")
                    //           h.handle(totalFrameData)
                    //       }
                    //   }
                }
                else -> if (LogContext.enableLog) LogContext.log.i(
                    TAG,
                    "Invalid message type=[${msg::class.simpleName}]"
                )
            }
        }

        fun sendTest(netty: BaseNettyClient) {
            val msg = EventBus.send(
                address = "your send address",
                handler = object : EventBusHandler {
                    override fun handle(message: Any?) {
                    }
                }
            )
            netty.executeCommand(serializeData(msg), "send test")
        }

        // =========================
        private val om = ObjectMapper(BsonFactory())

        private fun serializeData(data: Any): ByteArray {
            val baos = ByteArrayOutputStream()
            om.registerModule(BsonModule())
            om.writeValue(baos, data)
            return baos.toByteArray()
        }

        @Suppress("unused")
        private fun deserializeData(serializedData: ByteArray): JsonNode {
            return om.readTree(ByteArrayInputStream(serializedData))
        }

        @Suppress("unused")
        private fun <T> deserializeData(serializedData: ByteArray, clazz: Class<T>): T {
            return om.readValue(ByteArrayInputStream(serializedData), clazz)
        }
        // =========================
    }
}
