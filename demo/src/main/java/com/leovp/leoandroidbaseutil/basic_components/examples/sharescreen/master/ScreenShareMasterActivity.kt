package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
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
import com.leovp.androidbase.ui.FloatViewManager
import com.leovp.androidbase.utils.device.DeviceUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.drawonscreen.FingerPaintView
import com.leovp.leoandroidbaseutil.CustomApplication
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.client.ScreenShareClientActivity
import com.leovp.screenshot.ScreenCapture
import com.leovp.socket_sdk.framework.server.BaseNettyServer
import com.leovp.socket_sdk.framework.server.BaseServerChannelInboundHandler
import com.leovp.socket_sdk.framework.server.ServerConnectListener
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import kotlinx.android.synthetic.main.activity_screen_share_master.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset


class ScreenShareMasterActivity : BaseDemonstrationActivity() {
    companion object {
        const val CMD_GRAPHIC_CSD: Int = 1
        const val CMD_GRAPHIC_DATA: Int = 2
        const val CMD_TOUCH_EVENT: Int = 3
        const val CMD_DEVICE_SCREEN_INFO: Int = 4

        private const val REQUEST_CODE_DRAW_OVERLAY_PERMISSION = 0x2233
    }

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
                    override fun onUpdate(data: Any, flags: Int) {
                        if (clientChannel != null) {
                            val dataArray = data as ByteArray
                            runOnUiThread { txtInfo.text = "flags=$flags Data length=${dataArray.size}" }
                            runCatching {
                                clientChannel?.let { ch -> webSocketServerHandler.sendVideoData(ch, CMD_GRAPHIC_DATA, dataArray) }
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
        setContentView(R.layout.activity_screen_share_master)

        serviceIntent = Intent(this, MediaProjectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConn, Service.BIND_AUTO_CREATE)

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                ScreenCapture.requestPermission(this@ScreenShareMasterActivity)
            } else {
                stopServer()
                mediaProjectService?.stopScreenShare()
            }
        }

        floatCanvas = FloatViewManager(CustomApplication.instance, R.layout.component_screen_share_float_canvas, enableDrag = false, enableFullScreen = true)

        if (canDrawOverlays) {
            floatCanvas?.show()
        } else {
            startManageDrawOverlaysPermission()
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
                if (canDrawOverlays) {
                    floatCanvas?.show()
                } else {
                    toast("Permission[ACTION_MANAGE_OVERLAY_PERMISSION] is not granted!")
                }
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
                        val screenInfo = DeviceUtil.getResolution(this@ScreenShareMasterActivity)
                        val setting = ScreenShareSetting(
                            (screenInfo.x * 0.8F).toInt() / 16 * 16,
                            (screenInfo.y * 0.8F).toInt() / 16 * 16,
                            DeviceUtil.getDensity(this@ScreenShareMasterActivity)
                        )
                        setting.fps = 20F
                        setting.bitrate = setting.width * setting.height
                        // !!! Attention !!!
                        // In XiaoMi 10(Android 10), If you set BITRATE_MODE_CQ, the MediaCodec configure will be crashed.
                        setting.bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                        setting.keyFrameRate = 2
                        setting.iFrameInterval = 3
                        mediaProjectService?.startScreenShare(setting)
                    }
                    ScreenCapture.SCREEN_CAPTURE_RESULT_DENY -> {
                        LogContext.log.w(ITAG, "Permission denied!")
                        ToastUtil.showErrorToast("Permission denied!")
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

    class WebSocketServer(port: Int, connectionListener: ServerConnectListener<BaseNettyServer>) : BaseNettyServer(port, connectionListener, true)

    @ChannelHandler.Sharable
    class WebSocketServerHandler(private val netty: BaseNettyServer) : BaseServerChannelInboundHandler<Any>(netty) {
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) {
            val receivedString: String?
            val frame = msg as WebSocketFrame
            receivedString = when (frame) {
                is TextWebSocketFrame -> frame.text()
                is PongWebSocketFrame -> frame.content().toString(Charset.forName("UTF-8"))
                else -> null
            }
            netty.connectionListener.onReceivedData(netty, ctx.channel(), receivedString)
        }

        fun sendVideoData(clientChannel: Channel, cmdId: Int, data: ByteArray): Boolean {
            return netty.executeCommand(clientChannel, cmdId.toBytesLE() + data, showContent = false)
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
        val currentScreen = DeviceUtil.getResolutionWithVirtualKey(CustomApplication.instance)
        override fun onReceivedData(netty: BaseNettyServer, clientChannel: Channel, data: Any?) {
            LogContext.log.i(ITAG, "onReceivedData from ${clientChannel.remoteAddress()}: $data")
            cs.launch {
                val stringData = data as String
                val cmdBean = stringData.toObject(ScreenShareClientActivity.CmdBean::class.java)!!
                when (cmdBean.cmdId) {
                    CMD_DEVICE_SCREEN_INFO -> clientScreenInfo = cmdBean.deviceInfo
                    CMD_TOUCH_EVENT -> {
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
            runOnUiThread { toggleButton.isChecked = false }
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
    var fps: Float = 20F
    var bitrate = width * height
    var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
    var keyFrameRate = 20
    var iFrameInterval = 3

    @Suppress("unused")
    val bitrateInString
        get() = "${(bitrate / 8F / 1024).toInt()}/KB"

    /**
     * Only used in **ScreenCapture.SCREEN_CAPTURE_TYPE_IMAGE** mode
     */
    @Suppress("unused")
    var sampleSize: Int = 1
}