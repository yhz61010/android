package com.leovp.camerax_sdk.listeners

import com.leovp.camerax_sdk.bean.CaptureImage

/**
 * Author: Michael Leo
 * Date: 2022/4/25 14:23
 */
interface CaptureImageListener {
    fun onSavedImageFile(savedImage: CaptureImage.ImageUri)
    fun onSavedImageBytes(savedImage: CaptureImage.ImageBytes)
}