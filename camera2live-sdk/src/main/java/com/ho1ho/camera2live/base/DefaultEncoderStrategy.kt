package com.ho1ho.camera2live.base

import android.hardware.camera2.CameraMetadata
import android.media.Image
import com.ho1ho.androidbase.utils.media.YuvUtil
import com.ho1ho.camera2live.base.iters.CameraSensorOrientation
import com.ho1ho.camera2live.base.iters.IDataProcessStrategy

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:12
 */
class DefaultEncoderStrategy : IDataProcessStrategy {
    override fun doProcess(image: Image, lensFacing: Int, @CameraSensorOrientation cameraSensorOrientation: Int): ByteArray {
        val width = image.width
        val height = image.height
        val imageBytes = YuvUtil.getBytesFromImage(image)
        return if (lensFacing == CameraMetadata.LENS_FACING_BACK) {
            YuvUtil.rotateYUV420Degree90(imageBytes, width, height)
        } else {
            // Front lens
            return when (cameraSensorOrientation) {
                90 -> {
                    // Nexus phone(like Nexus 6 and Nexus 6P), the front lens cameraSensorOrientation is 90 not 270 degrees
                    // Tested on Nexus 6 and Nexus 6P
                    YuvUtil.mirrorNv21(imageBytes, width, height)
                    YuvUtil.rotateYUV420Degree270(imageBytes, width, height)
                }
                else /* 270 */ -> YuvUtil.rotateYUVDegree270AndMirror(imageBytes, width, height)
            }
        }
    }
}