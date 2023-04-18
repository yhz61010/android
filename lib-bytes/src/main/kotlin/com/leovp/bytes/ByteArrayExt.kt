@file:Suppress("unused")

package com.leovp.bytes

/**
 * Author: Michael Leo
 * Date: 20-3-18 下午2:17
 */
private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

fun Byte.toBytes(): ByteArray = byteArrayOf(this)

/**
 * Force convert int value to byte array.
 */
fun Int.asByteAndForceToBytes(): ByteArray = this.toByte().toBytes()

fun ByteArray.readByte(index: Int = 0): Byte = (this[index].toInt() and 0xFF).toByte()

fun ByteArray.readBytes(length: Int, srcPos: Int = 0): ByteArray {
    val bytes = ByteArray(length)
    System.arraycopy(this, srcPos, bytes, 0, length)
    return bytes
}

fun ByteArray.readShort(index: Int = 0): Short = (
    (this[index + 0].toInt() shl 8) or (this[index + 1].toInt() and 0xFF)
    ).toShort()

fun ByteArray.readShortLE(index: Int = 0): Short = (
    (this[index + 1].toInt() shl 8) or (this[index + 0].toInt() and 0xFF)
    ).toShort()

fun ByteArray.readInt(index: Int = 0): Int =
    this[3 + index].toInt() and 0xFF or
        (this[2 + index].toInt() and 0xFF shl 8) or
        (this[1 + index].toInt() and 0xFF shl 16) or
        (this[0 + index].toInt() and 0xFF shl 24)

fun ByteArray.readIntLE(index: Int = 0): Int =
    this[index].toInt() and 0xFF or
        (this[index + 1].toInt() and 0xFF shl 8) or
        (this[index + 2].toInt() and 0xFF shl 16) or
        (this[index + 3].toInt() and 0xFF shl 24)

fun ByteArray.readLong(index: Int = 0): Long {
    var result: Long = 0
    for (i in 0 until Long.SIZE_BYTES) {
        result = result or ((this[i + index].toLong() and 0xFF) shl (Long.SIZE_BYTES - 1 - i) * 8)
    }
    return result
}

fun ByteArray.readLongLE(index: Int = 0): Long {
    var result: Long = 0
    for (i in 0 until Long.SIZE_BYTES) {
        result = result or ((this[i + index].toLong() and 0xFF) shl (i * 8))
    }
    return result
}

// =============================================

fun Short.toBytes(): ByteArray =
    ByteArray(Short.SIZE_BYTES).also {
        for (i in it.indices) it[i] = (this.toInt() ushr ((Short.SIZE_BYTES - 1 - i) * 8) and 0xFF).toByte()
    }

fun Short.toBytesLE(): ByteArray =
    ByteArray(Short.SIZE_BYTES).also {
        for (i in it.indices) it[i] = ((this.toInt() ushr i * 8) and 0xFF).toByte()
    }

/**
 * A big-endian system stores the most significant byte of a word at the smallest memory address
 * and the least significant byte at the largest.
 *
 * Example:
 * 0x1A2B3C4D
 *
 * Big Endian in memory:
 *  a  a+1  a+2  a+3
 * 1A   2B   3C   4D
 */
fun Int.toBytes(): ByteArray = ByteArray(Int.SIZE_BYTES).also {
    for (i in it.indices) it[i] = (this ushr ((Int.SIZE_BYTES - 1 - i) * 8) and 0xFF).toByte()
}

/**
 * A little-endian system stores the least-significant byte at the smallest memory address.
 * and the most significant byte at the largest.
 * in contrast,
 *
 * Example:
 * 0x1A2B3C4D
 *
 * Little Endian in memory:
 *  a  a+1  a+2  a+3
 * 4D   3C   2B   1A
 */
fun Int.toBytesLE(): ByteArray = ByteArray(Int.SIZE_BYTES).also {
    for (i in it.indices) it[i] = ((this ushr (i * 8)) and 0xFF).toByte()
}

fun Long.toBytes(): ByteArray =
    ByteArray(Long.SIZE_BYTES).also {
        for (i in it.indices) it[i] = (this ushr ((Long.SIZE_BYTES - 1 - i) * 8) and 0xFF).toByte()
    }

fun Long.toBytesLE(): ByteArray = ByteArray(Long.SIZE_BYTES).also {
    for (i in it.indices) it[i] = ((this ushr (i * 8)) and 0xFF).toByte()
}

// ==============================================

fun Byte.toHexString(addPadding: Boolean = false) = let { if (addPadding) "%02X".format(it) else "%X".format(it) }
fun ByteArray.toAsciiString(delimiter: CharSequence = ",") = map { it.toInt().toChar() }.joinToString(delimiter)

/**
 * Attention.
 * This method is little bit slow so be aware that if you use it in loop.
 */
// fun ByteArray.toHexString(delimiter: CharSequence = " ") = joinToString(delimiter) { "%02X".format(it) }

fun ByteArray.toHexString(addPadding: Boolean = false, delimiter: CharSequence = ","): String {
    if (this.isEmpty()) return ""
    val result = StringBuilder()
    forEach {
        val octet = it.toInt()
        val highBit = octet and 0x0F
        val lowBit = (octet and 0xF0).ushr(4)
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

/**
 * The length of byte array must be an even number.
 */
fun ByteArray.toShortArray() = ShortArray(this.size ushr 1) {
    // You can replace `+` with 'or'
    ((this[it shl 1].toInt() shl 8) + (this[(it shl 1) + 1].toInt() and 0xFF)).toShort()
}

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

fun ShortArray.toByteArray() = ByteArray(this.size shl 1) {
    ((this[it / 2].toInt() ushr (if (it % 2 == 0) 8 else 0)) and 0xFF).toByte()
}
