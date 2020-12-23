package com.leovp.leoandroidbaseutil.basic_components.examples.media_player

import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceHolder
import com.leovp.androidbase.exts.android.hideNavigationBar
import com.leovp.androidbase.exts.android.requestFullScreen
import com.leovp.androidbase.exts.kotlin.ITAG
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.system.ResourcesUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.media_player.base.DecoderVideoFileManager
import com.leovp.leoandroidbaseutil.basic_components.examples.media_player.ui.CustomSurfaceView
import kotlinx.android.synthetic.main.activity_play_video.*
import kotlinx.coroutines.*

class PlayVideoByMediaCodecActivity : BaseDemonstrationActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private val decoderManager = DecoderVideoFileManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestFullScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_video)
        CodecUtil.getAllSupportedCodecList().forEach { LogContext.log.i(ITAG, "Codec name=${it.name}") }

        val videoSurfaceView = surfaceView as CustomSurfaceView
        val surface = videoSurfaceView.holder.surface
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                uiScope.launch {
                    val videoFile = withContext(Dispatchers.IO) {
                        ResourcesUtil.saveRawResourceToFile(R.raw.tears_400_x265, getExternalFilesDir(null)!!.absolutePath, "h265.mp4")
                    }
                    decoderManager.init(videoFile, surface)
                    // In order to fix the SurfaceView blink problem,
                    // first we set SurfaceView color to black same as root layout background color.
                    // When SurfaceView is ready to render, we remove its background color.
                    videoSurfaceView.setBackgroundColor(Color.TRANSPARENT)
                    videoSurfaceView.setDimension(decoderManager.videoWidth, decoderManager.videoHeight)
                    decoderManager.startDecoding()
//                    AudioPlayManager.setContext(applicationContext)
//                    AudioPlayManager.startThread()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }

    override fun onResume() {
        hideNavigationBar()
        super.onResume()
    }

    override fun onStop() {
        decoderManager.close()
        uiScope.cancel()
//        AudioPlayManager.close()
        super.onStop()
    }
}