package com.leovp.camera2live.base.encode_strategies

import android.hardware.camera2.CameraMetadata
import android.media.Image
import com.leovp.androidbase.utils.media.YuvUtil
import com.leovp.camera2live.base.iters.IDataProcessStrategy
import com.leovp.yuv_sdk.YUVUtil

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:12
 */
class EncoderStrategyYuv420P : IDataProcessStrategy {
    private val yuvUtil = YUVUtil()
    override fun doProcess(image: Image, lensFacing: Int, cameraSensorOrientation: Int): ByteArray {
        val width = image.width
        val height = image.height
        // Get I420/YU12(YUV420P) data YYYYYYYYUUVV
        val yuvData = YuvUtil.getYuvDataFromImage(image, YuvUtil.COLOR_FORMAT_I420)
//        val yuvData = YuvUtil.getBytesFromImage(image)
        return if (lensFacing == CameraMetadata.LENS_FACING_BACK) {
            // LENS_FACING_BACK
            yuvUtil.mirrorI420(yuvData, width, height)
//            YuvUtil.yuvRotate90(yuvUtil.mirrorI420(yuvData, width, height), width, height)

//            yuvUtil.convertToI420(yuvData, width, height, 1)
        } else {
            // LENS_FACING_FRONT
            YuvUtil.yuvRotate90(YuvUtil.yuvFlipHorizontal(yuvData, width, height), width, height)
        }
    }
}