package com.leovp.camerax_sdk.listeners.impl

import android.net.Uri
import com.leovp.camerax_sdk.listeners.CaptureImageListener

/**
 * Author: Michael Leo
 * Date: 2022/4/25 15:34
 */
class SimpleCaptureImageListener : CaptureImageListener {
    override fun onSavedImageUri(savedUri: Uri) {}

    override fun onSavedImageBytes(imageBytes: ByteArray, width: Int, height: Int) {}
}