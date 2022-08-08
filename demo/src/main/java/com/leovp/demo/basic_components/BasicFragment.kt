package com.leovp.demo.basic_components

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.androidbase.exts.kotlin.sleep
import com.leovp.androidbase.framework.BaseFragment
import com.leovp.demo.ColorBaseAdapter
import com.leovp.demo.R
import com.leovp.demo.basic_components.examples.*
import com.leovp.demo.basic_components.examples.accessibility.AccessibilityActivity
import com.leovp.demo.basic_components.examples.adb.AdbCommunication
import com.leovp.demo.basic_components.examples.aidl.AidlActivity
import com.leovp.demo.basic_components.examples.animation.AnimationActivity
import com.leovp.demo.basic_components.examples.audio.ADPCMActivity
import com.leovp.demo.basic_components.examples.audio.AudioActivity
import com.leovp.demo.basic_components.examples.bluetooth.BluetoothActivity
import com.leovp.demo.basic_components.examples.camera2.Camera2LiveActivity
import com.leovp.demo.basic_components.examples.camera2.Camera2WithoutPreviewActivity
import com.leovp.demo.basic_components.examples.cipher.AudioCipherActivity
import com.leovp.demo.basic_components.examples.ffmpeg.FFMpegH264Activity
import com.leovp.demo.basic_components.examples.ffmpeg.FFMpegH265Activity
import com.leovp.demo.basic_components.examples.koin.KoinActivity
import com.leovp.demo.basic_components.examples.log.LogActivity
import com.leovp.demo.basic_components.examples.media_player.PlayH265VideoByMediaCodecActivity
import com.leovp.demo.basic_components.examples.media_player.PlayRawH265ByMediaCodecActivity
import com.leovp.demo.basic_components.examples.opengl.OpenGLES20Activity
import com.leovp.demo.basic_components.examples.orientation.OrientationActivity
import com.leovp.demo.basic_components.examples.pref.PrefActivity
import com.leovp.demo.basic_components.examples.provider.ProviderActivity
import com.leovp.demo.basic_components.examples.sharescreen.client.ScreenShareClientActivity
import com.leovp.demo.basic_components.examples.sharescreen.master.ScreenShareMasterActivity
import com.leovp.demo.basic_components.examples.socket.SocketClientActivity
import com.leovp.demo.basic_components.examples.socket.SocketServerActivity
import com.leovp.demo.basic_components.examples.socket.eventbus_bridge.EventBusBridgeClientActivity
import com.leovp.demo.basic_components.examples.socket.websocket.WebSocketClientActivity
import com.leovp.demo.basic_components.examples.socket.websocket.WebSocketServerActivity
import com.leovp.demo.basic_components.examples.wifi.WifiActivity
import com.leovp.demo.databinding.FragmentBasicBinding
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import kotlin.concurrent.thread

class BasicFragment : BaseFragment<FragmentBasicBinding>(R.layout.fragment_basic) {

    override fun getTagName() = ITAG

    override fun getViewBinding(inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): FragmentBasicBinding {
        return FragmentBasicBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val featureList = arrayOf(
            Pair("ScreenShare\nMaster side", ScreenShareMasterActivity::class.java),
            Pair("ScreenShare\nClient side", ScreenShareClientActivity::class.java),
            Pair("Socket Server", SocketServerActivity::class.java),
            Pair("Socket Client", SocketClientActivity::class.java),
            Pair("WebSocket Server", WebSocketServerActivity::class.java),
            Pair("WebSocket Client", WebSocketClientActivity::class.java),
            Pair("Play H265 Video File by MediaCodec",
                PlayH265VideoByMediaCodecActivity::class.java),
            Pair("Play Raw H265 by MediaCodec", PlayRawH265ByMediaCodecActivity::class.java),
            Pair("FFMPEG Raw H264", FFMpegH264Activity::class.java),
            Pair("FFMPEG Raw H265", FFMpegH265Activity::class.java),
            Pair("Camera2Live", Camera2LiveActivity::class.java),
            Pair("Camera2 No Preview", Camera2WithoutPreviewActivity::class.java),
            Pair("OpenGL ES 2.0", OpenGLES20Activity::class.java),
            Pair("Eventbus-bridge WebSocket Client", EventBusBridgeClientActivity::class.java),
            Pair("Device Info", DeviceInfoActivity::class.java),
            Pair("TakeScreenshot", TakeScreenshotActivity::class.java),
            Pair("Record Single App Screen", RecordSingleAppScreenActivity::class.java),
            Pair("Network Monitor", NetworkMonitorActivity::class.java),
            Pair("Audio", AudioActivity::class.java),
            Pair("ADPCM", ADPCMActivity::class.java),
            Pair("Audio Cipher", AudioCipherActivity::class.java),
            Pair("Coroutine", CoroutineActivity::class.java),
            Pair("HTTP Related", HttpActivity::class.java),
            Pair("HTTP Net", NetActivity::class.java),
            Pair("Log", LogActivity::class.java),
            Pair("Clipboard", ClipboardActivity::class.java),
            Pair("SaveInstanceState", SaveInstanceStateActivity::class.java),
            Pair("KeepAlive", KeepAliveActivity::class.java),
            Pair("Finger Paint", FingerPaintActivity::class.java),
            Pair("Watermark", WatermarkActivity::class.java),
            Pair("Preference", PrefActivity::class.java),
            Pair("Java Mail", JavaMailActivity::class.java),
            Pair("Bluetooth", BluetoothActivity::class.java),
            Pair("Wifi", WifiActivity::class.java),
            Pair("Accessibility", AccessibilityActivity::class.java),
            Pair("AIDL", AidlActivity::class.java),
            Pair("Provider", ProviderActivity::class.java),
            Pair("Animation", AnimationActivity::class.java),
            Pair(getString(R.string.act_title_change_app_lang),
                ChangeAppLanguageActivity::class.java),
            Pair("Toast", ToastActivity::class.java),
            Pair("FloatView", FloatViewActivity::class.java),
            Pair("CircleProgressBar", CircleProgressbarActivity::class.java),
            Pair("App Settings", AppSettingsActivity::class.java),
            Pair("Orientation", OrientationActivity::class.java),
            Pair("Koin", KoinActivity::class.java),
            Pair("Adb Communication", AdbCommunication::class.java),
            Pair("Bitmap Native Util", BitmapNativeActivity::class.java),
        )

        val colorBaseAdapter = ColorBaseAdapter(featureList.map { it.first }, colors)
        colorBaseAdapter.onItemClickListener = object : ColorBaseAdapter.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                startActivity(featureList[position].second,
                    { intent -> intent.putExtra("title", featureList[position].first) })
            }
        }
        view.findViewById<SwipeRefreshLayout>(R.id.refreshLayout)?.let {
            it.setOnRefreshListener {
                thread {
                    sleep(2000)
                    Handler(Looper.getMainLooper()).post { it.isRefreshing = false }
                }
            }
        }
        view.findViewById<RecyclerView>(R.id.recyclerView).run {
            setHasFixedSize(true)
            // layoutManager = LinearLayoutManager(requireActivity())
            adapter = colorBaseAdapter
        }
    }

    override fun onDestroy() {
        // CustomApplication.instance.closeDebugOutputFile()
        LogContext.log.i(ITAG, "onDestroy()")
        super.onDestroy()
        // In some cases, if you use saved some parameters in Application, when app exits,
        // the parameters may not be released. So we need to call AppUtil.exitApp(ctx)
        // AppUtil.exitApp(this)
    }

    companion object {
        val colors = arrayOf(
            Color.parseColor("#80CBC4"),
            Color.parseColor("#80DEEA"),
            Color.parseColor("#81D4FA"),
            Color.parseColor("#90CAF9"),
            Color.parseColor("#9FA8DA"),
            Color.parseColor("#A5D6A7"),
            Color.parseColor("#B0BEC5"),
            Color.parseColor("#B39DDB"),
            Color.parseColor("#BCAAA4"),
            Color.parseColor("#C5E1A5"),
            Color.parseColor("#CE93D8"),
            Color.parseColor("#E6EE9C"),
            Color.parseColor("#EF9A9A"),
            Color.parseColor("#F48FB1"),
            Color.parseColor("#FFAB91"),
            Color.parseColor("#FFCC80"),
            Color.parseColor("#FFE082"),
            Color.parseColor("#FFF59D")
        )
    }
}