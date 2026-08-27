package com.leovp.bytes

import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 2023/4/17 10:39
 */

fun ByteBuffer.toByteArray(): ByteArray {
    val data = ByteArray(remaining())
    get(data) // Copy the buffer into a byte array
    return data // Return the byte array
}

/**
 * Only the read bytes in source buffer will be copied. And the position of new buffer will be `0`.
 *
 * Attention: In this method, it has already called _flip()_.
 * So DO NOT call _flip()_ or _rewind()_ again before this method. Or else you'll get an empty
 * _ByteBuffer_.
 *
 * For example:
 * ```
 *     val oriBuf = ByteBuffer.allocate(20)
 *     // Note that the put() method will increase the position of buffer.
 *     oriBuf.put("Leo".toByteArray()) // oriBuf = Leo\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0
 *                                     // position = 3
 *                                     // limit = 20
 *                                     // remaining = 17
 *                                     // capacity = 20
 *     val newBuf = oriBuf.copy()      // newBuf = Leo
 *                                     // position = 0
 *                                     // limit = 3
 *                                     // remaining = 3
 *                                     // capacity = 3
 * ```
 *
 * If the mark is defined then it is discarded.
 */
fun ByteBuffer.copy(): ByteBuffer {
    val oriPos = position() // Save the position of original buffer.
    val oriLimit = limit() // Save the limit of original buffer.
    flip() // flip() will set limit to position.
    val len = remaining()
    val dst = ByteBuffer.allocate(len)
    dst.order(order()) // Preserve the source byte order (remediation LB-1).
    // Copy one by one is much faster than dst.put(this)
    for (i in 0 until len) dst.put(i, this.get(i))
    dst.limit(len)
    dst.position(0)
    position(oriPos) // Reset the position of original buffer.
    limit(oriLimit) // Reset the limit of original buffer.
    return dst
}

/**
 * All bytes in source buffer will be copied. And the position of new buffer will be `0`.
 * For example:
 * ```
 *     val oriBuf = ByteBuffer.allocate(20)
 *     // Note that the put() method will increase the position of buffer.
 *     oriBuf.put("Leo".toByteArray()) // oriBuf = Leo\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0
 *                                     // position = 3
 *                                     // limit = 20
 *                                     // remaining = 17
 *                                     // capacity = 20
 *     val newBuf = oriBuf.copyAll()   // newBuf = Leo\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0
 *                                     // position = 0
 *                                     // limit = 20
 *                                     // remaining = 20
 *                                     // capacity = 20
 * ```
 *
 * If the mark is defined then it is discarded.
 */
fun ByteBuffer.copyAll(): ByteBuffer {
    val oriPos = position()
    rewind() // rewind() will NOT change limit.
    val len = remaining()
    val dst = ByteBuffer.allocate(len)
    dst.order(order()) // Preserve the source byte order (remediation LB-1).
    // Copy one by one is much faster than dst.put(this)
    for (i in 0 until len) dst.put(i, this.get(i))
    dst.limit(len)
    dst.position(0)
    position(oriPos) // Reset the position of original buffer.
    return dst
}
