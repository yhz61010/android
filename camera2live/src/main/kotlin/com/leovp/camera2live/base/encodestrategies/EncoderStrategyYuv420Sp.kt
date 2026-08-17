package com.leovp.camera2live.base.encodestrategies

import android.hardware.camera2.CameraMetadata
import android.media.Image
import com.leovp.androidbase.utils.media.YuvUtil
import com.leovp.camera2live.base.iters.IDataProcessStrategy
import com.leovp.camera2live.utils.requireSupportedRecordingRotation
import com.leovp.camera2live.utils.resolveRecordingRotation
import com.leovp.camera2live.utils.transformI420Frame
import com.leovp.yuv.YuvUtil as NativeYuvUtil

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:12
 */
class EncoderStrategyYuv420Sp(
    private val recordingRotationDegrees: Int? = null,
) : IDataProcessStrategy {
    init {
        recordingRotationDegrees?.let(::requireSupportedRecordingRotation)
    }

    override fun doProcess(image: Image, lensFacing: Int, cameraSensorOrientation: Int): ByteArray {
        val width = image.width
        val height = image.height

        // Step 1.1
        // val imageBytes = YuvUtil.getBytesFromImage(image)

        // or Step 1.2
        // Get NV21(YUV420SP) data YYYYYYYY VUVU
        // val nv21Bytes = YuvUtil.getYuvDataFromImage(image, YuvUtil.COLOR_FORMAT_NV21)

        // or Step 1.3
        // Get I420(YUV420P) data YYYYYYYY UUVV
        val i420Bytes = YuvUtil.getYuvDataFromImage(image, YuvUtil.COLOR_FORMAT_I420)
        val frontFacing = lensFacing == CameraMetadata.LENS_FACING_FRONT
        val resolvedRotationDegrees = resolveRecordingRotation(
            recordingRotationDegrees,
            frontFacing
        )

        val transformedFrame = transformI420Frame(
            i420Bytes,
            width,
            height,
            resolvedRotationDegrees,
            frontFacing
        )
        return NativeYuvUtil.i420ToNv12(
            transformedFrame.data,
            transformedFrame.dimensions.width,
            transformedFrame.dimensions.height
        )
    }
}
