package com.leovp.demo.basiccomponents.examples.ffmpeg

import android.os.Bundle
import com.leovp.android.exts.saveRawResourceToFile
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.ffmpeg.utils.DecodeH265RawFileByFFMpeg
import com.leovp.demo.databinding.ActivityFfmpegH265Binding
import com.leovp.log.base.ITAG
import com.leovp.opengl.ui.LeoGLSurfaceView

class FFMpegH265Activity : BaseDemonstrationActivity<ActivityFfmpegH265Binding>(R.layout.activity_ffmpeg_h265) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityFfmpegH265Binding =
        ActivityFfmpegH265Binding.inflate(layoutInflater)

    private lateinit var glSurfaceView: LeoGLSurfaceView
    private val decodeObjByFFMpeg: DecodeH265RawFileByFFMpeg = DecodeH265RawFileByFFMpeg()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setKeepRatio(true)
        //        glSurfaceView.updateDimension(screenWidth, getScreenAvailableHeight())
        val rawFileFullPath =
            saveRawResourceToFile(
                R.raw.tears_400_x265_raw,
                getExternalFilesDir(null)!!.absolutePath,
                "h265.h265"
            )
        decodeObjByFFMpeg.init(rawFileFullPath, glSurfaceView)
        decodeObjByFFMpeg.startDecoding()
    }

    override fun onDestroy() {
        decodeObjByFFMpeg.close()
        super.onDestroy()
    }
}
