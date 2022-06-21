package com.leovp.camerax_sdk.bean

import android.net.Uri
import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/6/2 14:24
 */
@Keep
sealed class CaptureImage {

    /**
     * The image saved in [fileUri] has already set Exif Orientation in order to
     * display it in correct orientation. Please note that **the original bitmap
     * data are not rotated.**
     */
    @Keep
    data class ImageUri(val fileUri: Uri) : CaptureImage()

    @Keep
    data class ImageBytes(val imgBytes: ByteArray,
        val width: Int,
        val height: Int) : CaptureImage() {

        override fun toString(): String {
            return "CaptureImageBytes(size=${imgBytes.size}, ${width}x$height)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ImageBytes

            if (!imgBytes.contentEquals(other.imgBytes)) return false
            if (width != other.width) return false
            if (height != other.height) return false

            return true
        }

        override fun hashCode(): Int {
            var result = imgBytes.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }
}