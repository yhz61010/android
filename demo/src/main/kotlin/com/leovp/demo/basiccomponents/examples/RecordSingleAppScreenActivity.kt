package com.leovp.demo.basiccomponents.examples

import android.media.MediaCodec
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.leovp.android.exts.toast
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.sharescreen.master.ScreenShareSetting
import com.leovp.demo.databinding.ActivityScreenshotRecordH264Binding
import com.leovp.bytes.toHexString
import com.leovp.android.exts.densityDpi
import com.leovp.android.exts.getBaseDirString
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.screencapture.screenrecord.ScreenCapture
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordMediaCodecStrategy
import com.leovp.screencapture.screenrecord.base.strategies.Screenshot2H26xStrategy
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class RecordSingleAppScreenActivity : BaseDemonstrationActivity<ActivityScreenshotRecordH264Binding>() {
    override fun getTagName(): String = ITAG

    companion object {
        val VIDEO_ENCODE_TYPE = ScreenRecordMediaCodecStrategy.EncodeType.H265
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityScreenshotRecordH264Binding {
        return ActivityScreenshotRecordH264Binding.inflate(layoutInflater)
    }

    private lateinit var videoH26xOsForDebug: BufferedOutputStream

    private val screenDataListener = object : ScreenDataListener {
        override fun onDataUpdate(buffer: Any, flags: Int, presentationTimeUs: Long) {
            val data = buffer as ByteArray
            when (flags) {
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> LogContext.log.i(
                    ITAG,
                    "Get $VIDEO_ENCODE_TYPE data[${data.size}]=${data.toHexString()} presentationTimeUs=$presentationTimeUs"
                )
                MediaCodec.BUFFER_FLAG_KEY_FRAME -> {
                    LogContext.log.i(ITAG, "Get $VIDEO_ENCODE_TYPE data Key-Frame[${data.size}] presentationTimeUs=$presentationTimeUs")
                }
                else -> LogContext.log.i(ITAG, "Get $VIDEO_ENCODE_TYPE data[${data.size}] presentationTimeUs=$presentationTimeUs")
            }
            videoH26xOsForDebug.write(data)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = getBaseDirString("output")
        val dstFile = File(
            file,
            "screen" + when (VIDEO_ENCODE_TYPE) {
                ScreenRecordMediaCodecStrategy.EncodeType.H264 -> ".h264"
                ScreenRecordMediaCodecStrategy.EncodeType.H265 -> ".h265"
            }
        )
        videoH26xOsForDebug = BufferedOutputStream(FileOutputStream(dstFile))
        LogContext.log.i("dstFile=${dstFile.absolutePath}")

        val screenInfo = application.screenAvailableResolution
        val setting = ScreenShareSetting(
            (screenInfo.width * 0.8F / 16).toInt() * 16,
            (screenInfo.height * 0.8F / 16).toInt() * 16,
            densityDpi
        )
        // FIXME: Seems does not work. Check bellow setKeyFrameRate
        setting.fps = 5f

        val screenProcessor = ScreenCapture.Builder(
            setting.width, // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
            setting.height, // 800 1024 1280
            setting.dpi,
            null,
            ScreenCapture.BY_IMAGE_2_H26x,
            screenDataListener
        )
            .setEncodeType(VIDEO_ENCODE_TYPE)
            .setFps(setting.fps)
            .setKeyFrameRate(20)
            .setQuality(80)
            .setSampleSize(1)
            .build()

        binding.toggleBtn.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // @RequiresApi(Build.VERSION_CODES.O)
                (screenProcessor as Screenshot2H26xStrategy).startRecord(this)
            } else {
                videoH26xOsForDebug.flush()
                videoH26xOsForDebug.close()
                screenProcessor.onRelease()
            }
        }
    }

    fun onShowToastClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("Custom Toast")
    }

    fun onShowDialogClick(@Suppress("UNUSED_PARAMETER") view: View) {
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
