package com.leovp.demo.basic_components.examples.ffmpeg

import android.os.Bundle
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basic_components.examples.ffmpeg.utils.DecodeH264RawFileByFFMpeg
import com.leovp.demo.databinding.ActivityFfmpegH264Binding
import com.leovp.lib_common_android.exts.saveRawResourceToFile
import com.leovp.log_sdk.base.ITAG
import com.leovp.opengl_sdk.ui.LeoGLSurfaceView

class FFMpegH264Activity : BaseDemonstrationActivity<ActivityFfmpegH264Binding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityFfmpegH264Binding {
        return ActivityFfmpegH264Binding.inflate(layoutInflater)
    }

    private lateinit var glSurfaceView: LeoGLSurfaceView
    private val decodeObjByFFMpeg: DecodeH264RawFileByFFMpeg = DecodeH264RawFileByFFMpeg()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setKeepRatio(true)
        //        glSurfaceView.updateDimension(screenWidth, getScreenAvailableHeight())
        val rawFileFullPath = saveRawResourceToFile(R.raw.tears_400_x264_raw, getExternalFilesDir(null)!!.absolutePath, "h264.h264")
        decodeObjByFFMpeg.init(rawFileFullPath, glSurfaceView)
        decodeObjByFFMpeg.startDecoding()
    }

    override fun onDestroy() {
        decodeObjByFFMpeg.close()
        super.onDestroy()
    }
}