package com.ho1ho.leoandroidbaseutil.ui.h264_player

import android.os.Bundle
import android.os.Environment
import android.view.Surface
import android.view.SurfaceHolder
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.leoandroidbaseutil.ui.h264_player.base.AudioPlayManager
import com.ho1ho.leoandroidbaseutil.ui.h264_player.base.DecoderManager
import kotlinx.android.synthetic.main.activity_play_mp4_encode_by_h264.*
import java.io.File

val mp4File = File(Environment.getExternalStorageDirectory(), "video.mp4")

class PlayMp4ByMediaCodecH264Activity : BaseDemonstrationActivity() {

    companion object {
        lateinit var surface: Surface
    }

    private val INIT_MANAGER_MSG = 0x01
    private val INIT_MANAGER_DELAY = 1 * 1000

    private var isPlayH264 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_mp4_encode_by_h264)

        surface = surfaceView.holder.surface
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                isPlayH264 = intent.getBooleanExtra("isPlayH264", false)
                LLog.w(ITAG, "isPlayH264=$isPlayH264")
                if (isPlayH264) {
//                    DecoderManager.startH264Decode()
                } else {
                    DecoderManager.startMP4Decode(mp4File.absolutePath, surface, 1080, 1920)
                    AudioPlayManager.getInstance().setContext(applicationContext)
                    AudioPlayManager.getInstance().startThread()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }
}