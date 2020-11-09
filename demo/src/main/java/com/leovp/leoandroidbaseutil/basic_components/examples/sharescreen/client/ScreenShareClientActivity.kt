package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.client

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.exts.toHexadecimalString
import com.leovp.androidbase.exts.toJsonString
import com.leovp.androidbase.utils.AppUtil
import com.leovp.androidbase.utils.device.DeviceUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.media.H264Util
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.drawonscreen.FingerPaintView
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_DEVICE_SCREEN_INFO
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_GRAPHIC_CSD
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_TOUCH_EVENT
import com.leovp.socket_sdk.framework.client.BaseClientChannelInboundHandler
import com.leovp.socket_sdk.framework.client.BaseNettyClient
import com.leovp.socket_sdk.framework.client.ClientConnectListener
import com.leovp.socket_sdk.framework.client.retry_strategy.ConstantRetry
import com.leovp.socket_sdk.framework.client.retry_strategy.base.RetryStrategy
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import kotlinx.android.synthetic.main.activity_screen_share_client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class ScreenShareClientActivity : BaseDemonstrationActivity() {

    private val cs = CoroutineScope(Dispatchers.IO)

    private var decoder: MediaCodec? = null
    private var outputFormat: MediaFormat? = null

    private var frameCount: Long = 0
    private val queue = ConcurrentLinkedQueue<ByteArray>()

    @Keep
    enum class TouchType {
        DOWN, MOVE, UP, CLEAR, UNDO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUtil.requestFullScreen(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_share_client)

        val screenInfo = DeviceUtil.getResolution(this)
        surfaceView.holder.setFixedSize(screenInfo.x, screenInfo.y)
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                connectToServer()
            } else {
                releaseConnection()
            }
        }

        finger.strokeColor = Color.RED
        finger.inEditMode = false

        finger.touchUpCallback = object : FingerPaintView.TouchUpCallback {
            override fun onTouchDown(x: Float, y: Float, paint: Paint) {
                if (::webSocketClientHandler.isInitialized) webSocketClientHandler.sendPaintData(TouchType.DOWN, x, y, paint)
            }

            override fun onTouchMove(x: Float, y: Float, paint: Paint) {
                if (::webSocketClientHandler.isInitialized) webSocketClientHandler.sendPaintData(TouchType.MOVE, x, y, paint)
            }

            override fun onTouchUp(x: Float, y: Float, paint: Paint) {
                if (::webSocketClientHandler.isInitialized) webSocketClientHandler.sendPaintData(TouchType.UP, x, y, paint)
            }

            override fun onClear() {
                if (::webSocketClientHandler.isInitialized) webSocketClientHandler.clearCanvas()
            }

            override fun onUndo() {
                if (::webSocketClientHandler.isInitialized) webSocketClientHandler.undoDraw()
            }
        }

        switchDraw.setOnCheckedChangeListener { _, isChecked ->
            finger.inEditMode = isChecked
        }
    }

    override fun onResume() {
        AppUtil.hideNavigationBar(this)
        super.onResume()
    }

    override fun onDestroy() {
        releaseConnection()
        super.onDestroy()
    }

    private fun initDecoder(sps: ByteArray, pps: ByteArray) {
        LogContext.log.w(ITAG, "initDecoder sps=${sps.toHexadecimalString()} pps=${pps.toHexadecimalString()}")
        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
//        decoder = MediaCodec.createByCodecName("OMX.google.h264.decoder")
        val screenInfo = DeviceUtil.getResolution(this)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, screenInfo.x, screenInfo.y)
//        val sps = byteArrayOf(0, 0, 0, 1, 103, 66, -64, 51, -115, 104, 8, -127, -25, -66, 1, -31, 16, -115, 64)
//        val pps = byteArrayOf(0, 0, 0, 1, 104, -50, 1, -88, 53, -56)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
        decoder?.configure(format, surfaceView.holder.surface, null, 0)
        outputFormat = decoder?.outputFormat // option B
        decoder?.setCallback(mediaCodecCallback)
        decoder?.start()
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
            try {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                // fill inputBuffer with valid data
                inputBuffer?.clear()
                val data = queue.poll()?.also {
//                LogContext.log.i(ITAG, "onInputBufferAvailable length=${it.size}")
                    inputBuffer?.put(it)
                }
                codec.queueInputBuffer(inputBufferId, 0, data?.size ?: 0, computePresentationTimeUs(++frameCount), 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
            runCatching {
                val outputBuffer = codec.getOutputBuffer(outputBufferId)
                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
                // bufferFormat is equivalent to member variable outputFormat
                // outputBuffer is ready to be processed or rendered.
                outputBuffer?.let {
//                LogContext.log.i(ITAG, "onOutputBufferAvailable length=${info.size}")
                    when (info.flags) {
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
                            val decodedData = ByteArray(info.size)
                            it.get(decodedData)
                            LogContext.log.w(ITAG, "Found SPS/PPS frame: ${decodedData.toHexadecimalString()}")
                        }
                        MediaCodec.BUFFER_FLAG_KEY_FRAME -> LogContext.log.i(ITAG, "Found Key Frame[" + info.size + "]")
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM -> {
                            // Do nothing
                        }
                        MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> {
                            // Do nothing
                        }
                        else -> {
                            // Do nothing
                        }
                    }
                }
                codec.releaseOutputBuffer(outputBufferId, true)
            }.onFailure { it.printStackTrace() }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            LogContext.log.w(ITAG, "onOutputFormatChanged format=$format")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LogContext.log.e(ITAG, "onError e=${e.message}")
        }
    }

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / 20

    // ============================================
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var webSocketClientHandler: WebSocketClientHandler

    class WebSocketClient(webSocketUri: URI, connectionListener: ClientConnectListener<BaseNettyClient>, retryStrategy: RetryStrategy) :
        BaseNettyClient(webSocketUri, connectionListener, retryStrategy) {
        override fun getTagName() = "ScreenShareClientActivity"
    }

    @ChannelHandler.Sharable
    class WebSocketClientHandler(private val netty: BaseNettyClient) : BaseClientChannelInboundHandler<Any>(netty) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
            val receivedByteBuf = (msg as WebSocketFrame).content().retain()
            val cmdId = receivedByteBuf.readIntLE()
            val bodyMessage = ByteBufUtil.getBytes(receivedByteBuf, 4, receivedByteBuf.capacity() - 4, false)
            receivedByteBuf.release()
            netty.connectionListener.onReceivedData(netty, bodyMessage, cmdId)
        }

        fun sendDeviceScreenInfoToServer(point: Point): Boolean {
            val cmd = CmdBean(CMD_DEVICE_SCREEN_INFO, null, point)
            return netty.executeCommand("ScreenInfo", "Send screen info to server", cmd.toJsonString())
        }

        fun sendPaintData(type: TouchType, x: Float, y: Float, paint: Paint): Boolean {
            val paintBean = PaintBean(type, x, y, paint.color, paint.style, paint.strokeWidth)
            val cmd = CmdBean(CMD_TOUCH_EVENT, paintBean, null)
            return netty.executeCommand("WebSocketCmd", "Paint[${type.name}]", cmd.toJsonString())
        }

        fun clearCanvas(): Boolean {
            val paintBean = PaintBean(TouchType.CLEAR)
            val cmd = CmdBean(CMD_TOUCH_EVENT, paintBean, null)
            return netty.executeCommand("WebSocketCmd", "Clear canvas", cmd.toJsonString())
        }

        fun undoDraw(): Boolean {
            val paintBean = PaintBean(TouchType.UNDO)
            val cmd = CmdBean(CMD_TOUCH_EVENT, paintBean, null)
            return netty.executeCommand("WebSocketCmd", "Undo draw", cmd.toJsonString())
        }

        override fun release() {
        }
    }

    @Keep
    data class CmdBean(val cmdId: Int, val paintBean: PaintBean?, val deviceInfo: Point?)

    @Keep
    data class PaintBean(
        val touchType: TouchType,
        val x: Float = 0f,
        val y: Float = 0f,
        val paintColor: Int = 0,
        val paintStyle: Paint.Style = Paint.Style.STROKE,
        val strokeWidth: Float = 0f
    )

    private fun connectToServer() {
        val connectionListener = object : ClientConnectListener<BaseNettyClient> {
            private val foundCsd = AtomicBoolean(false)

            override fun onConnected(netty: BaseNettyClient) {
                LogContext.log.i(ITAG, "onConnected")

                webSocketClientHandler.sendDeviceScreenInfoToServer(DeviceUtil.getResolutionWithVirtualKey(this@ScreenShareClientActivity))
            }

            @SuppressLint("SetTextI18n")
            override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
                val dataArray = data as ByteArray
                LogContext.log.i(ITAG, "CMD=$action onReceivedData[${dataArray.size}]")

                when (action) {
                    CMD_DEVICE_SCREEN_INFO -> {
                    }
                    CMD_GRAPHIC_CSD -> {
                        foundCsd.set(true)
                        queue.offer(dataArray)
                        LogContext.log.w(ITAG, "csd=${dataArray.contentToString()}")
                        val sps = H264Util.getSps(dataArray)
                        val pps = H264Util.getPps(dataArray)
                        LogContext.log.w(ITAG, "initDecoder with sps=${sps?.contentToString()} pps=${pps?.contentToString()}")
                        if (sps != null && pps != null) {
                            initDecoder(sps, pps)
                            return
                        } else {
                            ToastUtil.showErrorLongToast("Get SPS/PPS error.")
                            return
                        }
                    }
                }

                if (foundCsd.get()) {
                    queue.offer(dataArray)
                } else {
                    LogContext.log.i(ITAG, "Not found csd. Ignore video data.")
                }
            }

            override fun onDisconnected(netty: BaseNettyClient) {
                LogContext.log.w(ITAG, "onDisconnect")
                lostConnection()
            }

            override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?) {
                LogContext.log.w(ITAG, "onFailed code: $code message: $msg")
                lostConnection()
            }

            private fun lostConnection() {
                foundCsd.set(false)
                queue.clear()
                runCatching {
                    decoder?.release()
                }.onFailure { it.printStackTrace() }
                cs.launch { webSocketClient.disconnectManually() }
                runOnUiThread { toggleButton.isChecked = false }

            }
        }

        // This ip is remote phone ip.
        // You can get this value as following:
        // 1. adb shell (Login your server phone)
        // 2. Execute: ip a
        // Find ip like: 10.10.9.126
        val url = URI("ws://${etServerIp.text}:10086/ws")
        webSocketClient = WebSocketClient(url, connectionListener, ConstantRetry(10, 2000))
        webSocketClientHandler = WebSocketClientHandler(webSocketClient)
        webSocketClient.initHandler(webSocketClientHandler)
        cs.launch {
            webSocketClient.connect()
        }
    }

    private fun releaseConnection() {
        finger.clear()
        queue.clear()
        runCatching {
            decoder?.release()
        }.onFailure { it.printStackTrace() }
        cs.launch { if (::webSocketClient.isInitialized) webSocketClient.release() }
    }

    fun onClearClick(@Suppress("UNUSED_PARAMETER") view: View) {
        finger.clear()
    }

    fun onUndoClick(@Suppress("UNUSED_PARAMETER") view: View) {
        finger.undo()
    }
}