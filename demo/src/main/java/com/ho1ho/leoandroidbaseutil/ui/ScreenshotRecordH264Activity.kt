package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.device.DeviceUtil
import com.ho1ho.androidbase.utils.file.FileUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.leoandroidbaseutil.ui.sharescreen.master.ScreenShareSetting
import com.ho1ho.screenshot.ScreenCapture
import com.ho1ho.screenshot.base.ScreenDataListener
import com.ho1ho.screenshot.base.ScreenshotStrategy
import kotlinx.android.synthetic.main.activity_screenshot_record_h264.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream


class ScreenshotRecordH264Activity : BaseDemonstrationActivity() {

    private lateinit var videoH264OsForDebug: BufferedOutputStream

    private val screenDataListener = object : ScreenDataListener {
        override fun onDataUpdate(buffer: Any) {
            val buf = buffer as ByteArray
            LLog.i(ITAG, "Get h264 data[${buf.size}]")
            videoH264OsForDebug.write(buf)
//            if (outputH264File) {
//                try {
//                    videoH264Os.write(buf)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//            screenDataUpdateListener?.onUpdate(buffer)
            // Bitmap for screenshot
//            val bitmap = buffer as Bitmap
//            FileUtil.writeBitmapToFile(bitmap, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshot_record_h264)

        val file = File(FileUtil.getBaseDirString(this, "output"))
        file.mkdirs()
        videoH264OsForDebug = BufferedOutputStream(FileOutputStream(File(file, "screen.h264")))

        val width = window.decorView.rootView.width
        val height = window.decorView.rootView.height
        LLog.d(ITAG, "$width x $height=${width * height}")

        val screenInfo = DeviceUtil.getResolution(this)
        val setting = ScreenShareSetting(
            (screenInfo.x * 0.8F).toInt() / 16 * 16,
            (screenInfo.y * 0.8F).toInt() / 16 * 16,
            DeviceUtil.getDensity(this)
        )
        setting.fps = 20f

        val screenProcessor = ScreenCapture.Builder(
            setting.width, // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
            setting.height, // 800 1024 1280
            setting.dpi,
            null,
            ScreenCapture.SCREEN_CAPTURE_TYPE_IMAGE,
            screenDataListener
        ).setFps(setting.fps)
            .setSampleSize(1)
            .build()

        toggleBtn.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                CoroutineScope(Dispatchers.IO).launch {
                    screenProcessor.run {
                        onInit()
                        onStart()
                    }
                    (screenProcessor as ScreenshotStrategy).startRecord(window)
                }
            } else {
                videoH264OsForDebug.flush()
                videoH264OsForDebug.close()
                screenProcessor.onRelease()
            }
        }
    }
}