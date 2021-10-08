package com.leovp.util

/**
 * Author: Michael Leo
 * Date: 2021/10/8 14:48
 */

private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

/**
 * The length of byte array must be an even number.
 */
fun ByteArray.toShortArrayLE() = ShortArray(this.size ushr 1) {
    // You can replace `+` with 'or'
    ((this[it shl 1].toInt() and 0xFF) + (this[(it shl 1) + 1].toInt() shl 8)).toShort()
}

fun ShortArray.toByteArrayLE() = ByteArray(this.size shl 1) {
    ((this[it / 2].toInt() ushr (if (it % 2 == 0) 0 else 8)) and 0xFF).toByte()
}

fun ByteArray.toHexStringLE(addPadding: Boolean = false, delimiter: CharSequence = ","): String {
    if (this.isEmpty()) return ""
    val result = StringBuilder()
    forEach {
        val octet = it.toInt()
        val highBit = (octet and 0xF0).ushr(4)
        val lowBit = octet and 0x0F
        if (highBit == 0) {
            if (addPadding) result.append(HEX_CHARS[highBit])
            result.append(HEX_CHARS[lowBit])
        } else {
            result.append(HEX_CHARS[highBit])
            result.append(HEX_CHARS[lowBit])
        }
        if (delimiter.isNotEmpty()) result.append(delimiter)
    }
    if (delimiter.isNotEmpty()) result.deleteCharAt(result.length - 1)
    return result.toString()
}