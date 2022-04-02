package com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg

import android.os.Bundle
import com.leovp.androidbase.exts.android.saveRawResourceToFile
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg.utils.DecodeH264RawFileByFFMpeg
import com.leovp.opengl_sdk.ui.LeoGLSurfaceView

class FFMpegH264Activity : BaseDemonstrationActivity() {
    private lateinit var glSurfaceView: LeoGLSurfaceView
    private val decodeObjByFFMpeg: DecodeH264RawFileByFFMpeg = DecodeH264RawFileByFFMpeg()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ffmpeg_h264)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        //        glSurfaceView.updateDimension(getScreenWidth(), getScreenAvailableHeight())
        val rawFileFullPath = saveRawResourceToFile(R.raw.tears_400_x264_raw, getExternalFilesDir(null)!!.absolutePath, "h264.h264")
        decodeObjByFFMpeg.init(rawFileFullPath, glSurfaceView)
        decodeObjByFFMpeg.startDecoding()
    }

    override fun onDestroy() {
        decodeObjByFFMpeg.close()
        super.onDestroy()
    }
}