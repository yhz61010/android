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

package com.leovp.camerax.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import com.leovp.android.exts.SmartSize
import com.leovp.android.exts.getRatio
import com.leovp.android.exts.screenRealResolution

/** Screen size for pictures and video */
internal val SIZE_SD_576P: SmartSize = SmartSize(720, 576)
internal val SIZE_HD_720P: SmartSize = SmartSize(1280, 720) // 16:9
internal val SIZE_FHD_1080P: SmartSize = SmartSize(1920, 1080) // 16:9
internal val SIZE_4K_UHD_2160P_TV: SmartSize = SmartSize(3840, 2160) // 16:9
internal val SIZE_4K_DCI_2160P: SmartSize = SmartSize(4096, 2160) // Used by Digital Cinemas (DCI)
internal val SIZE_8K_UHD_4320P_TV: SmartSize = SmartSize(7680, 4320) // TV format in Japan from 2020 on

/** Returns a [SmartSize] object for the given [Display] */
internal fun getDisplaySmartSize(ctx: Context): SmartSize {
    val outSize = ctx.screenRealResolution
    return SmartSize(outSize.width, outSize.height)
}

/** Returns a [SmartSize] object for the given [Size] */
internal fun getDisplaySmartSize(designSize: Size): SmartSize = SmartSize(designSize.width, designSize.height)

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
internal fun <T> getPreviewOutputSize(
    designSize: Size,
    characteristics: CameraCharacteristics,
    targetClass: Class<T>,
    format: Int? = null,
): Size {
    val smartSize = getDisplaySmartSize(designSize)
    // Find which is smaller: designSize or 1080p
    val uhdScreen =
        smartSize.long >= SIZE_4K_UHD_2160P_TV.long || smartSize.short >= SIZE_4K_UHD_2160P_TV.short
    val maxSize = if (uhdScreen) SIZE_4K_UHD_2160P_TV else smartSize

    // If image format is provided, use it to determine supported sizes; else use target class
    val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
    if (format == null) {
        require(StreamConfigurationMap.isOutputSupportedFor(targetClass))
    } else {
        require(config.isOutputSupportedFor(format))
    }
    val allSizes =
        if (format == null) config.getOutputSizes(targetClass) else config.getOutputSizes(format)

    // Get available sizes and sort them by area from largest to smallest
    val validSizes =
        allSizes.sortedWith(compareBy { it.height * it.width })
            .map { SmartSize(it.width, it.height) }
            .reversed()

    // Then, get the largest output size that is smaller or equal than our max size
    val alphaSize = validSizes.first { it.long <= maxSize.long && it.short <= maxSize.short }
    return if (alphaSize.long < maxSize.short) {
        validSizes.first { it.long <= maxSize.long && it.short <= maxSize.short * 1.5F }.size
    } else {
        alphaSize.size
    }
}

internal fun <T> getPreviewOutputSize(
    ctx: Context,
    characteristics: CameraCharacteristics,
    targetClass: Class<T>,
    format: Int? = null,
): Size {
    val screenSize = getDisplaySmartSize(ctx)
    return getPreviewOutputSize(
        Size(screenSize.short, screenSize.long),
        characteristics,
        targetClass,
        format
    )
}

internal fun CameraCharacteristics.getCameraSupportedSize(): Array<SmartSize> =
    getConfigMap().getOutputSizes(SurfaceHolder::class.java)
        .map { SmartSize(it.width, it.height) }
        .toTypedArray()

internal fun CameraCharacteristics.getCameraSupportedSizeMap(): Map<String, List<SmartSize>> =
    getCameraSupportedSize().groupBy {
        getRatio(it)!!
    }

internal fun getSpecificPreviewOutputSize(
    context: Context,
    desiredVideoWidth: Int,
    desiredVideoHeight: Int,
    characteristics: CameraCharacteristics,
): Size {
    // Generally, if the device is in portrait(Surface.ROTATION_0),
    // the camera SENSOR_ORIENTATION(90) is just in landscape and vice versa.
    // Example: deviceRotation: 0
    val deviceRotation = context.screenSurfaceRotation
    // Example: cameraSensorOrientation: 90
    val cameraSensorOrientation = characteristics.cameraSensorOrientation()
    var swapDimension = false
    when (deviceRotation) {
        Surface.ROTATION_0,
        Surface.ROTATION_180 -> if (cameraSensorOrientation == 90 || cameraSensorOrientation == 270) {
            swapDimension = true
        }

        Surface.ROTATION_90,
        Surface.ROTATION_270 -> if (cameraSensorOrientation == 0 || cameraSensorOrientation == 180) {
            swapDimension = true
        }

        else -> throw IllegalAccessException("Display rotation is invalid: $deviceRotation")
    }

    // The device is normally in portrait by default.
    // Actually, the camera orientation is just 90 degree anticlockwise.
    var cameraWidth = desiredVideoHeight
    var cameraHeight = desiredVideoWidth

    // Landscape: true. Portrait: false
    if (swapDimension) {
        cameraWidth = desiredVideoWidth
        cameraHeight = desiredVideoHeight
    }
    if (cameraWidth > context.getPreviewViewMaxHeight()) cameraWidth = context.getPreviewViewMaxHeight()
    if (cameraHeight > context.getPreviewViewMaxWidth()) cameraHeight = context.getPreviewViewMaxWidth()

    // Calculate ImageReader input preview size from supported size list by camera.
    // Using configMap.getOutputSizes(SurfaceTexture.class) to get supported size list.
    // Attention: The returned value is in camera orientation. NOT in device orientation.
    val selectedSizeFromCamera: Size = getPreviewOutputSize(
        Size(cameraWidth, cameraHeight),
        characteristics,
        SurfaceHolder::class.java
    )

    // Take care of the result value. It's in camera orientation.
    // Swap the selectedPreviewSizeFromCamera is necessary. So that we can use the proper size for CameraTextureView.
    val previewSize: Size = if (swapDimension) {
        Size(selectedSizeFromCamera.height, selectedSizeFromCamera.width)
    } else {
        selectedSizeFromCamera
    }

    return previewSize
}

internal fun CameraCharacteristics.getCameraSizeMapForOutput(): Map<String, List<SmartSize>> {
    val outputSizeMap = HashMap<String, List<SmartSize>>()
    this.getCameraSupportedSizeMap().onEach { (ratio, sizeList) ->
        // Get available sizes and sort them by area from smallest to largest
        val allSize = sizeList.sortedWith(compareBy { it.long * it.short })
        val maxSize = allSize[allSize.size - 1]

        val targetMaxValue = (maxSize.long shr 1) * (maxSize.short shr 1) * 1.5f

        // Get the first size that greater or equal then targetMaxValue.
        val filteredMaxSize: SmartSize? =
            allSize.firstOrNull { it.long * it.short >= targetMaxValue }
        val filteredList: List<SmartSize> =
            filteredMaxSize?.let { listOf(maxSize, it) } ?: listOf(maxSize)
        outputSizeMap[ratio] = filteredList
    }
    return outputSizeMap
}
