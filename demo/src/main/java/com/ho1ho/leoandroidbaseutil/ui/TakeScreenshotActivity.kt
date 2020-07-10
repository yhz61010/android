package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import android.view.View
import com.ho1ho.androidbase.utils.device.ScreenUtil
import com.ho1ho.androidbase.utils.media.ImageUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import java.io.File

class TakeScreenshotActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_screenshot)
    }

    fun onScreenshot(view: View) {
        val bitmap = ScreenUtil.takeScreenshot(window)
        bitmap?.let {
            ImageUtil.writeBitmapToFile(File(getExternalFilesDir(null), "screenshot.jpg"), it, 100)
        }
    }
}