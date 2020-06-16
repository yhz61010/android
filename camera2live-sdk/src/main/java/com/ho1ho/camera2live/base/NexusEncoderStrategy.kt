package com.ho1ho.camera2live.base

import android.hardware.camera2.CameraMetadata
import android.media.Image
import com.ho1ho.androidbase.utils.media.YuvUtil
import com.ho1ho.camera2live.base.iters.IDataProcessStrategy

/**
 * Nexus phone(like Nexus 6 and Nexus 6P), the front lens cameraSensorOrientation is 90 not 270 degrees
 *
 * Author: Michael Leo
 * Date: 20-4-1 下午7:40
 */
class NexusEncoderStrategy : IDataProcessStrategy {
    override fun doProcess(image: Image, lensFacing: Int): ByteArray {
        val width = image.width
        val height = image.height
        val imageBytes = YuvUtil.getBytesFromImage(image)
        return if (lensFacing == CameraMetadata.LENS_FACING_BACK) {
            YuvUtil.rotateYUV420Degree90(imageBytes, width, height)
        } else {
            // Tested on Nexus 6 and Nexus 6P
            YuvUtil.mirrorNv21(imageBytes, width, height)
            YuvUtil.rotateYUV420Degree270(imageBytes, width, height)
        }
    }
}