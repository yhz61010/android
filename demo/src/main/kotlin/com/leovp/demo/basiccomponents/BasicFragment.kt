package com.leovp.demo.basiccomponents

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
import com.leovp.demo.basiccomponents.examples.AidlActivity
import com.leovp.demo.basiccomponents.examples.BitmapNativeActivity
import com.leovp.demo.basiccomponents.examples.ChangeAppLanguageActivity
import com.leovp.demo.basiccomponents.examples.CircleProgressbarActivity
import com.leovp.demo.basiccomponents.examples.ClipboardActivity
import com.leovp.demo.basiccomponents.examples.CoroutineActivity
import com.leovp.demo.basiccomponents.examples.DeviceInfoActivity
import com.leovp.demo.basiccomponents.examples.FingerPaintActivity
import com.leovp.demo.basiccomponents.examples.FloatViewActivity
import com.leovp.demo.basiccomponents.examples.HttpActivity
import com.leovp.demo.basiccomponents.examples.JavaMailActivity
import com.leovp.demo.basiccomponents.examples.KeepAliveActivity
import com.leovp.demo.basiccomponents.examples.NetActivity
import com.leovp.demo.basiccomponents.examples.NetworkMonitorActivity
import com.leovp.demo.basiccomponents.examples.RecordSingleAppScreenActivity
import com.leovp.demo.basiccomponents.examples.SaveInstanceStateActivity
import com.leovp.demo.basiccomponents.examples.TakeScreenshotActivity
import com.leovp.demo.basiccomponents.examples.ToastActivity
import com.leovp.demo.basiccomponents.examples.WatermarkActivity
import com.leovp.demo.basiccomponents.examples.accessibility.AccessibilityActivity
import com.leovp.demo.basiccomponents.examples.adb.AdbCommunication
import com.leovp.demo.basiccomponents.examples.animation.AnimationActivity
import com.leovp.demo.basiccomponents.examples.audio.ADPCMActivity
import com.leovp.demo.basiccomponents.examples.audio.AudioActivity
import com.leovp.demo.basiccomponents.examples.bluetooth.BluetoothActivity
import com.leovp.demo.basiccomponents.examples.camera2.Camera2LiveActivity
import com.leovp.demo.basiccomponents.examples.camera2.Camera2WithoutPreviewActivity
import com.leovp.demo.basiccomponents.examples.cipher.AudioCipherActivity
import com.leovp.demo.basiccomponents.examples.ffmpeg.FFMpegH264Activity
import com.leovp.demo.basiccomponents.examples.ffmpeg.FFMpegH265Activity
import com.leovp.demo.basiccomponents.examples.koin.KoinActivity
import com.leovp.demo.basiccomponents.examples.log.LogActivity
import com.leovp.demo.basiccomponents.examples.mediaplayer.PlayH265VideoByMediaCodecActivity
import com.leovp.demo.basiccomponents.examples.mediaplayer.PlayRawH265ByMediaCodecActivity
import com.leovp.demo.basiccomponents.examples.opengl.OpenGLES20Activity
import com.leovp.demo.basiccomponents.examples.orientation.OrientationActivity
import com.leovp.demo.basiccomponents.examples.pref.PrefActivity
import com.leovp.demo.basiccomponents.examples.provider.ProviderActivity
import com.leovp.demo.basiccomponents.examples.sharescreen.client.ScreenShareClientActivity
import com.leovp.demo.basiccomponents.examples.sharescreen.master.ScreenShareMasterActivity
import com.leovp.demo.basiccomponents.examples.socket.SocketClientActivity
import com.leovp.demo.basiccomponents.examples.socket.SocketServerActivity
import com.leovp.demo.basiccomponents.examples.socket.eventbusbridge.EventBusBridgeClientActivity
import com.leovp.demo.basiccomponents.examples.socket.websocket.WebSocketClientActivity
import com.leovp.demo.basiccomponents.examples.socket.websocket.WebSocketServerActivity
import com.leovp.demo.basiccomponents.examples.statusbar.StatusBarActivity
import com.leovp.demo.basiccomponents.examples.wifi.WifiActivity
import com.leovp.demo.databinding.FragmentBasicBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import kotlin.concurrent.thread

class BasicFragment : BaseFragment<FragmentBasicBinding>(R.layout.fragment_basic) {

    override fun getTagName() = ITAG

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): FragmentBasicBinding {
        return FragmentBasicBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val featureArray = arrayOf(
            Pair("ScreenShare\nMaster side", ScreenShareMasterActivity::class.java),
            Pair("ScreenShare\nClient side", ScreenShareClientActivity::class.java),
            Pair("Socket Server", SocketServerActivity::class.java),
            Pair("Socket Client", SocketClientActivity::class.java),
            Pair("WebSocket Server", WebSocketServerActivity::class.java),
            Pair("WebSocket Client", WebSocketClientActivity::class.java),
            Pair(
                "Play H265 Video File by MediaCodec",
                PlayH265VideoByMediaCodecActivity::class.java
            ),
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
            Pair(
                getString(R.string.act_title_change_app_lang),
                ChangeAppLanguageActivity::class.java
            ),
            Pair("Toast", ToastActivity::class.java),
            Pair("FloatView", FloatViewActivity::class.java),
            Pair("CircleProgressBar", CircleProgressbarActivity::class.java),
            Pair("App Settings", AppSettingsActivity::class.java),
            Pair("Orientation", OrientationActivity::class.java),
            Pair("Koin", KoinActivity::class.java),
            Pair("Adb Communication", AdbCommunication::class.java),
            Pair("Bitmap Native Util", BitmapNativeActivity::class.java),
            Pair("Status Bar", StatusBarActivity::class.java)
        )

        val colorBaseAdapter = ColorBaseAdapter(featureArray.map { it.first }.toTypedArray(), colors)
        colorBaseAdapter.onItemClickListener = object : ColorBaseAdapter.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                startActivity(
                    featureArray[position].second,
                    { intent -> intent.putExtra("title", featureArray[position].first) }
                )
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
        ).toIntArray()
    }
}
