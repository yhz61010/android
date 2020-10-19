package com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaCodecInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.Keep
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.exts.exception
import com.leovp.androidbase.utils.device.DeviceUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.screenshot.ScreenCapture
import kotlinx.android.synthetic.main.activity_screen_share_master.*
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class ScreenShareMasterActivity : BaseDemonstrationActivity() {

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
                    override fun onUpdate(data: Any) {
                        val dataArray = data as ByteArray
                        webSocket?.let { ws ->
                            if (ws.isClosed || ws.isClosing) {
                                LogContext.log.i(ITAG, "WebSocket is not available. Do nothing.")
                                return
                            }
                            LogContext.log.i(ITAG, "Data length=${dataArray.size}")
                            runOnUiThread {
                                txtInfo.text = "Data length=${dataArray.size}"
                            }
                            ws.send(dataArray)
                        } // webSocket let
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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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
        mediaProjectService?.onReleaseScreenShare()
        if (bound) {
            unbindService(serviceConn)
            serviceIntent?.let { stopService(it) }
        }
        super.onDestroy()
    }

    // =========================================

    private var webSocketServer: ScreenShareWebSocketServer? = null
    private var webSocket: WebSocket? = null

    inner class ScreenShareWebSocketServer(port: Int) :
        WebSocketServer(InetSocketAddress(port)) {
        override fun onOpen(webSocket: WebSocket?, clientHandshake: ClientHandshake?) {
            LogContext.log.w(ITAG, "onOpen")
            this@ScreenShareMasterActivity.webSocket = webSocket

            webSocket?.send(mediaProjectService?.spsPps)
            mediaProjectService?.triggerIFrame()
        }

        override fun onClose(webSocket: WebSocket?, i: Int, s: String?, b: Boolean) {
            LogContext.log.w(ITAG, "onClose")
            runOnUiThread { toggleButton.isChecked = false }
        }

        override fun onMessage(webSocket: WebSocket?, s: String?) {
            LogContext.log.i(ITAG, "onMessage")
        }

        override fun onError(webSocket: WebSocket?, e: Exception?) {
            e?.printStackTrace()
            LogContext.log.e(ITAG, "onError")
        }
    }

    private fun startServer() {
        webSocketServer = ScreenShareWebSocketServer(10086)
        webSocketServer?.start()
    }

    private fun stopServer() {
        webSocket?.close()
        webSocket = null
        webSocketServer?.stop()
        webSocketServer = null
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