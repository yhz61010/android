package com.ho1ho.leoandroidbaseutil.common_components.examples

import android.os.Bundle
import android.view.View
import com.ho1ho.androidbase.utils.device.CaptureUtil
import com.ho1ho.androidbase.utils.media.ImageUtil
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity
import java.io.File

class TakeScreenshotActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_screenshot)
    }

    fun onScreenshot(view: View) {
        val bitmap = CaptureUtil.takeScreenshot(window)
        bitmap?.let {
            val screenshotFile = File(getExternalFilesDir(null), "screenshot.jpg")
            ImageUtil.writeBitmapToFile(screenshotFile, it, 100)
            ToastUtil.showToast("Screenshot is saved in ${screenshotFile.absolutePath}")
        }
    }
}