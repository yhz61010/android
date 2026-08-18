package com.leovp.camera2live.base.encodestrategies

import android.hardware.camera2.CameraMetadata
import android.media.Image
import com.leovp.androidbase.utils.media.YuvUtil
import com.leovp.camera2live.base.iters.IDataProcessStrategy
import com.leovp.camera2live.utils.requireSupportedRecordingRotation
import com.leovp.camera2live.utils.resolveRecordingRotation
import com.leovp.camera2live.utils.transformI420Frame

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:12
 */
class EncoderStrategyYuv420P(private val recordingRotationDegrees: Int? = null) :
    IDataProcessStrategy {
    init {
        recordingRotationDegrees?.let(::requireSupportedRecordingRotation)
    }

    override fun doProcess(image: Image, lensFacing: Int, cameraSensorOrientation: Int): ByteArray {
        val width = image.width
        val height = image.height
        // Get I420(YU12)|(YUV420P) data YYYYYYYY UUVV
        val yuvData = YuvUtil.getYuvDataFromImage(image, YuvUtil.COLOR_FORMAT_I420)
        val frontFacing = lensFacing == CameraMetadata.LENS_FACING_FRONT
        val resolvedRotationDegrees = resolveRecordingRotation(
            recordingRotationDegrees,
            frontFacing
        )
        return transformI420Frame(
            yuvData,
            width,
            height,
            resolvedRotationDegrees,
            frontFacing
        ).data
    }
}
