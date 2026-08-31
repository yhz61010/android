package com.leovp.yuv

import androidx.annotation.Keep
import java.nio.ByteBuffer

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

    const val ROTATE_0 = 0 // No rotation.
    const val ROTATE_90 = 90 // Rotate 90 degrees clockwise.
    const val ROTATE_180 = 180 // Rotate 180 degrees.
    const val ROTATE_270 = 270 // Rotate 270 degrees clockwise.

    const val SCALE_FILTER_NONE = 0 // Point sample; Fastest.
    const val SCALE_FILTER_LINEAR = 1 // Filter horizontally only.
    const val SCALE_FILTER_BILINEAR = 2 // Faster than box, but lower quality scaling down.
    const val SCALE_FILTER_BOX = 3 // Highest quality.

    /** Converts tightly packed planar I420 data with optional vertical flip and rotation. */
    @Deprecated(
        message = "Use the plane-based overload for YUV_420_888 images.",
        replaceWith = ReplaceWith(
            "android420ToI420(yBuffer, uBuffer, vBuffer, yRowStride, uRowStride, " +
                "vRowStride, yPixelStride, uPixelStride, vPixelStride, width, height, " +
                "verticallyFlip, degree)"
        )
    )
    external fun android420ToI420(
        srcYuvByteArray: ByteArray,
        pixelStrideUV: Int,
        width: Int,
        height: Int,
        verticallyFlip: Boolean,
        degree: Int = ROTATE_0
    ): ByteArray

    /**
     * Converts three [android.media.ImageFormat.YUV_420_888] planes to tightly packed I420.
     *
     * Each plane is read from its current [ByteBuffer.position] without changing the caller's
     * position or limit. Row and pixel strides are measured in bytes. Both direct and heap
     * buffers, including read-only buffers, are supported.
     */
    @Suppress("LongParameterList")
    fun android420ToI420(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        yRowStride: Int,
        uRowStride: Int,
        vRowStride: Int,
        yPixelStride: Int,
        uPixelStride: Int,
        vPixelStride: Int,
        width: Int,
        height: Int,
        verticallyFlip: Boolean,
        degree: Int = ROTATE_0,
    ): ByteArray {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0) {
            "Width and height must be positive even values."
        }
        require(
            degree == ROTATE_0 ||
                degree == ROTATE_90 ||
                degree == ROTATE_180 ||
                degree == ROTATE_270
        ) {
            "Rotation must be 0, 90, 180, or 270 degrees."
        }

        val ySize = checkedPlaneSize(width, height)
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val chromaSize = checkedPlaneSize(chromaWidth, chromaHeight)
        val outputSize = ySize.toLong() + chromaSize.toLong() * 2L
        require(outputSize <= Int.MAX_VALUE) { "I420 output is too large." }

        if (
            yBuffer.isDirect &&
            uBuffer.isDirect &&
            vBuffer.isDirect &&
            yPixelStride == 1 &&
            uPixelStride == vPixelStride
        ) {
            return android420ToI420Direct(
                yBuffer.slice(),
                uBuffer.slice(),
                vBuffer.slice(),
                yRowStride,
                uRowStride,
                vRowStride,
                yPixelStride,
                uPixelStride,
                vPixelStride,
                width,
                height,
                verticallyFlip,
                degree
            )
        }

        val packedI420 = ByteArray(outputSize.toInt())
        copyPlane(yBuffer, yRowStride, yPixelStride, width, height, packedI420, 0)
        copyPlane(uBuffer, uRowStride, uPixelStride, chromaWidth, chromaHeight, packedI420, ySize)
        copyPlane(
            vBuffer,
            vRowStride,
            vPixelStride,
            chromaWidth,
            chromaHeight,
            packedI420,
            ySize + chromaSize
        )
        return convertToI420(packedI420, I420, width, height, verticallyFlip, degree)
    }

    @Suppress("LongParameterList")
    private external fun android420ToI420Direct(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        yRowStride: Int,
        uRowStride: Int,
        vRowStride: Int,
        yPixelStride: Int,
        uPixelStride: Int,
        vPixelStride: Int,
        width: Int,
        height: Int,
        verticallyFlip: Boolean,
        degree: Int
    ): ByteArray

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
        degree: Int = ROTATE_0
    ): ByteArray

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
    external fun rotateI420(
        i420ByteArray: ByteArray,
        width: Int,
        height: Int,
        degree: Int
    ): ByteArray

    /**
     * Rotates and optionally mirrors I420 in one JNI call, then returns I420 or NV12 data.
     *
     * [width] and [height] must be positive even values. [outputFormat] must be [I420] or [NV12].
     * Mirroring is applied horizontally to the rotated output.
     */
    external fun transformI420(
        i420ByteArray: ByteArray,
        width: Int,
        height: Int,
        degree: Int,
        mirrorHorizontally: Boolean,
        outputFormat: Int,
    ): ByteArray

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
    ): ByteArray

    external fun i420ToNv21(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray

    external fun i420ToNv12(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray

    external fun nv21ToI420(nv21ByteArray: ByteArray, width: Int, height: Int): ByteArray

    external fun nv12ToI420(
        nv21ByteArray: ByteArray,
        width: Int,
        height: Int,
        degree: Int = ROTATE_0
    ): ByteArray

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
     * Returns tightly packed RGB24 data with three bytes per pixel and [width] * 3 bytes per row.
     */
    external fun i420ToRgb24(i420ByteArray: ByteArray, width: Int, height: Int): ByteArray

    private fun checkedPlaneSize(width: Int, height: Int): Int {
        val size = width.toLong() * height.toLong()
        require(size in 1..Int.MAX_VALUE.toLong()) { "Plane size is invalid or too large." }
        return size.toInt()
    }

    @Suppress("LongParameterList")
    private fun copyPlane(
        source: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        destination: ByteArray,
        destinationOffset: Int,
    ) {
        require(rowStride > 0 && pixelStride > 0) { "Plane strides must be positive." }
        val minimumRowBytes = (width.toLong() - 1L) * pixelStride + 1L
        require(minimumRowBytes <= rowStride.toLong()) {
            "Row stride is smaller than the bytes required by the plane width."
        }
        val requiredBytes = (height.toLong() - 1L) * rowStride + minimumRowBytes
        require(requiredBytes <= source.remaining().toLong()) {
            "Plane buffer is smaller than required by its dimensions and strides."
        }

        val duplicate = source.duplicate()
        val sourceOffset = duplicate.position()
        var destinationIndex = destinationOffset
        repeat(height) { row ->
            val rowOffset = sourceOffset + row * rowStride
            repeat(width) { column ->
                destination[destinationIndex++] = duplicate.get(rowOffset + column * pixelStride)
            }
        }
    }
}
