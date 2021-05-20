package com.leovp.leoandroidbaseutil.basic_components.examples

import android.media.MediaCodec
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.leovp.androidbase.exts.android.densityDpi
import com.leovp.androidbase.exts.android.getAvailableResolution
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.ITAG
import com.leovp.androidbase.exts.kotlin.toHexString
import com.leovp.androidbase.utils.file.FileUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.sharescreen.master.ScreenShareSetting
import com.leovp.leoandroidbaseutil.databinding.ActivityScreenshotRecordH264Binding
import com.leovp.screenshot.ScreenCapture
import com.leovp.screenshot.base.ScreenDataListener
import com.leovp.screenshot.base.strategies.Screenshot2H264Strategy
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream


class RecordSingleAppScreenActivity : BaseDemonstrationActivity() {

    private lateinit var binding: ActivityScreenshotRecordH264Binding

    private lateinit var videoH264OsForDebug: BufferedOutputStream

    private val screenDataListener = object : ScreenDataListener {
        override fun onDataUpdate(buffer: ByteArray, flags: Int) {
            when (flags) {
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> LogContext.log.i(ITAG, "Get h264 data[${buffer.size}]=${buffer.toHexString()}")
                MediaCodec.BUFFER_FLAG_KEY_FRAME -> LogContext.log.i(ITAG, "Get h264 data Key-Frame[${buffer.size}]")
                else -> LogContext.log.i(ITAG, "Get h264 data[${buffer.size}]")
            }
            videoH264OsForDebug.write(buffer)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenshotRecordH264Binding.inflate(layoutInflater).apply { setContentView(root) }

        val file = FileUtil.getBaseDirString(this, "output")
        val dstFile = File(file, "screen.h264")
        videoH264OsForDebug = BufferedOutputStream(FileOutputStream(dstFile))
        LogContext.log.i("dstFile=${dstFile.absolutePath}")

        val screenInfo = getAvailableResolution()
        val setting = ScreenShareSetting(
            (screenInfo.x * 0.8F / 16).toInt() * 16,
            (screenInfo.y * 0.8F / 16).toInt() * 16,
            densityDpi
        )
        // FIXME: Seems does not work. Check bellow setKeyFrameRate
        setting.fps = 5f

        val screenProcessor = ScreenCapture.Builder(
            setting.width, // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
            setting.height, // 800 1024 1280
            setting.dpi,
            null,
            ScreenCapture.BY_IMAGE_2_H264,
            screenDataListener
        ).setFps(setting.fps)
            .setKeyFrameRate(20)
            .setQuality(80)
            .setSampleSize(1)
            .build()

        binding.toggleBtn.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                (screenProcessor as Screenshot2H264Strategy).startRecord(this)
            } else {
                videoH264OsForDebug.flush()
                videoH264OsForDebug.close()
                screenProcessor.onRelease()
            }
        }
    }

    fun onShowToastClick(view: View) {
        toast("Custom Toast")
    }

    fun onShowDialogClick(view: View) {
        AlertDialog.Builder(this)
            .setTitle("Title")
            .setMessage("This is a dialog")
            .setPositiveButton("OK") { dlg, _ ->
                dlg.dismiss()
            }
            .setNeutralButton("Cancel", null)
            .create()
            .show()
    }
}