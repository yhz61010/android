package com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg.ui.GLSurfaceView

class FFMpegH264Activity : AppCompatActivity() {
    private lateinit var glSurfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ffmpeg_h264)

//        glSurfaceView = findViewById<GLSurfaceView>(R.id.glSurfaceView).apply{
//            initDecoder(null, sps, pps)
//            decodeVideo(sps + pps)
//        }
//
//        val decodeFrame = decodeVideo(videoData)
//        glSurfaceView.setVideoDimension(decodeFrame.width, decodeFrame.height)
//        glSurfaceView.render(decodeFrame.yuvBytes, 0)
    }

    override fun onDestroy() {
//        glSurfaceView.releaseDecoder()
        super.onDestroy()
    }
}