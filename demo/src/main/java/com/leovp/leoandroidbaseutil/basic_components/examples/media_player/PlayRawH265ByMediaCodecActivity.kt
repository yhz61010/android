package com.leovp.leoandroidbaseutil.basic_components.examples.media_player

import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceHolder
import com.leovp.androidbase.exts.android.hideNavigationBar
import com.leovp.androidbase.exts.android.requestFullScreen
import com.leovp.androidbase.exts.kotlin.ITAG
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.system.ResourcesUtil.saveRawResourceToFile
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.media_player.base.DecodeH265RawFile
import com.leovp.leoandroidbaseutil.basic_components.examples.media_player.ui.CustomSurfaceView
import kotlinx.android.synthetic.main.activity_play_video.*
import kotlinx.coroutines.*

class PlayRawH265ByMediaCodecActivity : BaseDemonstrationActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private val decoderManager = DecodeH265RawFile()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestFullScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_video)
        CodecUtil.getAllSupportedCodecList().forEach { LogContext.log.i(ITAG, "Codec name=${it.name}") }

        val videoSurfaceView = surfaceView as CustomSurfaceView
        val surface = videoSurfaceView.holder.surface

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                uiScope.launch {
                    val rawFileFullPath = withContext(Dispatchers.IO) {
                        saveRawResourceToFile(resources, R.raw.tears_400_x265_raw, getExternalFilesDir(null)!!.absolutePath, "h265.h265")
                    }
                    decoderManager.init(rawFileFullPath, 1920, 800, surface)
                    // In order to fix the SurfaceView blink problem,
                    // first we set SurfaceView color to black same as root layout background color.
                    // When SurfaceView is ready to render, we remove its background color.
                    videoSurfaceView.setBackgroundColor(Color.TRANSPARENT)
                    videoSurfaceView.setDimension(1920, 800)
                    decoderManager.startDecoding()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }

    override fun onResume() {
        hideNavigationBar()
        super.onResume()
    }

    override fun onStop() {
        decoderManager.close()
        super.onStop()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }
}