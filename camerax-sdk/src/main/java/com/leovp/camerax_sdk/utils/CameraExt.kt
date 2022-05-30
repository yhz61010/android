package com.leovp.camerax_sdk.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import com.leovp.lib_common_android.exts.SmartSize
import com.leovp.lib_common_android.exts.getAvailableResolution
import com.leovp.lib_common_android.exts.windowManager
import com.leovp.log_sdk.LogContext
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 2022/4/26 13:55
 */

private const val TAG = "CameraExt"

// Camera2 API supported the MAX width and height
fun Context.getPreviewViewMaxWidth(): Int {
    val screenSize = getAvailableResolution()
    return max(screenSize.width, screenSize.height)
}

fun Context.getPreviewViewMaxHeight(): Int {
    val screenSize = getAvailableResolution()
    return min(screenSize.width, screenSize.height)
}

fun CameraCharacteristics.getConfigMap(): StreamConfigurationMap {
    return get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
}

fun CameraCharacteristics.isFlashSupported(): Boolean = get(CameraCharacteristics.FLASH_INFO_AVAILABLE)!!
fun CameraCharacteristics.hardwareLevel(): Int = get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
fun CameraCharacteristics.hardwareLevelName(): String {
    return when (get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!) {
        0    -> "LIMIT"
        1    -> "FULL"
        2    -> "LEGACY"
        3    -> "LEVEL_3"
        else -> "NA"
    }
}

fun CameraCharacteristics.supportedFpsRanges(): Array<Range<Int>> =
        get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!

fun CameraCharacteristics.cameraSensorOrientation(): Int = get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1

fun Context.getDeviceRotation(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: -1
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.rotation
    }
}

fun CameraCharacteristics.getCameraSupportedSize(): Array<SmartSize> {
    return getConfigMap().getOutputSizes(SurfaceHolder::class.java)
        .map { SmartSize(it.width, it.height) }
        .toTypedArray()
}

fun getSpecificPreviewOutputSize(context: Context,
    desiredVideoWidth: Int,
    desiredVideoHeight: Int,
    characteristics: CameraCharacteristics): Size {
    // Generally, if the device is in portrait(Surface.ROTATION_0),
    // the camera SENSOR_ORIENTATION(90) is just in landscape and vice versa.
    // Example: deviceRotation: 0
    val deviceRotation = context.getDeviceRotation()
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
        else                 -> LogContext.log.e(TAG, "Display rotation is invalid: $deviceRotation")
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
    val selectedSizeFromCamera: Size =
            getPreviewOutputSize(Size(cameraWidth, cameraHeight), characteristics, SurfaceHolder::class.java)

    // Take care of the result value. It's in camera orientation.
    // Swap the selectedPreviewSizeFromCamera is necessary. So that we can use the proper size for CameraTextureView.
    val previewSize: Size = if (swapDimension) {
        Size(selectedSizeFromCamera.height, selectedSizeFromCamera.width)
    } else {
        selectedSizeFromCamera
    }

    return previewSize
}