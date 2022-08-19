package com.leovp.camera2live.base.encode_strategies

import android.hardware.camera2.CameraMetadata
import android.media.Image
import com.leovp.androidbase.utils.media.YuvUtil
import com.leovp.camera2live.base.iters.IDataProcessStrategy

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:12
 */
class EncoderStrategyYuv420Sp : IDataProcessStrategy {
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

        return if (lensFacing == CameraMetadata.LENS_FACING_BACK) {
            // Step 2.1
            // YuvUtil.rotateYUV420Degree90(imageBytes, width, height)

            // or Step 2.2
            // val rotateI420 = com.leovp.yuv_sdk.YuvUtil.convertToI420(nv21Bytes, com.leovp.yuv_sdk.YuvUtil.NV21, width, height, false, com.leovp.yuv_sdk.YuvUtil.Rotate_90)!!
            // com.leovp.yuv_sdk.YuvUtil.i420ToNv21(rotateI420, height, width)

            // or Step 2.3
            val rotateI420 = com.leovp.yuv_sdk.YuvUtil.rotateI420(i420Bytes, width, height, com.leovp.yuv_sdk.YuvUtil.Rotate_90)
            com.leovp.yuv_sdk.YuvUtil.i420ToNv12(rotateI420, height, width)
        } else {
            // Front lens
            return when (cameraSensorOrientation) {
                90 -> {
                    // Nexus phone(like Nexus 6 and Nexus 6P), the front lens cameraSensorOrientation is 90 not 270 degrees
                    // Tested on Nexus 6 and Nexus 6P
                    YuvUtil.mirrorNv21(i420Bytes, width, height)
                    YuvUtil.rotateYUV420Degree270(i420Bytes, width, height)
                }
                else -> /* 270 */ YuvUtil.rotateYUVDegree270AndMirror(i420Bytes, width, height)
            }
        }
    }
}
