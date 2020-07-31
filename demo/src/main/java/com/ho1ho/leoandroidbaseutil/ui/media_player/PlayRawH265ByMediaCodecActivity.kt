package com.ho1ho.leoandroidbaseutil.ui.media_player

import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.AppUtil
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.media.CodecUtil
import com.ho1ho.androidbase.utils.system.ResourcesUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.leoandroidbaseutil.ui.media_player.base.DecodeH265RawFile
import com.ho1ho.leoandroidbaseutil.ui.media_player.ui.CustomSurfaceView
import kotlinx.android.synthetic.main.activity_play_video.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        CodecUtil.getAllSupportedCodecList().forEach { LLog.i(ITAG, "Codec name=${it.name}") }

        surface = videoSurfaceView.holder.surface
        CoroutineScope(Dispatchers.IO).launch {
            val rawH265File = saveRawFile()
            addSurfaceCallback(rawH265File)
        }
    }

    private fun saveRawFile(): String {
        return ResourcesUtil.saveRawResourceToFile(resources, R.raw.tears_400_x265_raw, getExternalFilesDir(null)!!.absolutePath, "h265.h265")
    }

    private suspend fun addSurfaceCallback(h265RawFile: String) = withContext(Dispatchers.Main) {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                decoderManager.init(h265RawFile, 1920, 800, surface)
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