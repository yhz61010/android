package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.client

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import androidx.annotation.Keep
import com.leovp.androidbase.exts.android.*
import com.leovp.androidbase.exts.kotlin.*
import com.leovp.androidbase.utils.ByteUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.media.H264Util
import com.leovp.drawonscreen.FingerPaintView
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_DEVICE_SCREEN_INFO
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_GRAPHIC_CSD
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_PAINT_EVENT
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_TOUCH_EVENT
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_TRIGGER_I_FRAME
import com.leovp.leoandroidbaseutil.databinding.ActivityScreenShareClientBinding
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class ScreenShareClientActivity : BaseDemonstrationActivity() {
    companion object {
        private const val CLICK_THRESHOLD = 8
    }

    private lateinit var binding: ActivityScreenShareClientBinding

    private val cs = CoroutineScope(Dispatchers.IO)

    private var decoder: MediaCodec? = null
    private var outputFormat: MediaFormat? = null

    private var frameCount: Long = 0
    private val queue = ConcurrentLinkedQueue<ByteArray>()

    @Keep
    enum class TouchType {
        DOWN, MOVE, UP, CLEAR, UNDO, DRAG, HOME, BACK, RECENT
    }

    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    private lateinit var screenInfo: Point

    override fun onCreate(savedInstanceState: Bundle?) {
        requestFullScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityScreenShareClientBinding.inflate(layoutInflater).apply { setContentView(root) }

        screenInfo = getAvailableResolution()
//        binding.surfaceView.holder.setFixedSize(screenInfo.x, screenInfo.y)
        binding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
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
                LogContext.log.w(ITAG, "=====> surfaceChanged($width x $height format=$format) <=====")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                LogContext.log.w(ITAG, "=====> surfaceDestroyed <=====")
            }
        })

        binding.toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) connectToServer() else releaseConnection()
        }

        binding.finger.strokeColor = Color.RED
        binding.finger.inEditMode = false

        binding.finger.touchUpCallback = object : FingerPaintView.TouchUpCallback {
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

        binding.switchDraw.setOnCheckedChangeListener { _, isChecked ->
            binding.finger.inEditMode = isChecked
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LogContext.log.i("onConfigurationChanged: ${newConfig.toJsonString()}")
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LogContext.log.i("Running in LANDSCAPE ${screenInfo.toJsonString()}")
            binding.surfaceView.holder.setFixedSize(screenInfo.x * screenInfo.x / screenInfo.y, screenInfo.x)
        } else {
            LogContext.log.i("Running in PORTRAIT ${screenInfo.toJsonString()}")
            binding.surfaceView.holder.setFixedSize(screenInfo.x, screenInfo.y)
        }
    }

    override fun onResume() {
        hideNavigationBar()
        super.onResume()
    }

    override fun onDestroy() {
        releaseConnection()
        super.onDestroy()
    }

    private fun initDecoder(sps: ByteArray, pps: ByteArray) {
        LogContext.log.w(ITAG, "initDecoder sps=${sps.toHexStringLE()} pps=${pps.toHexStringLE()}")
        this.sps = sps
        this.pps = pps
        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
//        decoder = MediaCodec.createByCodecName("OMX.google.h264.decoder")
        val screenInfo = getAvailableResolution()
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, screenInfo.x, screenInfo.y)
//        val sps = byteArrayOf(0, 0, 0, 1, 103, 66, -64, 51, -115, 104, 8, -127, -25, -66, 1, -31, 16, -115, 64)
//        val pps = byteArrayOf(0, 0, 0, 1, 104, -50, 1, -88, 53, -56)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
        decoder?.configure(format, binding.surfaceView.holder.surface, null, 0)
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
                            LogContext.log.w(ITAG, "Found SPS/PPS frame: HEX[${decodedData.toHexStringLE()}]")
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
            val cmdArray = CmdBean(CMD_TRIGGER_I_FRAME, null, null, null).toJsonString().encodeToByteArray()

            val cId = CMD_TRIGGER_I_FRAME.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + cmdArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, cmdArray)

            return netty.executeCommand("TriggerIFrame", "Acquire I Frame", command)
        }

        fun sendDeviceScreenInfoToServer(point: Point): Boolean {
            val paintArray = CmdBean(CMD_DEVICE_SCREEN_INFO, null, point, null).toJsonString().encodeToByteArray()

            val cId = CMD_DEVICE_SCREEN_INFO.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand("ScreenInfo", "Send screen info to server", command)
        }

        fun sendDragData(x: Float, y: Float, dstX: Float, dstY: Float, duration: Long): Boolean {
            val touchBean = TouchBean(TouchType.DRAG, x, y, dstX, dstY, duration)
            val touchArray = CmdBean(ScreenShareMasterActivity.CMD_TOUCH_DRAG, null, null, touchBean).toJsonString().encodeToByteArray()
            val cId = ScreenShareMasterActivity.CMD_TOUCH_DRAG.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + touchArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, touchArray)

            return netty.executeCommand("WebSocketTouchDragCmd", "Touch[${touchBean.touchType}]", command, false)
        }

        fun sendTouchData(type: TouchType, x: Float, y: Float): Boolean {
            val touchBean = TouchBean(type, x, y)
            val cmdId = when (type) {
                TouchType.RECENT -> ScreenShareMasterActivity.CMD_TOUCH_RECENT
                TouchType.HOME -> ScreenShareMasterActivity.CMD_TOUCH_HOME
                TouchType.BACK -> ScreenShareMasterActivity.CMD_TOUCH_BACK
                else -> CMD_TOUCH_EVENT
            }
            val touchArray = CmdBean(cmdId, null, null, touchBean).toJsonString().encodeToByteArray()
            val cId = cmdId.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + touchArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, touchArray)

            return netty.executeCommand("WebSocketTouchCmd", "Touch[${type.name}]", command, false)
        }

        fun sendPaintData(type: TouchType, x: Float, y: Float, paint: Paint): Boolean {
            val paintBean = PaintBean(type, x, y, paint.color, paint.style, paint.strokeWidth)
            val paintArray = CmdBean(CMD_PAINT_EVENT, paintBean, null, null).toJsonString().encodeToByteArray()

            val cId = CMD_PAINT_EVENT.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand("WebSocketCmd", "Paint[${type.name}]", command, false)
        }

        fun clearCanvas(): Boolean {
            val paintBean = PaintBean(TouchType.CLEAR)
            val paintArray = CmdBean(CMD_PAINT_EVENT, paintBean, null, null).toJsonString().encodeToByteArray()

            val cId = CMD_PAINT_EVENT.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand("WebSocketCmd", "Clear canvas", command)
        }

        fun undoDraw(): Boolean {
            val paintBean = PaintBean(TouchType.UNDO)
            val paintArray = CmdBean(CMD_PAINT_EVENT, paintBean, null, null).toJsonString().encodeToByteArray()

            val cId = CMD_PAINT_EVENT.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand("WebSocketCmd", "Undo", command)
        }

        override fun release() {
        }
    }

    @Keep
    data class CmdBean(val cmdId: Int, val paintBean: PaintBean?, val deviceInfo: Point?, val touchBean: TouchBean?)

    @Keep
    data class PaintBean(
        val touchType: TouchType,
        val x: Float = 0f,
        val y: Float = 0f,
        val paintColor: Int = 0,
        val paintStyle: Paint.Style = Paint.Style.STROKE,
        val strokeWidth: Float = 0f
    )

    @Keep
    data class TouchBean(
        val touchType: TouchType,
        var x: Float = 0f,
        var y: Float = 0f,
        var dstX: Float = 0f,
        var dstY: Float = 0f,
        var duration: Long = 0
    )

    private fun connectToServer() {
        val connectionListener = object : ClientConnectListener<BaseNettyClient> {
            private val foundCsd = AtomicBoolean(false)

            override fun onConnected(netty: BaseNettyClient) {
                LogContext.log.i(ITAG, "onConnected")
                webSocketClientHandler?.sendDeviceScreenInfoToServer(getRealResolution())
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
                            toast("Get SPS/PPS error.", duration = Toast.LENGTH_LONG, error = true)
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

            override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
                LogContext.log.w(ITAG, "onFailed code: $code message: $msg")
//                lostConnection()
            }

            private fun lostConnection() {
                foundCsd.set(false)
                queue.clear()
                runCatching {
                    decoder?.release()
                }.onFailure { it.printStackTrace() }
                cs.launch { webSocketClient?.disconnectManually() }
                runOnUiThread { binding.toggleButton.isChecked = false }

            }
        }

        // This ip is remote phone ip.
        // You can get this value as following:
        // 1. adb shell (Login your server phone)
        // 2. Execute: ip a
        // Find ip like: 10.10.9.126
        val url = URI("ws://${binding.etServerIp.text}:10086/")
        webSocketClient = WebSocketClient(url, connectionListener, ConstantRetry(10, 2000)).also {
            webSocketClientHandler = WebSocketClientHandler(it)
            it.initHandler(webSocketClientHandler)
            cs.launch { it.connect() }
        }
    }

    private fun releaseConnection() {
        sps = null
        pps = null
        binding.finger.clear()
        queue.clear()
        runCatching {
            decoder?.release()
        }.onFailure { it.printStackTrace() }
        cs.launch { webSocketClient?.release() }
    }

    fun onClearClick(@Suppress("UNUSED_PARAMETER") view: View) {
        binding.finger.clear()
    }

    fun onUndoClick(@Suppress("UNUSED_PARAMETER") view: View) {
        binding.finger.undo()
    }

    fun onRecentClick(@Suppress("UNUSED_PARAMETER") view: View) {
        webSocketClientHandler?.sendTouchData(TouchType.RECENT, 0f, 0f)
    }

    fun onBackClick(@Suppress("UNUSED_PARAMETER") view: View) {
        webSocketClientHandler?.sendTouchData(TouchType.BACK, 0f, 0f)
    }

    fun onHomeClick(@Suppress("UNUSED_PARAMETER") view: View) {
        webSocketClientHandler?.sendTouchData(TouchType.HOME, 0f, 0f)
    }

    private var touchDownRawX = 0F
    private var touchDownRawY = 0F
    private var touchDownStartTime = 0L

    private var touchUpRawX = 0F
    private var touchUpRawY = 0F
    private var touchUpStartTime = 0L

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (binding.finger.inEditMode) {
            return super.dispatchTouchEvent(ev)
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownRawX = ev.rawX
                touchDownRawY = ev.rawY
                touchDownStartTime = SystemClock.currentThreadTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> Unit
            MotionEvent.ACTION_UP -> {
                touchUpRawX = ev.rawX
                touchUpRawY = ev.rawY
                touchUpStartTime = SystemClock.currentThreadTimeMillis()

                if (abs(touchUpRawX - touchDownRawX) <= CLICK_THRESHOLD && abs(touchDownRawY - touchUpRawY) <= CLICK_THRESHOLD) {
                    LogContext.log.w("Accept as click")
                    webSocketClientHandler?.sendTouchData(TouchType.DOWN, touchUpRawX, touchDownRawY)
                    return super.dispatchTouchEvent(ev)
                }
            }
        }

        LogContext.log.w("Drag from ($touchDownRawX x $touchDownRawY) to ($touchUpRawX x $touchUpRawY) duration=${touchUpStartTime - touchDownStartTime}ms")
        webSocketClientHandler?.sendDragData(touchDownRawX, touchDownRawY, touchUpRawX, touchUpRawY, touchUpStartTime - touchDownStartTime)
        return super.dispatchTouchEvent(ev)
    }
}