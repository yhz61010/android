package com.leovp.camera2live.base

import android.hardware.camera2.CameraMetadata
import android.media.Image
import com.leovp.androidbase.utils.media.YuvUtil
import com.leovp.camera2live.base.iters.IDataProcessStrategy

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:12
 */
class YuvEncoderStrategy : IDataProcessStrategy {
    override fun doProcess(image: Image, lensFacing: Int, cameraSensorOrientation: Int): ByteArray {
        val width = image.width
        val height = image.height

        val yuvData = YuvUtil.getYuvDataFromImage(image, YuvUtil.COLOR_FORMAT_I420)
        return if (lensFacing == CameraMetadata.LENS_FACING_BACK) {
            // LENS_FACING_BACK
            YuvUtil.yuvRotate90(yuvData, width, height)
        } else {
            // LENS_FACING_FRONT
            YuvUtil.yuvRotate90(YuvUtil.yuvFlipHorizontal(yuvData, width, height), width, height)
        }
    }
}