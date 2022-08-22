package com.leovp.demo.basiccomponents.examples.media_player

import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceHolder
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.media_player.base.DecoderVideoFileManager
import com.leovp.demo.databinding.ActivityPlayVideoBinding
import com.leovp.android.exts.hideNavigationBar
import com.leovp.android.exts.requestFullScreenAfterVisible
import com.leovp.android.exts.requestFullScreenBeforeSetContentView
import com.leovp.android.exts.saveRawResourceToFile
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import kotlinx.coroutines.*

class PlayH265VideoByMediaCodecActivity : BaseDemonstrationActivity<ActivityPlayVideoBinding>() {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityPlayVideoBinding {
        return ActivityPlayVideoBinding.inflate(layoutInflater)
    }

    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private val decoderManager = DecoderVideoFileManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestFullScreenBeforeSetContentView()
        super.onCreate(savedInstanceState)
        CodecUtil.getAllSupportedCodecList()
            .forEach { LogContext.log.i(ITAG, "Codec name=${it.name}") }

        val videoSurfaceView = binding.surfaceView
        val surface = videoSurfaceView.holder.surface
        binding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                uiScope.launch {
                    val videoFile = withContext(Dispatchers.IO) {
                        saveRawResourceToFile(R.raw.tears_400_x265, getExternalFilesDir(null)!!.absolutePath, "h265.mp4")
                    }
                    decoderManager.init(videoFile, surface)
                    // In order to fix the SurfaceView blink problem,
                    // first we set SurfaceView color to black same as root layout background color.
                    // When SurfaceView is ready to render, we remove its background color.
                    videoSurfaceView.setBackgroundColor(Color.TRANSPARENT)
                    videoSurfaceView.setDimension(decoderManager.videoWidth, decoderManager.videoHeight)
                    decoderManager.startDecoding()
//                    AudioPlayManager.setContext(applicationContext)
//                    AudioPlayManager.startThread()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        requestFullScreenAfterVisible()
        hideNavigationBar()
    }

    override fun onStop() {
        decoderManager.close()
        uiScope.cancel()
//        AudioPlayManager.close()
        super.onStop()
    }
}
