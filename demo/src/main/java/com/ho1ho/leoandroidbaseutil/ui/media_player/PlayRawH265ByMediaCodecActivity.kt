package com.ho1ho.leoandroidbaseutil.ui.media_player

import android.os.Bundle
import android.os.Environment
import android.view.Surface
import android.view.SurfaceHolder
import com.ho1ho.androidbase.utils.AppUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.leoandroidbaseutil.ui.media_player.base.DecodeH265RawFile
import com.ho1ho.leoandroidbaseutil.ui.media_player.ui.CustomSurfaceView
import kotlinx.android.synthetic.main.activity_play_video.*
import java.io.File

val videoRawFile = File(Environment.getExternalStorageDirectory(), "h265.h265")

class PlayRawH265ByMediaCodecActivity : BaseDemonstrationActivity() {

    companion object {
        lateinit var surface: Surface
    }

    private val decoderManager = DecodeH265RawFile()
    private val videoSurfaceView: CustomSurfaceView by lazy { surfaceView as CustomSurfaceView }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUtil.requestFullScreen(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_video)

        surface = videoSurfaceView.holder.surface
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                decoderManager.init(videoRawFile.absolutePath, 1920, 800, surface)
                videoSurfaceView.setDimension(1920, 800)
                decoderManager.startDecoding()
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
        super.onStop()
    }
}