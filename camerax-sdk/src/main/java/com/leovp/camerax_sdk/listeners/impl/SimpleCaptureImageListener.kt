@file:Suppress("unused")

package com.leovp.camerax_sdk.listeners.impl

import android.net.Uri
import com.leovp.camerax_sdk.bean.CaptureImage
import com.leovp.camerax_sdk.listeners.CaptureImageListener

/**
 * Author: Michael Leo
 * Date: 2022/4/25 15:34
 */
class SimpleCaptureImageListener : CaptureImageListener {
    override fun onSavedImageUri(savedUri: Uri, rotationInDegree: Int, mirror: Boolean) {}

    override fun onSavedImageBytes(savedImage: CaptureImage) {
    }
}