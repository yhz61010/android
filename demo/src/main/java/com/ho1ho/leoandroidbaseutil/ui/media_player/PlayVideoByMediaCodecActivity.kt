package com.ho1ho.leoandroidbaseutil.ui.media_player

import android.os.Bundle
import android.os.Environment
import android.view.Surface
import android.view.SurfaceHolder
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.AppUtil
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.leoandroidbaseutil.ui.media_player.base.DecoderManager
import com.ho1ho.leoandroidbaseutil.ui.media_player.ui.CustomSurfaceView
import kotlinx.android.synthetic.main.activity_play_video.*
import java.io.File

//val videoFile = File(Environment.getExternalStorageDirectory(), "video.mp4")
val videoFile = File(Environment.getExternalStorageDirectory(), "h265.mp4")

class PlayVideoByMediaCodecActivity : BaseDemonstrationActivity() {

    companion object {
        lateinit var surface: Surface
    }

    private val decoderManager = DecoderManager()
    private var isPlayH264 = false
    private val videoSurfaceView: CustomSurfaceView by lazy { surfaceView as CustomSurfaceView }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUtil.requestFullScreen(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_video)

        surface = videoSurfaceView.holder.surface
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                isPlayH264 = intent.getBooleanExtra("isPlayH264", false)
                LLog.w(ITAG, "isPlayH264=$isPlayH264")
                if (isPlayH264) {
//                    DecoderManager.startH264Decode()
                } else {
                    decoderManager.init(videoFile.absolutePath, surface)
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