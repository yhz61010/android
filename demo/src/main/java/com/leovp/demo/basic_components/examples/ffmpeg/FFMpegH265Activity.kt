package com.leovp.demo.basic_components.examples.ffmpeg

import android.os.Bundle
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basic_components.examples.ffmpeg.utils.DecodeH265RawFileByFFMpeg
import com.leovp.demo.databinding.ActivityFfmpegH265Binding
import com.leovp.lib_common_android.exts.saveRawResourceToFile
import com.leovp.log_sdk.base.ITAG
import com.leovp.opengl_sdk.ui.LeoGLSurfaceView

class FFMpegH265Activity : BaseDemonstrationActivity<ActivityFfmpegH265Binding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityFfmpegH265Binding {
        return ActivityFfmpegH265Binding.inflate(layoutInflater)
    }

    private lateinit var glSurfaceView: LeoGLSurfaceView
    private val decodeObjByFFMpeg: DecodeH265RawFileByFFMpeg = DecodeH265RawFileByFFMpeg()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setKeepRatio(true)
        //        glSurfaceView.updateDimension(getScreenWidth(), getScreenAvailableHeight())
        val rawFileFullPath =
                saveRawResourceToFile(R.raw.tears_400_x265_raw,
                    getExternalFilesDir(null)!!.absolutePath,
                    "h265.h265")
        decodeObjByFFMpeg.init(rawFileFullPath, glSurfaceView)
        decodeObjByFFMpeg.startDecoding()
    }

    override fun onDestroy() {
        decodeObjByFFMpeg.close()
        super.onDestroy()
    }
}