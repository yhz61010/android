package com.leovp.camerax_sdk.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Range
import com.leovp.lib_common_android.exts.screenAvailableResolution
import com.leovp.lib_common_android.exts.windowManager
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 2022/4/26 13:55
 */

// Camera2 API supported the MAX width and height
fun Context.getPreviewViewMaxWidth(): Int {
    val screenSize = screenAvailableResolution
    return max(screenSize.width, screenSize.height)
}

fun Context.getPreviewViewMaxHeight(): Int {
    val screenSize = screenAvailableResolution
    return min(screenSize.width, screenSize.height)
}

fun CameraCharacteristics.getConfigMap(): StreamConfigurationMap {
    return get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
}

fun CameraCharacteristics.isFlashSupported(): Boolean =
        get(CameraCharacteristics.FLASH_INFO_AVAILABLE)!!

fun CameraCharacteristics.hardwareLevel(): Int =
        get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!

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

fun CameraCharacteristics.cameraSensorOrientation(): Int =
        get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1

fun Context.getDeviceRotation(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: -1
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.rotation
    }
}