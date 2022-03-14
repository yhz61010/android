package com.leovp.yuv_sdk

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/3/10 16:41
 */
@Keep
object YuvUtil {
    init {
        System.loadLibrary("leo-yuv")
    }

    /**
     * Mirror(height only) first then do rotate.
     *
     * @param width The original video width.
     * @param height The original video height.
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
     * @param width The original video width before rotation.
     * @param height The original video height before rotation.
     * @param degree    0: No rotation.
     *                 90: Rotate 90 degrees clockwise.
     *                180: Rotate 180 degrees.
     *                270: Rotate 270 degrees clockwise.
     */
    external fun rotateI420(i420ByteArray: ByteArray, width: Int, height: Int, degree: Int): ByteArray

    /**
     * @param width The original video width.
     * @param height The original video height.
     */
    external fun mirrorI420(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray

    /**
     * @param srcWidth The original video width.
     * @param srcHeight The original video height.
     * @param dstWidth The width after scaled.
     * @param dstHeight The height after scaled.
     *
     * @param mode
     *              kFilterNone = 0,      // Point sample; Fastest.
     *              kFilterLinear = 1,    // Filter horizontally only.
     *              kFilterBilinear = 2,  // Faster than box, but lower quality scaling down.
     *              kFilterBox = 3        // Highest quality.
     */
    external fun scaleI420(i420ByteArray: ByteArray, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, mode: Int): ByteArray

    /**
     * @param srcWidth The original video width.
     * @param srcHeight The original video height.
     * @param dstWidth The width after cropped.
     * @param dstHeight The height after cropped.
     * @param left The left position of cropping. Must be an even number.
     * @param top The top position of cropping. Must be an even number.
     **/
    external fun cropI420(i420ByteArray: ByteArray, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, left: Int, top: Int): ByteArray?
}