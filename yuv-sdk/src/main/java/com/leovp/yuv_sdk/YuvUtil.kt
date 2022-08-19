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

    const val I420 = 1
    const val NV21 = 2
    const val NV12 = 3
    const val YUY2 = 4

    const val Rotate_0 = 0 // No rotation.
    const val Rotate_90 = 90 // Rotate 90 degrees clockwise.
    const val Rotate_180 = 180 // Rotate 180 degrees.
    const val Rotate_270 = 270 // Rotate 270 degrees clockwise.

    const val SCALE_FILTER_NONE = 0 // Point sample; Fastest.
    const val SCALE_FILTER_LINEAR = 1 // Filter horizontally only.
    const val SCALE_FILTER_BILINEAR = 2 // Faster than box, but lower quality scaling down.
    const val SCALE_FILTER_BOX = 3 // Highest quality.

    /**
     * Maybe the [convertToI420] method is what you want. Otherwise I find a way to get [pixelStrideUV] automatically.
     *
     * @param pixelStrideUV How can I get this parameter automatically?
     *                      1: I420
     *                      2: NV21/NV12
     */
    external fun android420ToI420(
        srcYuvByteArray: ByteArray,
        pixelStrideUV: Int,
        width: Int,
        height: Int,
        verticallyFlip: Boolean,
        degree: Int = Rotate_0
    ): ByteArray?

    /**
     * Convert specified YUV data to I420 with vertically flipping and rotating at the same time.
     *
     * Vertically flip yuv data first then do rotate.
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
     *                  0: No rotation.
     *                 90: Rotate 90 degrees clockwise.
     *                180: Rotate 180 degrees.
     *                270: Rotate 270 degrees clockwise.
     * ```
     */
    external fun convertToI420(
        srcYuvByteArray: ByteArray,
        format: Int,
        width: Int,
        height: Int,
        verticallyFlip: Boolean,
        degree: Int = Rotate_0
    ): ByteArray?

    /**
     * @param width The original video width before rotation.
     * @param height The original video height before rotation.
     * @param degree The yuv data should be rotated by degree.
     * ```
     *                  0: No rotation.
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

    external fun flipVerticallyI420(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray

    /**
     * @param srcWidth The original video width.
     * @param srcHeight The original video height.
     * @param dstWidth The width after scaled.
     * @param dstHeight The height after scaled.
     *
     * @param mode
     *              kFilterNone = 0,     // Point sample; Fastest.
     *              kFilterLinear = 1,   // Filter horizontally only.
     *              kFilterBilinear = 2, // Faster than box, but lower quality scaling down.
     *              kFilterBox = 3       // Highest quality.
     */
    external fun scaleI420(
        i420ByteArray: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        mode: Int = SCALE_FILTER_NONE
    ): ByteArray

    /**
     * @param srcWidth The original video width.
     * @param srcHeight The original video height.
     * @param dstWidth The width after cropped.
     * @param dstHeight The height after cropped.
     * @param left The left position of cropping. Must be an even number.
     * @param top The top position of cropping. Must be an even number.
     **/
    external fun cropI420(
        i420ByteArray: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        left: Int,
        top: Int
    ): ByteArray?

    external fun i420ToNv21(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray

    external fun i420ToNv12(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray

    external fun nv21ToI420(nv21ByteArray: ByteArray, width: Int, height: Int): ByteArray

    external fun nv12ToI420(nv21ByteArray: ByteArray, width: Int, height: Int, degree: Int = Rotate_0): ByteArray

    // --------------------

    /**
     * @param width The original video width.
     * @param height The original video height.
     */
    external fun mirrorNv12(nv12ByteArray: ByteArray, width: Int, height: Int): ByteArray

    /**
     * @param srcWidth The original video width.
     * @param srcHeight The original video height.
     * @param dstWidth The width after scaled. Make sure it's the multiple of 8.
     * @param dstHeight The height after scaled. Make sure it's the multiple of 8.
     *
     * @param mode
     *              kFilterNone = 0,     // Point sample; Fastest.
     *              kFilterLinear = 1,   // Filter horizontally only.
     *              kFilterBilinear = 2, // Faster than box, but lower quality scaling down.
     *              kFilterBox = 3       // Highest quality.
     */
    external fun scaleNv12(
        nv12ByteArray: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        mode: Int = SCALE_FILTER_NONE
    ): ByteArray

    external fun nv21ToNv12(nv21ByteArray: ByteArray, width: Int, height: Int): ByteArray

    /**
     * **NOT** work now.
     *
     * The `libyuv` doesn't include the jpeg library. So it doesn't work now.
     */
    external fun i420ToRgb24(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray
}
