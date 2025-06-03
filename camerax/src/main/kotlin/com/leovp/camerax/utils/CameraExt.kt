package com.leovp.camerax.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Range
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.android.exts.windowManager
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 2022/4/26 13:55
 */

// Camera2 API supported the MAX width and height
internal fun Context.getPreviewViewMaxWidth(): Int {
    val screenSize = screenAvailableResolution
    return max(screenSize.width, screenSize.height)
}

internal fun Context.getPreviewViewMaxHeight(): Int {
    val screenSize = screenAvailableResolution
    return min(screenSize.width, screenSize.height)
}

internal fun CameraCharacteristics.getConfigMap(): StreamConfigurationMap {
    return get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
}

internal fun CameraCharacteristics.isFlashSupported(): Boolean = get(CameraCharacteristics.FLASH_INFO_AVAILABLE)!!

internal fun CameraCharacteristics.hardwareLevel(): Int = get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!

internal fun CameraCharacteristics.hardwareLevelName(): String {
    return when (get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!) {
        0 -> "LIMIT"
        1 -> "FULL"
        2 -> "LEGACY"
        3 -> "LEVEL_3"
        else -> "NA"
    }
}

internal fun CameraCharacteristics.supportedFpsRanges(): Array<Range<Int>> =
    get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!

internal fun CameraCharacteristics.cameraSensorOrientation(): Int = get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1

/**
 * @return Return the screen rotation(**NOT** device rotation).
 *         The result is one of the following value:
 *
 * - Surface.ROTATION_0
 * - Surface.ROTATION_90
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270
 */
internal val Context.screenSurfaceRotation: Int
    @Suppress("DEPRECATION")
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display.rotation else windowManager.defaultDisplay.rotation
