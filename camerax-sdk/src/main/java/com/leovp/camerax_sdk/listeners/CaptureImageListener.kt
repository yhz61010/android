package com.leovp.camerax_sdk.listeners

import android.net.Uri
import com.leovp.camerax_sdk.bean.CaptureImage

/**
 * Author: Michael Leo
 * Date: 2022/4/25 14:23
 */
interface CaptureImageListener {
    fun onSavedImageUri(savedUri: Uri, rotationInDegree: Int, mirror: Boolean)
    fun onSavedImageBytes(savedImage: CaptureImage)
}