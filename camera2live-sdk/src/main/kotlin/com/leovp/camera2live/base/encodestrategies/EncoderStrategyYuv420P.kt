package com.leovp.camera2live.base.encodestrategies

import android.hardware.camera2.CameraMetadata
import android.media.Image
import com.leovp.androidbase.utils.media.YuvUtil
import com.leovp.camera2live.base.iters.IDataProcessStrategy

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:12
 */
class EncoderStrategyYuv420P : IDataProcessStrategy {
    override fun doProcess(image: Image, lensFacing: Int, cameraSensorOrientation: Int): ByteArray {
        val width = image.width
        val height = image.height
        // Get I420(YU12)|(YUV420P) data YYYYYYYY UUVV
        val yuvData = YuvUtil.getYuvDataFromImage(image, YuvUtil.COLOR_FORMAT_I420)
        return if (lensFacing == CameraMetadata.LENS_FACING_BACK) {
            // LENS_FACING_BACK
            // YuvUtil.yuvRotate90(yuvData, width, height)

            // If you uncomment these two lines, you need also to modify [CameraAvcEncoder] at line 46,
            // modify the width and height to `width / 2`, `height / 2`
            // val scaleI420 = com.leovp.yuv_sdk.YuvUtil.scaleI420(yuvData, width, height, width / 2, height / 2,
            // com.leovp.yuv_sdk.YuvUtil.SCALE_FILTER_NONE)
            // com.leovp.yuv_sdk.YuvUtil.rotateI420(scaleI420, width / 2, height / 2,
            // com.leovp.yuv_sdk.YuvUtil.Rotate_90)
            com.leovp.yuv.YuvUtil.rotateI420(yuvData, width, height, com.leovp.yuv.YuvUtil.Rotate_90)
        } else {
            // LENS_FACING_FRONT
            // YuvUtil.yuvRotate90(YuvUtil.yuvFlipHorizontal(yuvData, width, height), width, height)
            // com.leovp.yuv_sdk.YuvUtil.rotateI420(YUVUtil.mirrorI420(yuvData, width, height), width, height,
            // com.leovp.yuv_sdk.YuvUtil.Rotate_90)

            // Mirror(height only) first then do rotate
            com.leovp.yuv.YuvUtil.convertToI420(
                yuvData, com.leovp.yuv.YuvUtil.I420, width, height, true, com.leovp.yuv.YuvUtil.Rotate_270
            )!!
            // com.leovp.yuv_sdk.YuvUtil.android420ToI420(yuvData, 1, width, height, true,
            // com.leovp.yuv_sdk.YuvUtil.Rotate_270)!!
        }
    }
}
