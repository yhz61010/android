@file:Suppress("unused")

package com.leovp.camerax_sdk.utils

import android.util.Size
import com.leovp.lib_common_kotlin.exts.round
import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 2022/5/31 10:19
 */

/**
 * Helper extension function used to extract a byte array from an image plane buffer
 */
internal fun ByteBuffer.toByteArray(): ByteArray {
    rewind() // Rewind the buffer to zero
    val data = ByteArray(remaining())
    get(data) // Copy the buffer into a byte array
    return data // Return the byte array
}

internal fun getCameraSizeTotalPixels(size: Size): String {
    val total = size.width * size.height
    val calTotal = total * 1f / 1_000_000
    return if (calTotal > 10) {
        "${calTotal.toInt()}MP"
    } else {
        "${calTotal.round(1)}MP"
    }
}
