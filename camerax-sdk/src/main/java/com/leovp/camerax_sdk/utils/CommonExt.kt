package com.leovp.camerax_sdk.utils

import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 2022/5/31 10:19
 */

/**
 * Helper extension function used to extract a byte array from an image plane buffer
 */
fun ByteBuffer.toByteArray(): ByteArray {
    rewind()    // Rewind the buffer to zero
    val data = ByteArray(remaining())
    get(data)   // Copy the buffer into a byte array
    return data // Return the byte array
}