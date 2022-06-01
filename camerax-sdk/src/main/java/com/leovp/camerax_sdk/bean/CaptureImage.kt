package com.leovp.camerax_sdk.bean

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/4/25 14:33
 *
 * @param rotationDegrees Indicates the rotation that the image should be rotated.
 * @param mirror Whether the image should be mirrored.
 */
@Keep
data class CaptureImage(val imgBytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val mirror: Boolean) {

    override fun toString(): String {
        return "{image size=${imgBytes.size} ${width}x$height rotation=$rotationDegrees mirror=$mirror}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CaptureImage

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
