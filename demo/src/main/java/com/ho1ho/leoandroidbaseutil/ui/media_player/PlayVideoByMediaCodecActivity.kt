package com.ho1ho.leoandroidbaseutil.ui.media_player

import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceHolder
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.AppUtil
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.media.CodecUtil
import com.ho1ho.androidbase.utils.system.ResourcesUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.leoandroidbaseutil.ui.media_player.base.DecoderVideoFileManager
import com.ho1ho.leoandroidbaseutil.ui.media_player.ui.CustomSurfaceView
import kotlinx.android.synthetic.main.activity_play_video.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayVideoByMediaCodecActivity : BaseDemonstrationActivity() {

    private val decoderManager = DecoderVideoFileManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUtil.requestFullScreen(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_video)
        CodecUtil.getAllSupportedCodecList().forEach { LLog.i(ITAG, "Codec name=${it.name}") }

        val videoSurfaceView = surfaceView as CustomSurfaceView
        val surface = videoSurfaceView.holder.surface
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                CoroutineScope(Dispatchers.Main).launch {
                    val videoFile = withContext(Dispatchers.IO) {
                        ResourcesUtil.saveRawResourceToFile(resources, R.raw.tears_400_x265, getExternalFilesDir(null)!!.absolutePath, "h265.mp4")
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
        AppUtil.hideNavigationBar(this)
        super.onResume()
    }

    override fun onStop() {
        decoderManager.close()
//        AudioPlayManager.close()
        super.onStop()
    }
}