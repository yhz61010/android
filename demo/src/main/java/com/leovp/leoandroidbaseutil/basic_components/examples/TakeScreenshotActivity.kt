package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.lib_image.writeToFile
import com.leovp.screencapture.screenshot.CaptureUtil
import java.io.File

class TakeScreenshotActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_screenshot)
    }

    fun onScreenshot(@Suppress("UNUSED_PARAMETER") view: View) {
        val bitmap = CaptureUtil.takeScreenshot(window)
        bitmap?.let {
            val screenshotFile = File(getExternalFilesDir(null), "screenshot.jpg")
            it.writeToFile(screenshotFile, 100)
            toast("Screenshot is saved in ${screenshotFile.absolutePath}")
        }
    }
}