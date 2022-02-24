package com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.leovp.androidbase.exts.android.saveRawResourceToFile
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg.ui.GLSurfaceView
import com.leovp.leoandroidbaseutil.basic_components.examples.ffmpeg.utils.DecodeH265RawFile
import com.leovp.lib_common_android.exts.getScreenRealHeight
import com.leovp.lib_common_android.exts.getScreenWidth

class FFMpegH265Activity : AppCompatActivity() {
    private lateinit var glSurfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ffmpeg_h265)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.updateDimension(getScreenWidth(), getScreenRealHeight())
        val rawFileFullPath = saveRawResourceToFile(R.raw.tears_400_x265_raw, getExternalFilesDir(null)!!.absolutePath, "h265.h265")
        val decodeObj = DecodeH265RawFile()
        decodeObj.init(rawFileFullPath, glSurfaceView)
        decodeObj.startDecoding()
    }

    override fun onDestroy() {
        glSurfaceView.releaseDecoder()
        super.onDestroy()
    }
}