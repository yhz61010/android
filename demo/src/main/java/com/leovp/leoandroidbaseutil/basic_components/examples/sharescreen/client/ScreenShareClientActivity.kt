package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.client

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
import androidx.annotation.Keep
import com.leovp.androidbase.exts.*
import com.leovp.androidbase.exts.kotlin.asByteAndForceToBytes
import com.leovp.androidbase.exts.kotlin.toBytesLE
import com.leovp.androidbase.exts.kotlin.toHexadecimalString
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.AppUtil
import com.leovp.androidbase.utils.ByteUtil
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
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_TRIGGER_I_FRAME
import com.leovp.socket_sdk.framework.base.decoder.CustomSocketByteStreamDecoder
import com.leovp.socket_sdk.framework.client.BaseClientChannelInboundHandler
import com.leovp.socket_sdk.framework.client.BaseNettyClient
import com.leovp.socket_sdk.framework.client.ClientConnectListener
import com.leovp.socket_sdk.framework.client.retry_strategy.ConstantRetry
import com.leovp.socket_sdk.framework.client.retry_strategy.base.RetryStrategy
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
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

    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUtil.requestFullScreen(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_share_client)

        val screenInfo = DeviceUtil.getAvailableResolution(this)
        surfaceView.holder.setFixedSize(screenInfo.x, screenInfo.y)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                LogContext.log.w(ITAG, "=====> surfaceCreated <=====")
                // When surface recreated, we need to redraw screen again.
                // The surface will be recreated if you reopen you app from background
                if (sps != null && pps != null) {
                    initDecoder(sps!!, pps!!)
                    webSocketClientHandler?.triggerIFrame()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                LogContext.log.w(ITAG, "=====> surfaceChanged <=====")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                LogContext.log.w(ITAG, "=====> surfaceDestroyed <=====")
            }
        })

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) connectToServer() else releaseConnection()
        }

        finger.strokeColor = Color.RED
        finger.inEditMode = false

        finger.touchUpCallback = object : FingerPaintView.TouchUpCallback {
            override fun onTouchDown(x: Float, y: Float, paint: Paint) {
                webSocketClientHandler?.sendPaintData(TouchType.DOWN, x, y, paint)
            }

            override fun onTouchMove(x: Float, y: Float, paint: Paint) {
                webSocketClientHandler?.sendPaintData(TouchType.MOVE, x, y, paint)
            }

            override fun onTouchUp(x: Float, y: Float, paint: Paint) {
                webSocketClientHandler?.sendPaintData(TouchType.UP, x, y, paint)
            }

            override fun onClear() {
                webSocketClientHandler?.clearCanvas()
            }

            override fun onUndo() {
                webSocketClientHandler?.undoDraw()
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
        this.sps = sps
        this.pps = pps
        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
//        decoder = MediaCodec.createByCodecName("OMX.google.h264.decoder")
        val screenInfo = DeviceUtil.getAvailableResolution(this)
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
            LogContext.log.e(ITAG, "onError e=${e.message}", e)
        }
    }

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / 20

    // ============================================
    private var webSocketClient: WebSocketClient? = null
    private var webSocketClientHandler: WebSocketClientHandler? = null

    class WebSocketClient(webSocketUri: URI, connectionListener: ClientConnectListener<BaseNettyClient>, retryStrategy: RetryStrategy) :
        BaseNettyClient(webSocketUri, connectionListener, retryStrategy) {
        override fun getTagName() = "ScreenShareClientActivity"

        override fun addLastToPipeline(pipeline: ChannelPipeline) {
            pipeline.addLast("messageDecoder", CustomSocketByteStreamDecoder())
        }
    }

    @ChannelHandler.Sharable
    class WebSocketClientHandler(private val netty: BaseNettyClient) : BaseClientChannelInboundHandler<Any>(netty) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
            val receivedByteBuf = (msg as WebSocketFrame).content().retain()
            // Data length
            receivedByteBuf.readIntLE()
            // Command ID
            val cmdId = receivedByteBuf.readByte().toInt()
            // Protocol version
            receivedByteBuf.readByte()

            val bodyBytes = ByteArray(receivedByteBuf.readableBytes())
            receivedByteBuf.getBytes(6, bodyBytes)
            receivedByteBuf.release()
            netty.connectionListener.onReceivedData(netty, bodyBytes, cmdId)
        }

        fun triggerIFrame(): Boolean {
            val cmdArray = CmdBean(CMD_TRIGGER_I_FRAME, null, null).toJsonString().encodeToByteArray()

            val cId = CMD_TRIGGER_I_FRAME.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + cmdArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, cmdArray)

            return netty.executeCommand("TriggerIFrame", "Acquire I Frame", command)
        }

        fun sendDeviceScreenInfoToServer(point: Point): Boolean {
            val paintArray = CmdBean(CMD_DEVICE_SCREEN_INFO, null, point).toJsonString().encodeToByteArray()

            val cId = CMD_DEVICE_SCREEN_INFO.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand("ScreenInfo", "Send screen info to server", command)
        }

        fun sendPaintData(type: TouchType, x: Float, y: Float, paint: Paint): Boolean {
            val paintBean = PaintBean(type, x, y, paint.color, paint.style, paint.strokeWidth)
            val paintArray = CmdBean(CMD_TOUCH_EVENT, paintBean, null).toJsonString().encodeToByteArray()

            val cId = CMD_TOUCH_EVENT.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand("WebSocketCmd", "Paint[${type.name}]", command, false)
        }

        fun clearCanvas(): Boolean {
            val paintBean = PaintBean(TouchType.CLEAR)
            val paintArray = CmdBean(CMD_TOUCH_EVENT, paintBean, null).toJsonString().encodeToByteArray()

            val cId = CMD_TOUCH_EVENT.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand("WebSocketCmd", "Clear canvas", command)
        }

        fun undoDraw(): Boolean {
            val paintBean = PaintBean(TouchType.UNDO)
            val paintArray = CmdBean(CMD_TOUCH_EVENT, paintBean, null).toJsonString().encodeToByteArray()

            val cId = CMD_TOUCH_EVENT.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand("WebSocketCmd", "Undo", command)
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
                webSocketClientHandler?.sendDeviceScreenInfoToServer(DeviceUtil.getRealResolution(this@ScreenShareClientActivity))
            }

            @SuppressLint("SetTextI18n")
            override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
                val dataArray = data as ByteArray
                LogContext.log.i(ITAG, "CMD=$action onReceivedData[${dataArray.size}]")

                when (action) {
                    CMD_DEVICE_SCREEN_INFO -> Unit
                    CMD_GRAPHIC_CSD -> {
                        foundCsd.set(true)
                        queue.offer(dataArray)
                        LogContext.log.w(ITAG, "csd=${dataArray.contentToString()}")
                        val sps = H264Util.getSps(dataArray)
                        val pps = H264Util.getPps(dataArray)
                        LogContext.log.w(ITAG, "initDecoder with sps=${sps?.contentToString()} pps=${pps?.contentToString()}")
                        if (sps != null && pps != null) {
                            initDecoder(sps, pps)
                            webSocketClientHandler?.triggerIFrame()
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
                cs.launch { webSocketClient?.disconnectManually() }
                runOnUiThread { toggleButton.isChecked = false }

            }
        }

        // This ip is remote phone ip.
        // You can get this value as following:
        // 1. adb shell (Login your server phone)
        // 2. Execute: ip a
        // Find ip like: 10.10.9.126
        val url = URI("ws://${etServerIp.text}:10086/ws")
        webSocketClient = WebSocketClient(url, connectionListener, ConstantRetry(10, 2000)).also {
            webSocketClientHandler = WebSocketClientHandler(it)
            it.initHandler(webSocketClientHandler)
            cs.launch {
                it.connect()
            }
        }
    }

    private fun releaseConnection() {
        sps = null
        pps = null
        finger.clear()
        queue.clear()
        runCatching {
            decoder?.release()
        }.onFailure { it.printStackTrace() }
        cs.launch { webSocketClient?.release() }
    }

    fun onClearClick(@Suppress("UNUSED_PARAMETER") view: View) {
        finger.clear()
    }

    fun onUndoClick(@Suppress("UNUSED_PARAMETER") view: View) {
        finger.undo()
    }
}