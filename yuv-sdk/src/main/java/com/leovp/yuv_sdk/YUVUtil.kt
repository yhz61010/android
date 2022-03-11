package com.leovp.yuv_sdk

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/3/10 16:41
 */
@Keep
object YUVUtil {
    init {
        System.loadLibrary("leo-yuv")
    }

    /**
     * Mirror(height only) first then do rotate
     *
     * @param type 1: No rotate
     *
     */
    external fun convertToI420(yuvByteArray: ByteArray, format: Int, width: Int, height: Int, type: Int): ByteArray
    external fun convertToI420NegativeStride(yuvByteArray: ByteArray, width: Int, height: Int, type: Int): ByteArray

    external fun rotateI420(i420ByteArray: ByteArray, width: Int, height: Int, degree: Int): ByteArray
    external fun mirrorI420(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray
}