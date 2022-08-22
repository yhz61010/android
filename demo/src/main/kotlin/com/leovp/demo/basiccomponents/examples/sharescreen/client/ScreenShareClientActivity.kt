package com.leovp.demo.basiccomponents.examples.sharescreen.client

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import androidx.annotation.Keep
import com.leovp.android.exts.hideNavigationBar
import com.leovp.android.exts.requestFullScreenAfterVisible
import com.leovp.android.exts.requestFullScreenBeforeSetContentView
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.android.exts.screenRealResolution
import com.leovp.android.exts.toast
import com.leovp.androidbase.utils.ByteUtil
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.media.H264Util
import com.leovp.androidbase.utils.media.H265Util
import com.leovp.basenetty.framework.base.decoder.CustomSocketByteStreamDecoder
import com.leovp.basenetty.framework.client.BaseClientChannelInboundHandler
import com.leovp.basenetty.framework.client.BaseNettyClient
import com.leovp.basenetty.framework.client.ClientConnectListener
import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import com.leovp.basenetty.framework.client.retrystrategy.base.RetryStrategy
import com.leovp.bytes.asByteAndForceToBytes
import com.leovp.bytes.toBytesLE
import com.leovp.bytes.toHexStringLE
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.sharescreen.master.MediaProjectionService
import com.leovp.demo.basiccomponents.examples.sharescreen.master.ScreenShareMasterActivity
import com.leovp.demo.basiccomponents.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_DEVICE_SCREEN_INFO
import com.leovp.demo.basiccomponents.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_GRAPHIC_CSD
import com.leovp.demo.basiccomponents.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_PAINT_EVENT
import com.leovp.demo.basiccomponents.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_TOUCH_EVENT
import com.leovp.demo.basiccomponents.examples.sharescreen.master.ScreenShareMasterActivity.Companion.CMD_TRIGGER_I_FRAME
import com.leovp.demo.databinding.ActivityScreenShareClientBinding
import com.leovp.drawonscreen.FingerPaintView
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordMediaCodecStrategy
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenShareClientActivity : BaseDemonstrationActivity<ActivityScreenShareClientBinding>() {
    override fun getTagName(): String = ITAG

    companion object {
        private const val CLICK_THRESHOLD = 8
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityScreenShareClientBinding {
        return ActivityScreenShareClientBinding.inflate(layoutInflater)
    }

    private val cs = CoroutineScope(Dispatchers.IO)

    private var decoder: MediaCodec? = null
    private var outputFormat: MediaFormat? = null

    private var frameCount: Long = 0
    private val queue = ConcurrentLinkedQueue<ByteArray>()

    @Keep
    enum class TouchType {
        DOWN, MOVE, UP, CLEAR, UNDO, DRAG, HOME, BACK, RECENT
    }

    private var vps: ByteArray? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    private lateinit var screenInfo: Size

    override fun onCreate(savedInstanceState: Bundle?) {
        requestFullScreenBeforeSetContentView()
        super.onCreate(savedInstanceState)

        screenInfo = screenRealResolution
        //        binding.surfaceView.holder.setFixedSize(screenInfo.x, screenInfo.y)
        binding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                LogContext.log.w(ITAG, "=====> surfaceCreated <=====")
                // When surface recreated, we need to redraw screen again.
                // The surface will be recreated if you reopen you app from background
                if (sps != null && pps != null) {
                    initDecoder(vps, sps!!, pps!!)
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

        binding.finger.touchEventCallback = object : FingerPaintView.TouchEventCallback {
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
        val ratio = screenInfo.width * 1.0F / screenInfo.height
        //        LogContext.log.w("onConfigurationChanged: ${newConfig.toJsonString()}")
        val newWidth: Int
        val newHeight: Int
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //            val layoutParams = binding.surfaceView.layoutParams
            //            layoutParams.width = 800
            //            layoutParams.height = 600
            //            binding.surfaceView.layoutParams = layoutParams

            newWidth = (screenInfo.width * ratio).toInt()
            newHeight = screenInfo.width
            // If remote screen is still in portrait, do like this to preserve the video dimension.
            LogContext.log.w("Running in LANDSCAPE ${screenInfo.toJsonString()} SurfaceView size=$newWidth x $newHeight(ratio=$ratio)")
        } else {
            newWidth = screenInfo.width
            newHeight = screenInfo.height
            LogContext.log.w("Running in PORTRAIT ${screenInfo.toJsonString()} SurfaceView size=$newWidth x $newHeight(ratio=$ratio)")
        }
        binding.surfaceView.holder.setFixedSize(newWidth, newHeight)
    }

    override fun onResume() {
        super.onResume()
        requestFullScreenAfterVisible()
        hideNavigationBar()
    }

    override fun onDestroy() {
        releaseConnection()
        super.onDestroy()
    }

    private fun initDecoder(vps: ByteArray?, sps: ByteArray, pps: ByteArray) {
        LogContext.log.w(ITAG, "initDecoder vps=${vps?.toHexStringLE()} sps=${sps.toHexStringLE()} pps=${pps.toHexStringLE()}")
        this.vps = vps
        this.sps = sps
        this.pps = pps

        val screenInfo = screenAvailableResolution
        val format = MediaFormat.createVideoFormat(
            when (MediaProjectionService.VIDEO_ENCODE_TYPE) {
                ScreenRecordMediaCodecStrategy.EncodeType.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                ScreenRecordMediaCodecStrategy.EncodeType.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
            },
            screenInfo.width, screenInfo.height
        )

        //        decoder = MediaCodec.createByCodecName("OMX.google.h264.decoder")
        decoder = when (MediaProjectionService.VIDEO_ENCODE_TYPE) {
            ScreenRecordMediaCodecStrategy.EncodeType.H264 -> {
                // val sps = byteArrayOf(0, 0, 0, 1, 103, 66, -64, 51, -115, 104, 8, -127, -25, -66, 1, -31, 16, -115, 64)
                // val pps = byteArrayOf(0, 0, 0, 1, 104, -50, 1, -88, 53, -56)
                format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
                format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))

                if (CodecUtil.hasCodecByName(MediaFormat.MIMETYPE_VIDEO_AVC, "c2.android.avc.decoder", encoder = false)) {
                    LogContext.log.w(ITAG, "Use decoder: c2.android.avc.decoder")
                    // c2.android.avc.decoder       latency: 170ms(130ms ~ 200ms)
                    // OMX.google.h264.decoder      latency: 170ms(150ms ~ 270ms)
                    MediaCodec.createByCodecName("c2.android.avc.decoder")
                } else {
                    // Hardware decoder             latency: 100ms(60ms ~ 120ms)
                    MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                }
            }
            ScreenRecordMediaCodecStrategy.EncodeType.H265 -> {
                val csd0 = vps!! + sps + pps
                format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))

                if (CodecUtil.hasCodecByName(MediaFormat.MIMETYPE_VIDEO_HEVC, "c2.android.hevc.decoder", encoder = false)) {
                    LogContext.log.w(ITAG, "Use decoder: c2.android.hevc.decoder")
                    // c2.android.hevc.decoder       latency: 60ms(0ms ~ 70ms)
                    MediaCodec.createByCodecName("c2.android.hevc.decoder")
                } else {
                    // Hardware decoder             latency: 100ms
                    MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                }
            }
        }

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

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / 120

    // ============================================
    private var webSocketClient: WebSocketClient? = null
    private var webSocketClientHandler: WebSocketClientHandler? = null

    class WebSocketClient(
        webSocketUri: URI,
        connectionListener: ClientConnectListener<BaseNettyClient>,
        trustAllServers: Boolean,
        retryStrategy: RetryStrategy
    ) :
        BaseNettyClient(webSocketUri, connectionListener, trustAllServers, retryStrategy) {
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

            return netty.executeCommand(command, "Acquire I Frame", "TriggerIFrame")
        }

        fun sendDeviceScreenInfoToServer(point: Size): Boolean {
            val paintArray = CmdBean(CMD_DEVICE_SCREEN_INFO, null, point, null).toJsonString().encodeToByteArray()

            val cId = CMD_DEVICE_SCREEN_INFO.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand(command, "Send screen info to server", "ScreenInfo")
        }

        fun sendDragData(x: Float, y: Float, dstX: Float, dstY: Float, duration: Long): Boolean {
            val touchBean = TouchBean(TouchType.DRAG, x, y, dstX, dstY, duration)
            val touchArray = CmdBean(ScreenShareMasterActivity.CMD_TOUCH_DRAG, null, null, touchBean).toJsonString().encodeToByteArray()
            val cId = ScreenShareMasterActivity.CMD_TOUCH_DRAG.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + touchArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, touchArray)

            return netty.executeCommand(command, "Touch[${touchBean.touchType}]", "WebSocketTouchDragCmd", false)
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

            return netty.executeCommand(command, "Touch[${type.name}]", "WebSocketTouchCmd", false)
        }

        fun sendPaintData(type: TouchType, x: Float, y: Float, paint: Paint): Boolean {
            val paintBean = PaintBean(type, x, y, paint.color, paint.style, paint.strokeWidth)
            val paintArray = CmdBean(CMD_PAINT_EVENT, paintBean, null, null).toJsonString().encodeToByteArray()

            val cId = CMD_PAINT_EVENT.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand(command, "Paint[${type.name}]", "WebSocketCmd", false)
        }

        fun clearCanvas(): Boolean {
            val paintBean = PaintBean(TouchType.CLEAR)
            val paintArray = CmdBean(CMD_PAINT_EVENT, paintBean, null, null).toJsonString().encodeToByteArray()

            val cId = CMD_PAINT_EVENT.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand(command, "Clear canvas", "WebSocketCmd")
        }

        fun undoDraw(): Boolean {
            val paintBean = PaintBean(TouchType.UNDO)
            val paintArray = CmdBean(CMD_PAINT_EVENT, paintBean, null, null).toJsonString().encodeToByteArray()

            val cId = CMD_PAINT_EVENT.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()
            val contentLen = (cId.size + protoVer.size + paintArray.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, paintArray)

            return netty.executeCommand(command, "Undo", "WebSocketCmd")
        }

        override fun release() {
        }
    }

    @Keep
    data class CmdBean(val cmdId: Int, val paintBean: PaintBean?, val deviceInfo: Size?, val touchBean: TouchBean?)

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
                webSocketClientHandler?.sendDeviceScreenInfoToServer(screenRealResolution)
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
                        LogContext.log.w(ITAG, "csd=${dataArray.toHexStringLE()}")
                        val vps: ByteArray?
                        val sps: ByteArray?
                        val pps: ByteArray?
                        when (MediaProjectionService.VIDEO_ENCODE_TYPE) {
                            ScreenRecordMediaCodecStrategy.EncodeType.H264 -> {
                                vps = null
                                sps = H264Util.getSps(dataArray)
                                pps = H264Util.getPps(dataArray)
                            }
                            ScreenRecordMediaCodecStrategy.EncodeType.H265 -> {
                                vps = H265Util.getVps(dataArray)
                                sps = H265Util.getSps(dataArray)
                                pps = H265Util.getPps(dataArray)
                            }
                        }
                        LogContext.log.w(
                            ITAG,
                            "initDecoder with vps=" +
                                "${vps?.toHexStringLE()} sps=${sps?.toHexStringLE()} pps=${pps?.toHexStringLE()}"
                        )
                        if (sps != null && pps != null) {
                            initDecoder(vps, sps, pps)
                            webSocketClientHandler?.triggerIFrame()
                            return
                        } else {
                            toast("Get SPS/PPS error.", longDuration = true, error = true)
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

            override fun onDisconnected(netty: BaseNettyClient, byRemote: Boolean) {
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
        webSocketClient = WebSocketClient(
            url,
            connectionListener,
            true,
            ConstantRetry(10, 2000)
        ).also {
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (binding.finger.inEditMode) {
            return super.dispatchTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownRawX = event.rawX
                touchDownRawY = event.rawY
                touchDownStartTime = SystemClock.currentThreadTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> Unit
            MotionEvent.ACTION_UP -> {
                touchUpRawX = event.rawX
                touchUpRawY = event.rawY
                touchUpStartTime = SystemClock.currentThreadTimeMillis()

                if (abs(touchUpRawX - touchDownRawX) <= CLICK_THRESHOLD && abs(touchDownRawY - touchUpRawY) <= CLICK_THRESHOLD) {
                    LogContext.log.w("Accept as click")
                    webSocketClientHandler?.sendTouchData(TouchType.DOWN, touchUpRawX, touchDownRawY)
                    return super.dispatchTouchEvent(event)
                }
            }
        }

        LogContext.log.w(
            "Drag from ($touchDownRawX x $touchDownRawY) to ($touchUpRawX x $touchUpRawY) " +
                "duration=${touchUpStartTime - touchDownStartTime}ms"
        )
        webSocketClientHandler?.sendDragData(
            touchDownRawX, touchDownRawY, touchUpRawX, touchUpRawY,
            touchUpStartTime - touchDownStartTime
        )
        return super.dispatchTouchEvent(event)
    }
}
