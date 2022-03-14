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
     * @param format The [srcYuvByteArray] format.
     * ```
     *               1: I420
     *               2: NV21
     *               3: NV12
     *               4: YUY2
     * ```
     *
     * @param degree The yuv data should be rotated by degree.
     * ```
     *                 0: No rotation.
     *                 90: Rotate 90 degrees clockwise.
     *                180: Rotate 180 degrees.
     *                270: Rotate 270 degrees clockwise.
     * ```
     */
    external fun convertToI420(srcYuvByteArray: ByteArray, format: Int, width: Int, height: Int, verticallyFlip: Boolean, degree: Int): ByteArray?

    /**
     * @param degree    0: No rotation.
     *                 90: Rotate 90 degrees clockwise.
     *                180: Rotate 180 degrees.
     *                270: Rotate 270 degrees clockwise.
     */
    external fun rotateI420(i420ByteArray: ByteArray, width: Int, height: Int, degree: Int): ByteArray
    external fun mirrorI420(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray
}