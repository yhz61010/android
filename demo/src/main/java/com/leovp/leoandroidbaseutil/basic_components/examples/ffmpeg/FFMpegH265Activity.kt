package com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg

import android.os.Bundle
import com.leovp.androidbase.exts.android.saveRawResourceToFile
import com.leovp.androidbase.ui.LeoGLSurfaceView
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg.utils.DecodeH265RawFileByFFMpeg

class FFMpegH265Activity : BaseDemonstrationActivity() {
    private lateinit var glSurfaceView: LeoGLSurfaceView
    private val decodeObjByFFMpeg: DecodeH265RawFileByFFMpeg = DecodeH265RawFileByFFMpeg()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ffmpeg_h265)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        //        glSurfaceView.updateDimension(getScreenWidth(), getScreenAvailableHeight())
        val rawFileFullPath = saveRawResourceToFile(R.raw.tears_400_x265_raw, getExternalFilesDir(null)!!.absolutePath, "h265.h265")
        decodeObjByFFMpeg.init(rawFileFullPath, glSurfaceView)
        decodeObjByFFMpeg.startDecoding()
    }

    override fun onDestroy() {
        decodeObjByFFMpeg.close()
        super.onDestroy()
    }
}