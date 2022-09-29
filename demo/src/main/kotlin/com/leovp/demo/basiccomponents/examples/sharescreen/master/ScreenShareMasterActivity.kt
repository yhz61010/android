package com.leovp.demo.basiccomponents.examples.sharescreen.master

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaCodecInfo
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Size
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import com.leovp.android.exts.canDrawOverlays
import com.leovp.android.exts.densityDpi
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.android.exts.screenRealHeight
import com.leovp.android.exts.screenRealResolution
import com.leovp.android.exts.screenSurfaceRotation
import com.leovp.android.exts.screenWidth
import com.leovp.android.exts.toast
import com.leovp.android.utils.NetworkUtil
import com.leovp.androidbase.utils.ByteUtil
import com.leovp.androidbase.utils.system.AccessibilityUtil
import com.leovp.basenetty.framework.base.decoder.CustomSocketByteStreamDecoder
import com.leovp.basenetty.framework.server.BaseNettyServer
import com.leovp.basenetty.framework.server.BaseServerChannelInboundHandler
import com.leovp.basenetty.framework.server.ServerConnectListener
import com.leovp.bytes.asByteAndForceToBytes
import com.leovp.bytes.toBytesLE
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.sharescreen.client.ScreenShareClientActivity
import com.leovp.demo.databinding.ActivityScreenShareMasterBinding
import com.leovp.drawonscreen.FingerPaintView
import com.leovp.floatview.FloatView
import com.leovp.json.toJsonString
import com.leovp.json.toObject
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.screencapture.screenrecord.ScreenCapture.BY_IMAGE_2_H26x
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordMediaCodecStrategy
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

class ScreenShareMasterActivity : BaseDemonstrationActivity<ActivityScreenShareMasterBinding>() {
    override fun getTagName(): String = ITAG

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
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityScreenShareMasterBinding {
        return ActivityScreenShareMasterBinding.inflate(layoutInflater)
    }

    private var fingerPaintView: FingerPaintView? = null

    private val cs = CoroutineScope(Dispatchers.IO)

    private var lastScreenRotation = 0
    private var testTimer: Timer? = null
    private var testTimerTask: TimerTask? = null

    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var currentRealResolution: Size
    private var mediaProjectService: MediaProjectionService? = null
    private var serviceIntent: Intent? = null
    private var bound: Boolean = false
    private var serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            LogContext.log.i(ITAG, "===== onServiceConnected($name) =====")
            bound = true
            mediaProjectService = (service as MediaProjectionService.CustomBinder).service.also {
                it.outputH26xFile = true
                it.screenDataUpdateListener = object : ScreenDataUpdateListener {
                    @SuppressLint("SetTextI18n")
                    override fun onUpdate(data: ByteArray, flags: Int, presentationTimeUs: Long) {
                        LogContext.log.d("onUpdate[${data.size}] flags=$flags presentationTimeUs=$presentationTimeUs")
                        if (clientChannel != null) {
                            runOnUiThread {
                                binding.txtInfo.text =
                                    "flags=$flags Data length=${data.size} presentationTimeUs=$presentationTimeUs"
                            }
                            runCatching {
                                clientChannel?.let { ch ->
                                    webSocketServerHandler.sendVideoData(ch, CMD_GRAPHIC_DATA, data)
                                }
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
        currentRealResolution = screenRealResolution
        if (!AccessibilityUtil.isAccessibilityEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    LogContext.log.w(ITAG, "Prepare to record...")
                    startServer()
                    checkNotNull(mediaProjectService) { "mediaProjectService can not be null!" }
                    mediaProjectService?.setData(
                        result.resultCode,
                        result.data
                            ?: error("Intent data is null. Can not capture screen!")
                    )
                    val screenInfo = screenAvailableResolution
                    val setting = ScreenShareSetting(
                        // Round the value to the nearest multiple of 16.
                        (screenInfo.width * 0.8F + 8).toInt() and 0xF.inv(),
                        // Round the value to the nearest multiple of 16.
                        (screenInfo.height * 0.8F + 8).toInt() and 0xF.inv(), densityDpi
                    )
                    setting.fps = 30F
                    setting.bitrate = screenInfo.width * screenInfo.height * 2
                    // setting.bitrate = setting.width * setting.height
                    // !!! Attention !!!
                    // In XiaoMi 10(Android 10), If you set BITRATE_MODE_CQ, the MediaCodec configure will be crashed.
                    // setting.bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    // setting.keyFrameRate = 2
                    // setting.iFrameInterval = 3
                    mediaProjectService?.startScreenShare(setting)
                } else {
                    LogContext.log.w(ITAG, "Permission denied!")
                    toast("Permission denied!", error = true)
                }
            }

        binding.txtInfo.text = NetworkUtil.getIp()[0]

        serviceIntent = Intent(this, MediaProjectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConn, Service.BIND_AUTO_CREATE)

        binding.toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val mediaProjectionManager =
                    getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                activityResultLauncher.launch(captureIntent)
            } else {
                FloatView.removeAll()
                stopServer()
                mediaProjectService?.stopScreenShare()
            }
        }

        if (canDrawOverlays) {
            createFloatView()
        } else {
            startManageDrawOverlaysPermission()
        }

        testTimerTask = object : TimerTask() {
            @SuppressLint("NewApi")
            override fun run() {
                runCatching {
                    val currentScreenRotation = screenSurfaceRotation
                    //                    LogContext.log.e("currentScreenRotation=$currentScreenRotation lastScreenRotation=$lastScreenRotation")
                    if (currentScreenRotation != lastScreenRotation) {
                        lastScreenRotation = currentScreenRotation
                        runOnUiThread { (mediaProjectService?.screenProcessor as? ScreenRecordMediaCodecStrategy)?.changeOrientation() }
                    }
                }
            }
        }
        testTimer = Timer(true).apply { schedule(testTimerTask, 1000, 1000) }
    }

    private fun createFloatView() {
        FloatView.with(this)
            .meta { _, _ ->
                // We must set this value to false. Check that method comment.
                touchable = false
                enableDrag = false
                fullScreenFloatView = true
            }
            .layout(R.layout.component_screen_share_float_canvas) { v ->
                fingerPaintView = v.findViewById(R.id.finger) as? FingerPaintView
            }.build()
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
                simpleActivityLauncher.launch(it) {
                    if (canDrawOverlays) {
                        if (FloatView.default().exist()) {
                            FloatView.removeAll()
                            createFloatView()
                        }
                    } else {
                        toast("Permission[ACTION_MANAGE_OVERLAY_PERMISSION] is not granted!")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        LogContext.log.w(ITAG, "onDestroy(bound=$bound)")
        FloatView.removeAll()
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

    class WebSocketServer(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) :
        BaseNettyServer(
            port,
            connectionListener,
            true
        ) {
        override fun getTagName() = "SSMA-WS"

        override fun addLastToPipeline(pipeline: ChannelPipeline) {
            pipeline.addLast("messageDecoder", CustomSocketByteStreamDecoder())
        }
    }

    @ChannelHandler.Sharable
    class WebSocketServerHandler(private val netty: BaseNettyServer) :
        BaseServerChannelInboundHandler<Any>(netty) {
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

                netty.connectionListener.onReceivedData(
                    netty,
                    ctx.channel(),
                    bodyBytes.decodeToString()
                )
            }
        }

        fun sendVideoData(clientChannel: Channel, cmdId: Int, data: ByteArray): Boolean {
            val cId = cmdId.asByteAndForceToBytes()
            val protoVer = 1.asByteAndForceToBytes()

            val contentLen = (cId.size + protoVer.size + data.size).toBytesLE()
            val command = ByteUtil.mergeBytes(contentLen, cId, protoVer, data)

            return netty.executeCommand(
                clientChannel,
                command,
                "sendVideoData",
                "$cmdId",
                showContent = false
            )
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
                webSocketServerHandler.sendVideoData(
                    clientChannel,
                    CMD_GRAPHIC_CSD,
                    mediaProjectService?.vpsSpsPps!!
                )
                mediaProjectService?.triggerIFrame()
                this@ScreenShareMasterActivity.clientChannel = clientChannel
            }
        }

        var clientScreenInfo: Size? = null
        var userPath: MutableList<Pair<Path, Paint>> = mutableListOf()
        override fun onReceivedData(
            netty: BaseNettyServer,
            clientChannel: Channel,
            data: Any?,
            action: Int
        ) {
            LogContext.log.i(ITAG, "onReceivedData from ${clientChannel.remoteAddress()}: $data")
            cs.launch {
                val stringData = data as String
                val cmdBean: ScreenShareClientActivity.CmdBean = stringData.toObject()!!
                when (cmdBean.cmdId) {
                    CMD_TOUCH_EVENT -> {
                        val touchBean = cmdBean.touchBean!!
                        touchBean.x =
                            currentRealResolution.width / clientScreenInfo!!.width.toFloat() * touchBean.x
                        touchBean.y =
                            currentRealResolution.height / clientScreenInfo!!.height.toFloat() * touchBean.y
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
                            val calX =
                                currentRealResolution.width / clientScreenInfo!!.width.toFloat() * paintBean.x
                            val calY =
                                currentRealResolution.height / clientScreenInfo!!.height.toFloat() * paintBean.y
                            when (paintBean.touchType) {
                                ScreenShareClientActivity.TouchType.DOWN -> userPath.add(
                                    Path().also {
                                        it.moveTo(calX, calY)
                                    } to Paint(pathPaint)
                                )
                                ScreenShareClientActivity.TouchType.MOVE -> userPath.lastOrNull()?.first?.lineTo(
                                    calX,
                                    calY
                                )
                                ScreenShareClientActivity.TouchType.UP -> userPath.lastOrNull()?.first?.lineTo(
                                    calX,
                                    calY
                                )
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
                                else -> throw IllegalArgumentException(
                                    "Unknown touch type[${paintBean.touchType}]"
                                )
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
            runOnUiThread {
                binding.toggleButton.isChecked = false
                FloatView.removeAll()
            }
            stopServer()
        }
    }

    private fun startServer() {
        FloatView.default().show()
        cs.launch {
            webSocketServer = WebSocketServer(10086, connectionListener)
            webSocketServerHandler = WebSocketServerHandler(webSocketServer)
            webSocketServer.initHandler(webSocketServerHandler)
            webSocketServer.startServer()
        }
    }

    private fun stopServer() {
        clientChannel = null
        cs.launch { if (::webSocketServer.isInitialized) webSocketServer.stopServer() }
    }

    fun onScreenshotClick(@Suppress("UNUSED_PARAMETER") view: View) {
        LogContext.log.w("Click Screenshot button.")
        toast("Prepare to take screenshot in 3s...")
        Handler(Looper.getMainLooper()).postDelayed({
            mediaProjectService?.takeScreenshot(screenWidth, screenRealHeight)
        }, 3000)
    }
}

@Keep
data class ScreenShareSetting(val width: Int, val height: Int, val dpi: Int) {
    var fps: Float = 20F
    var bitrate = (width * height * 1.8f).toInt()
    var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
    var keyFrameRate = 8
    var iFrameInterval = 4

    /** Only used in [BY_IMAGE_2_H26x] mode */
    @Suppress("unused")
    var sampleSize: Int = 1
}
