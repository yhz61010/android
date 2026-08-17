package com.leovp.camera2live.utils

import com.leovp.yuv.YuvUtil

internal const val DEFAULT_BACK_RECORDING_ROTATION_DEGREES = 90
internal const val DEFAULT_FRONT_RECORDING_ROTATION_DEGREES = 270

internal data class FrameDimensions(val width: Int, val height: Int)

internal data class TransformedI420Frame(
    val data: ByteArray,
    val dimensions: FrameDimensions,
)

internal fun requireSupportedRecordingRotation(rotationDegrees: Int) {
    require(
        rotationDegrees == 0 ||
            rotationDegrees == 90 ||
            rotationDegrees == 180 ||
            rotationDegrees == 270
    ) { "Unsupported recording rotation: $rotationDegrees" }
}

internal fun resolveRecordingRotation(
    rotationDegrees: Int?,
    frontFacing: Boolean,
): Int {
    val resolvedRotation = rotationDegrees ?: if (frontFacing) {
        DEFAULT_FRONT_RECORDING_ROTATION_DEGREES
    } else {
        DEFAULT_BACK_RECORDING_ROTATION_DEGREES
    }
    requireSupportedRecordingRotation(resolvedRotation)
    return resolvedRotation
}

internal fun getRotatedFrameDimensions(
    width: Int,
    height: Int,
    rotationDegrees: Int,
): FrameDimensions {
    require(width > 0 && height > 0) { "Frame dimensions must be positive: ${width}x$height" }
    requireSupportedRecordingRotation(rotationDegrees)
    return if (rotationDegrees == 90 || rotationDegrees == 270) {
        FrameDimensions(height, width)
    } else {
        FrameDimensions(width, height)
    }
}

internal fun transformI420Frame(
    i420Data: ByteArray,
    width: Int,
    height: Int,
    rotationDegrees: Int,
    mirrorHorizontally: Boolean,
): TransformedI420Frame {
    val outputDimensions = getRotatedFrameDimensions(width, height, rotationDegrees)
    val rotatedData = if (rotationDegrees == YuvUtil.ROTATE_0) {
        i420Data
    } else {
        YuvUtil.rotateI420(i420Data, width, height, rotationDegrees)
    }
    val outputData = if (mirrorHorizontally) {
        YuvUtil.mirrorI420(
            rotatedData,
            outputDimensions.width,
            outputDimensions.height
        )
    } else {
        rotatedData
    }
    return TransformedI420Frame(outputData, outputDimensions)
}
