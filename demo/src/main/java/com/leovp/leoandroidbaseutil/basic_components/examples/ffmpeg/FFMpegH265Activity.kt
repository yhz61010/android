package com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg

import android.os.Bundle
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg.utils.DecodeH265RawFileByFFMpeg
import com.leovp.lib_common_android.exts.saveRawResourceToFile
import com.leovp.opengl_sdk.ui.LeoGLSurfaceView

class FFMpegH265Activity : BaseDemonstrationActivity() {
    private lateinit var glSurfaceView: LeoGLSurfaceView
    private val decodeObjByFFMpeg: DecodeH265RawFileByFFMpeg = DecodeH265RawFileByFFMpeg()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ffmpeg_h265)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setKeepRatio(true)
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