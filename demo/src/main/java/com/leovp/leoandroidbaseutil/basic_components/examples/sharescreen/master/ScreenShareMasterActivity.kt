package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.media.MediaCodecInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.annotation.Keep
import com.leovp.androidbase.exts.*
import com.leovp.androidbase.exts.android.*
import com.leovp.androidbase.exts.kotlin.*
import com.leovp.androidbase.ui.FloatViewManager
import com.leovp.androidbase.utils.ByteUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.system.AccessibilityUtil
import com.leovp.drawonscreen.FingerPaintView
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.client.ScreenShareClientActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityScreenShareMasterBinding
import com.leovp.screenshot.ScreenCapture
import com.leovp.screenshot.ScreenCapture.BY_IMAGE_2_H264
import com.leovp.socket_sdk.framework.base.decoder.CustomSocketByteStreamDecoder
import com.leovp.socket_sdk.framework.server.BaseNettyServer
import com.leovp.socket_sdk.framework.server.BaseServerChannelInboundHandler
import com.leovp.socket_sdk.framework.server.ServerConnectListener
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus


class ScreenShareMasterActivity : BaseDemonstrationActivity() {
    companion object {
        const val CMD_GRAPHIC_CSD: Int = 1
        const val CMD_GRAPHIC_DATA: Int = 2
        const val CMD_PAINT_EVENT: Int = 3
        const val CMD_DEVICE_SCREEN_INFO: Int = 4
        const val CMD_TRIGGER_I_FRAME: Int = 5

        const val CMD_TOUCH_EVENT: Int = 6
        const val CMD_TOUCH_HOME: Int = 7
        const val CMD_TOUCH_BACK: Int = 8
        const val CMD_TOUCH_RECENT: Int = 9
        const val CMD_TOUCH_DRAG: Int = 10

        private const val REQUEST_CODE_DRAW_OVERLAY_PERMISSION = 0x2233
    }

    private lateinit var binding: ActivityScreenShareMasterBinding

    private val cs = CoroutineScope(Dispatchers.IO)

    private var floatCanvas: FloatViewManager? = null

    private var mediaProjectService: MediaProjectionService? = null
    private var serviceIntent: Intent? = null
    private var bound: Boolean = false
    private var serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            LogContext.log.i(ITAG, "===== onServiceConnected($name) =====")
            bound = true
            mediaProjectService = (service as MediaProjectionService.CustomBinder).service.also {
                it.outputH264File = false
                it.screenDataUpdateListener = object : ScreenDataUpdateListener {
                    @SuppressLint("SetTextI18n")
                    override fun onUpdate(data: ByteArray, flags: Int) {
                        if (clientChannel != null) {
                            runOnUiThread { binding.txtInfo.text = "flags=$flags Data length=${data.size}" }
                            runCatching {
                                clientChannel?.let { ch -> webSocketServerHandler.sendVideoData(ch, CMD_GRAPHIC_DATA, data) }
                            }.onFailure { e -> e.printStackTrace() }
                        } // if
                    } // onUpdate
                } // ScreenDataUpdateListener
            } // mediaProjectService also
        } // ServiceConnection

        override fun onServiceDisconnected(name: ComponentName?) {
            LogContext.log.w(ITAG, "===== onServiceDisconnected($name) =====")
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenShareMasterBinding.inflate(layoutInflater).apply { setContentView(root) }

        if (!AccessibilityUtil.isAccessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        serviceIntent = Intent(this, MediaProjectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConn, Service.BIND_AUTO_CREATE)

        binding.toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                ScreenCapture.requestPermission(this@ScreenShareMasterActivity)
            } else {
                stopServer()
                mediaProjectService?.stopScreenShare()
            }
        }

        floatCanvas = FloatViewManager(this, R.layout.component_screen_share_float_canvas, enableDrag = false, enableFullScreen = true)

        if (canDrawOverlays) {
            floatCanvas?.show()
        } else {
            startManageDrawOverlaysPermission()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LogContext.log.i("onConfigurationChanged: ${newConfig.toJsonString()}")
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LogContext.log.i("Running in LANDSCAPE")
        } else {
            LogContext.log.i("Running in PORTRAIT")
        }
    }

    private fun startManageDrawOverlaysPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${applicationContext.packageName}")
            ).let {
                startActivityForResult(it, REQUEST_CODE_DRAW_OVERLAY_PERMISSION)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_DRAW_OVERLAY_PERMISSION -> {
                if (canDrawOverlays) floatCanvas?.show() else toast("Permission[ACTION_MANAGE_OVERLAY_PERMISSION] is not granted!")
                return
            }
        }
        ScreenCapture.onActivityResult(requestCode, resultCode, data, object : ScreenCapture.ScreenCaptureListener {
            override fun requestResult(result: Int, resultCode: Int, data: Intent?) {
                when (result) {
                    ScreenCapture.SCREEN_CAPTURE_RESULT_GRANT -> {
                        LogContext.log.w(ITAG, "Prepare to record...")
                        startServer()
                        checkNotNull(mediaProjectService) { "mediaProjectService can not be null!" }
                        mediaProjectService?.setData(
                            resultCode,
                            data ?: exception("Intent data is null. Can not capture screen!")
                        )
                        val screenInfo = getAvailableResolution()
                        val setting = ScreenShareSetting(
                            (screenInfo.x * 0.5F / 16).toInt() * 16,
                            (screenInfo.y * 0.5F / 16).toInt() * 16,
                            densityDpi
                        )
//                        setting.fps = 20F
//                        setting.bitrate = setting.width * setting.height
                        // !!! Attention !!!
                        // In XiaoMi 10(Android 10), If you set BITRATE_MODE_CQ, the MediaCodec configure will be crashed.
//                        setting.bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
//                        setting.keyFrameRate = 2
//                        setting.iFrameInterval = 3
                        mediaProjectService?.startScreenShare(setting)
                    }
                    ScreenCapture.SCREEN_CAPTURE_RESULT_DENY -> {
                        LogContext.log.w(ITAG, "Permission denied!")
                        toast("Permission denied!", error = true)
                    }
                    else -> LogContext.log.d(ITAG, "Not screen capture request")
                }
            }
        })
    }

    override fun onDestroy() {
        LogContext.log.w(ITAG, "onDestroy(bound=$bound)")
        stopServer()
        mediaProjectService?.onReleaseScreenShare()
        if (bound) {
            unbindService(serviceConn)
            serviceIntent?.let { stopService(it) }
        }
        super.onDestroy()
    }

    // =========================================

    private lateinit var webSocketServer: WebSocketServer
    private lateinit var webSocketServerHandler: WebSocketServerHandler

    private var clientChannel: Channel? = null

    class WebSocketServer(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) : BaseNettyServer(port, connectionListener, true) {
        override fun addLastToPipeline(pipeline: ChannelPipeline) {
            pipeline.addLast("messageDecoder", CustomSocketByteStreamDecoder())
        }
    }

    @ChannelHandler.Sharable
    class WebSocketServerHandler(private val netty: BaseNettyServer) : BaseServerChannelInboundHandler<Any>(netty) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
//            val receivedString: String?
//            val frame = msg as WebSocketFrame
//            receivedString = when (frame) {
//                is TextWebSocketFrame -> frame.text()
//                is PongWebSocketFrame -> frame.content().toString(Charset.forName("UTF-8"))
//                else -> null
//            }
//            netty.connectionListener.onReceivedData(netty, ctx.channel(), receivedString)

            val receivedByteBuf = (msg as WebSocketFrame).content().retain()
            // Data Length
            receivedByteBuf.readIntLE()
            // Command ID
            receivedByteBuf.readByte()
            // Protocol version
            receivedByteBuf.readByte()

            runCatching {
                val bodyBytes = ByteArray(receivedByteBuf.readableBytes())
                receivedByteBuf.getBytes(6, bodyBytes)
                receivedByteBuf.release()

                netty.connectionListener.onReceivedData(netty, ctx.channel(), bodyBytes.decodeToString())
            }
        }

        fun sendVideoData(clientChannel: Channel, cmdId: Int, data: ByteArray): Boolean {
            val cId = cmdId.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()

            val contentLen = (cId.size + protoVer.size + data.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, data)

            return netty.executeCommand(clientChannel, "$cId", "sendVideoData", command, showContent = false)
        }

        override fun release() {
        }
    }

    private val connectionListener = object : ServerConnectListener<BaseNettyServer> {
        override fun onStarted(netty: BaseNettyServer) {
            LogContext.log.i(ITAG, "onStarted")
        }

        override fun onStopped() {
            LogContext.log.i(ITAG, "onStop")
        }

        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.w(ITAG, "onClientConnected: ${clientChannel.remoteAddress()}")
            cs.launch {
                webSocketServerHandler.sendVideoData(clientChannel, CMD_GRAPHIC_CSD, mediaProjectService?.spsPps!!)
                mediaProjectService?.triggerIFrame()
                this@ScreenShareMasterActivity.clientChannel = clientChannel
            }
        }

        var clientScreenInfo: Point? = null
        var userPath: MutableList<Pair<Path, Paint>> = mutableListOf()
        val currentScreen = getRealResolution()
        override fun onReceivedData(netty: BaseNettyServer, clientChannel: Channel, data: Any?, action: Int) {
            LogContext.log.i(ITAG, "onReceivedData from ${clientChannel.remoteAddress()}: $data")
            cs.launch {
                val stringData = data as String
                val cmdBean = stringData.toObject(ScreenShareClientActivity.CmdBean::class.java)!!
                when (cmdBean.cmdId) {
                    CMD_TOUCH_EVENT -> {
                        val touchBean = cmdBean.touchBean!!
                        touchBean.x = currentScreen.x / clientScreenInfo!!.x.toFloat() * touchBean.x
                        touchBean.y = currentScreen.y / clientScreenInfo!!.y.toFloat() * touchBean.y
                        EventBus.getDefault().post(touchBean)
                    }
                    CMD_TOUCH_DRAG -> EventBus.getDefault().post(cmdBean.touchBean!!)
                    CMD_TOUCH_BACK -> AccessibilityUtil.clickBackKey()
                    CMD_TOUCH_HOME -> AccessibilityUtil.clickHomeKey()
                    CMD_TOUCH_RECENT -> AccessibilityUtil.clickRecentKey()
                    CMD_TRIGGER_I_FRAME -> mediaProjectService?.triggerIFrame()
                    CMD_DEVICE_SCREEN_INFO -> clientScreenInfo = cmdBean.deviceInfo
                    CMD_PAINT_EVENT -> {
                        val paintBean = cmdBean.paintBean!!
                        val pathPaint = Paint().apply {
                            isAntiAlias = true
                            isDither = true
                            color = paintBean.paintColor
                            style = paintBean.paintStyle
                            strokeJoin = Paint.Join.ROUND
                            strokeCap = Paint.Cap.ROUND
                            strokeWidth = paintBean.strokeWidth
                        }

                        withContext(Dispatchers.Main) {
                            val fingerPaintView = floatCanvas?.floatView?.findViewById(R.id.finger) as? FingerPaintView
                            val calX = currentScreen.x / clientScreenInfo!!.x.toFloat() * paintBean.x
                            val calY = currentScreen.y / clientScreenInfo!!.y.toFloat() * paintBean.y
                            when (paintBean.touchType) {
                                ScreenShareClientActivity.TouchType.DOWN -> userPath.add(Path().also {
                                    it.moveTo(calX, calY)
                                } to Paint(pathPaint))
                                ScreenShareClientActivity.TouchType.MOVE -> userPath.lastOrNull()?.first?.lineTo(calX, calY)
                                ScreenShareClientActivity.TouchType.UP -> userPath.lastOrNull()?.first?.lineTo(calX, calY)
                                ScreenShareClientActivity.TouchType.CLEAR -> {
                                    userPath.clear()
                                    fingerPaintView?.clear()
                                    return@withContext
                                }
                                ScreenShareClientActivity.TouchType.UNDO -> {
                                    fingerPaintView?.undo()
                                    userPath = fingerPaintView?.getPaths()!!
                                    return@withContext
                                }
                                else -> throw IllegalArgumentException("Unknown touch type[${paintBean.touchType}]")
                            }
                            fingerPaintView?.drawUserPath(userPath)
                        }
                    }
                }
            }
        }

        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.w(ITAG, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            lostClientDisconnection()
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LogContext.log.w(ITAG, "onFailed code: $code message: $msg")
            lostClientDisconnection()
        }

        private fun lostClientDisconnection() {
            this@ScreenShareMasterActivity.clientChannel = null
            runOnUiThread { binding.toggleButton.isChecked = false }
            stopServer()
        }
    }

    private fun startServer() {
        floatCanvas?.show()
        cs.launch {
            webSocketServer = WebSocketServer(10086, connectionListener)
            webSocketServerHandler = WebSocketServerHandler(webSocketServer)
            webSocketServer.initHandler(webSocketServerHandler)
            webSocketServer.startServer()
        }
    }

    private fun stopServer() {
        val fingerPaintView = floatCanvas?.floatView?.findViewById(R.id.finger) as? FingerPaintView
        fingerPaintView?.clear()
        floatCanvas?.dismiss()
        clientChannel = null
        cs.launch { if (::webSocketServer.isInitialized) webSocketServer.stopServer() }
    }
}

@Keep
data class ScreenShareSetting(val width: Int, val height: Int, val dpi: Int) {
    var fps: Float = 15F
    var bitrate = (width * height * 1.8f).toInt()
    var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
    var keyFrameRate = 8
    var iFrameInterval = 4

    /** Only used in [BY_IMAGE_2_H264] mode */
    @Suppress("unused")
    var sampleSize: Int = 1
}