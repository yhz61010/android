package com.leovp.camerax_sdk.bean

import android.net.Uri
import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/6/2 14:24
 */
@Keep
sealed class CaptureImage(open val mirror: Boolean, open val rotationDegrees: Int) {

    /**
     * The image saved in [fileUri] has already set Exif Orientation in order to
     * display it in correct orientation. Please note that **the original bitmap
     * data are not rotated.**
     *
     * Due to the original bitmap data are not rotated, if you load image into bitmap,
     * these two parameters [mirror] and [rotationDegrees] tell you how to rotate
     * your image correctly.
     * Flip horizontally firstly and rotate clockwise.
     *
     * @param mirror Whether the image should be mirrored.
     * @param rotationDegrees Indicates the rotation that the image should be rotated.
     */
    @Keep
    data class ImageUri(val fileUri: Uri, override val mirror: Boolean, override val rotationDegrees: Int) :
        CaptureImage(mirror, rotationDegrees)

    /**
     * @param mirror Whether the image should be mirrored.
     * @param rotationDegrees Indicates the rotation that the image should be rotated.
     */
    @Keep
    data class ImageBytes(val imgBytes: ByteArray,
        val width: Int,
        val height: Int,
        override val mirror: Boolean,
        override val rotationDegrees: Int) : CaptureImage(mirror, rotationDegrees) {

        override fun toString(): String {
            return "CaptureImageBytes(size=${imgBytes.size}, ${width}x$height, rotation=$rotationDegrees, mirror=$mirror)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ImageBytes

            if (!imgBytes.contentEquals(other.imgBytes)) return false
            if (width != other.width) return false
            if (height != other.height) return false
            if (rotationDegrees != other.rotationDegrees) return false
            if (mirror != other.mirror) return false

            return true
        }

        override fun hashCode(): Int {
            var result = imgBytes.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + rotationDegrees
            result = 31 * result + mirror.hashCode()
            return result
        }
    }
}