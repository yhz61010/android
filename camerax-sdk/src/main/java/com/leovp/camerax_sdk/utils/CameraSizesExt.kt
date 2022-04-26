@file:Suppress("unused")

/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.leovp.camerax_sdk.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import android.view.Display
import com.leovp.lib_common_android.exts.getRealResolution
import kotlin.math.max
import kotlin.math.min

/** Helper class used to pre-compute shortest and longest sides of a [Size] */
class SmartSize(width: Int, height: Int) {
    var size = Size(width, height)
    var long = max(size.width, size.height)
    var short = min(size.width, size.height)
    override fun toString() = "SmartSize(${long}x${short})"
}

/** Standard High Definition size for pictures and video */
val SIZE_1080P: SmartSize = SmartSize(1920, 1080)

/** Returns a [SmartSize] object for the given [Display] */
fun getDisplaySmartSize(ctx: Context): SmartSize {
    val outSize = ctx.getRealResolution()
    return SmartSize(outSize.width, outSize.height)
}

/** Returns a [SmartSize] object for the given [Size] */
fun getDisplaySmartSize(designSize: Size): SmartSize {
    return SmartSize(designSize.width, designSize.height)
}

/**
 * Returns the largest available PREVIEW size. For more information, see:
 * [CameraDevice](https://d.android.com/reference/android/hardware/camera2/CameraDevice) and
 * [StreamConfigurationMap](https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap)
 *
 * @param targetClass The following list is generally usable for outputs:
 * - [android.media.ImageReader] - Recommended for image processing or streaming to external resources (such as a file or network)
 * - [android.media.MediaRecorder] - Recommended for recording video (simple to use)
 * - [android.media.MediaCodec] - Recommended for recording video (more complicated to use, with more flexibility)
 * - [android.renderscript.Allocation] - Recommended for image processing with {@link android.renderscript RenderScript}
 * - [android.view.SurfaceHolder] - Recommended for low-power camera preview with {@link android.view.SurfaceView}
 * - [android.graphics.SurfaceTexture] - Recommended for OpenGL-accelerated preview processing or compositing with
 * - [android.view.TextureView]
 * @param format an image format from either `ImageFormat` or `PixelFormat`
 */
fun <T> getPreviewOutputSize(
    designSize: Size,
    characteristics: CameraCharacteristics,
    targetClass: Class<T>,
    format: Int? = null
): Size {
    val smartSize = getDisplaySmartSize(designSize)
    // Find which is smaller: designSize or 1080p
    val hdScreen = smartSize.long >= SIZE_1080P.long || smartSize.short >= SIZE_1080P.short
    val maxSize = if (hdScreen) SIZE_1080P else smartSize

    // If image format is provided, use it to determine supported sizes; else use target class
    val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
    if (format == null) require(StreamConfigurationMap.isOutputSupportedFor(targetClass)) else require(config.isOutputSupportedFor(format))
    val allSizes = if (format == null) config.getOutputSizes(targetClass) else config.getOutputSizes(format)

    // Get available sizes and sort them by area from largest to smallest
    val validSizes = allSizes.sortedWith(compareBy { it.height * it.width }).map { SmartSize(it.width, it.height) }.reversed()

    // Then, get the largest output size that is smaller or equal than our max size
    val alphaSize = validSizes.first { it.long <= maxSize.long && it.short <= maxSize.short }
    return if (alphaSize.long < maxSize.short) {
        validSizes.first { it.long <= maxSize.long && it.short <= maxSize.short * 1.5F }.size
    } else {
        alphaSize.size
    }
}

fun <T> getPreviewOutputSize(
    ctx: Context,
    characteristics: CameraCharacteristics,
    targetClass: Class<T>,
    format: Int? = null
): Size {
    val screenSize = getDisplaySmartSize(ctx)
    return getPreviewOutputSize(Size(screenSize.short, screenSize.long), characteristics, targetClass, format)
}