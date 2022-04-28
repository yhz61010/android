package com.leovp.camerax_sdk.bean

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/4/25 14:33
 */
@Keep
data class CaptureImage(val imgBytes: ByteArray, val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CaptureImage

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
