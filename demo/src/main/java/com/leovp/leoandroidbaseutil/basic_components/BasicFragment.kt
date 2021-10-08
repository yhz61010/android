package com.leovp.leoandroidbaseutil.basic_components

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.leovp.androidbase.exts.kotlin.sleep
import com.leovp.leoandroidbaseutil.ColorBaseAdapter
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.basic_components.examples.*
import com.leovp.leoandroidbaseutil.basic_components.examples.accessibility.AccessibilityActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.aidl.AidlActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.animation.AnimationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.ADPCMActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.AudioActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.BluetoothActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.camera2.Camera2LiveActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.camera2.Camera2WithoutPreviewActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.cipher.AudioCipherActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.log.LogActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.media_player.PlayRawH265ByMediaCodecActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.media_player.PlayVideoByMediaCodecActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.pref.PrefActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.provider.ProviderActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.client.ScreenShareClientActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareMasterActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.socket.SocketClientActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.socket.SocketServerActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.socket.eventbus_bridge.EventBusBridgeClientActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.socket.websocket.WebSocketClientActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.socket.websocket.WebSocketServerActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.wifi.WifiActivity
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import kotlin.concurrent.thread

class BasicFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_basic, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val colorBaseAdapter = ColorBaseAdapter(featureList.map { it.first }, colors)
        colorBaseAdapter.onItemClickListener = object : ColorBaseAdapter.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                val intent = Intent(requireContext(), featureList[position].second)
                intent.putExtra("title", featureList[position].first)
                startActivity(intent)
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
//            layoutManager = LinearLayoutManager(requireActivity())
            adapter = colorBaseAdapter
        }
    }

    override fun onDestroy() {
//        CustomApplication.instance.closeDebugOutputFile()
        LogContext.log.i(ITAG, "onDestroy()")
        super.onDestroy()
        // In some cases, if you use saved some parameters in Application, when app exits,
        // the parameters may not be released. So we need to call AppUtil.exitApp(ctx)
//        AppUtil.exitApp(this)
    }

    companion object {
        private val featureList = arrayOf(
            Pair("ScreenShare\nMaster side", ScreenShareMasterActivity::class.java),
            Pair("ScreenShare\nClient side", ScreenShareClientActivity::class.java),
            Pair("Socket Server", SocketServerActivity::class.java),
            Pair("Socket Client", SocketClientActivity::class.java),
            Pair("WebSocket Server", WebSocketServerActivity::class.java),
            Pair("WebSocket Client", WebSocketClientActivity::class.java),
            Pair("Play Video File by MediaCodec", PlayVideoByMediaCodecActivity::class.java),
            Pair("Play Raw H265 by MediaCodec", PlayRawH265ByMediaCodecActivity::class.java),
            Pair("Camera2Live", Camera2LiveActivity::class.java),
            Pair("Camera2 No Preview", Camera2WithoutPreviewActivity::class.java),
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
            Pair("Change App Language", ChangeAppLanguageActivity::class.java),
            Pair("Toast", ToastActivity::class.java),
            Pair("FloatView", FloatViewActivity::class.java),
            Pair("CircleProgressBar", CircleProgressbarActivity::class.java),
        )

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