package com.leovp.camerax_sdk.bean

import android.net.Uri
import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/6/2 14:24
 */
@Keep
sealed class CaptureImage(open val rotationDegrees: Int, open val mirror: Boolean) {

    /**
     * The image saved in [fileUri] has already set Exif Orientation in order to
     * display it in correct orientation. Please note that **the original bitmap
     * data are not rotated.**
     *
     * Due to the original bitmap data are not rotated, if you load image into bitmap,
     * these two parameters [rotationDegrees] and [mirror] tell you how to rotate
     * your image correctly.
     *
     * @param rotationDegrees Indicates the rotation that the image should be rotated.
     * @param mirror Whether the image should be mirrored.
     */
    @Keep
    data class ImageUri(val fileUri: Uri, override val rotationDegrees: Int, override val mirror: Boolean) :
        CaptureImage(rotationDegrees, mirror)

    /**
     * @param rotationDegrees Indicates the rotation that the image should be rotated.
     * @param mirror Whether the image should be mirrored.
     */
    @Keep
    data class ImageBytes(val imgBytes: ByteArray,
        val width: Int,
        val height: Int,
        override val rotationDegrees: Int,
        override val mirror: Boolean) : CaptureImage(rotationDegrees, mirror) {

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