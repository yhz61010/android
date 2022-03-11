package com.leovp.yuv_sdk

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/3/10 16:41
 */
@Keep
class YUVUtil {
    companion object {
        init {
            System.loadLibrary("leo-yuv")
        }
    }

    /**
     * @param type 1
     *
     */
    external fun convertToI420(nv21ByteArray: ByteArray, width: Int, height: Int, type: Int): ByteArray
    external fun convertToI420NegativeStride(nv21ByteArray: ByteArray, width: Int, height: Int, type: Int): ByteArray

    external fun rotateI420(i420ByteArray: ByteArray, width: Int, height: Int, degree: Int): ByteArray
    external fun mirrorI420(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray
}