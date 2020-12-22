package com.leovp.androidbase.exts.kotlin

/**
 * Author: Michael Leo
 * Date: 20-3-13 下午4:44
 */

fun String.toAsciiByteArray() = this.toByteArray(Charsets.US_ASCII)

/**
 * Transform each 2 hex chars to one byte
 */
fun String.hexToByteArray(): ByteArray {
    val binary = ByteArray(this.length / 2)
    for (i in binary.indices) {
        binary[i] = this.substring(2 * i, 2 * i + 2).toInt(16).toByte()
    }
    return binary
}