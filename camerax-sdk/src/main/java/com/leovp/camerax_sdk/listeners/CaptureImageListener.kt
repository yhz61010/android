package com.leovp.camerax_sdk.listeners

import android.net.Uri

/**
 * Author: Michael Leo
 * Date: 2022/4/25 14:23
 */
interface CaptureImageListener {
    fun onSavedImageUri(savedUri: Uri)
    fun onSavedImageBytes(imageBytes: ByteArray, width: Int, height: Int, rotationDegrees: Int)
}