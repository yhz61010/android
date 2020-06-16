package com.ho1ho.androidbase.exts

import java.util.*
import kotlin.experimental.and

/**
 * Author: Michael Leo
 * Date: 20-3-18 下午2:17
 */
private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

/**
 * Attention.
 * This method is little bit slow if you want to use in it loop.
 */
//fun ByteArray.toHexString(delimiter: CharSequence = " ") = joinToString(delimiter) { "%02X".format(it) }

fun ByteArray.toHexStringLE(delimiter: CharSequence = " "): String {
    val result = StringBuilder()

    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHARS[firstIndex])
        result.append(HEX_CHARS[secondIndex])
        if (delimiter.isNotEmpty()) result.append(delimiter)
    }

    if (delimiter.isNotEmpty()) result.deleteCharAt(result.length - 1)

    return result.toString()
}

fun Byte.toHexString() = let { "%02X".format(it) }

fun ByteArray.toAsciiString(delimiter: CharSequence = "") = map { it.toChar() }.joinToString(delimiter)

fun ByteArray.readByte(offset: Int = 0): Byte {
    throwOffsetError(this, offset, 1)
    return (this[offset].toInt() and 0xFF).toByte()
}

fun ByteArray.readShort(offset: Int = 0): Short {
    throwOffsetError(this, offset, 2)
    return (((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)).toShort()
}

fun ByteArray.readShortLE(offset: Int = 0): Short {
    throwOffsetError(this, offset, 2)
    return (((this[offset + 1].toInt() and 0xFF) shl 8) or (this[offset].toInt() and 0xFF)).toShort()
}

fun ByteArray.readInt(offset: Int = 0): Int {
    throwOffsetError(this, offset, 4)
    return (this[offset].toInt() and 0xFF) shl 24 or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}

fun ByteArray.readIntLE(offset: Int = 0): Int {
    throwOffsetError(this, offset, 4)
    return (this[offset + 3].toInt() and 0xFF) shl 24 or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            (this[offset].toInt() and 0xFF)
}

fun ByteArray.readFloat(offset: Int = 0) = java.lang.Float.intBitsToFloat(readInt(offset))

fun ByteArray.readFloatLE(offset: Int = 0) = java.lang.Float.intBitsToFloat(readIntLE(offset))

/**
 * Return the byte array from specified start position and read specified length bytes.<br>
 * The result bytes are stored in big endian.
 * @param offset The start position
 * @param length The number of bytes to be read
 *
 * @return The new byte array.
 */
fun ByteArray.readByteArray(offset: Int, length: Int): ByteArray {
    throwOffsetError(this, offset)
    return this.copyOfRange(offset, if ((offset + length) > this.size) this.size else offset + length)
}

/**
 * Return the byte array from specified start position and read specified length bytes.<br>
 * The result bytes are stored in little endian.
 * @param offset The start position
 * @param length The number of bytes to be read
 *
 * @return The new byte array.
 */
fun ByteArray.readByteArrayLE(offset: Int, length: Int): ByteArray {
    throwOffsetError(this, offset)
    return this.readByteArray(offset, length).reversedArray()
}

/**
 * Return the byte array from specified start position and read specified length bytes then cast them to string.<br>
 * The result bytes are stored in little endian.
 * @param offset The start position
 * @param length The number of bytes to be read
 * @param encoding "hex" or "ascii"
 * @param delimiter The delimiter for each encoded string.
 *
 * @return The string from read bytes which stored in big endian.
 */
fun ByteArray.readString(
    offset: Int,
    length: Int,
    encoding: String = "hex",
    delimiter: String = ""
): String {
    return when (encoding.toLowerCase(Locale.getDefault())) {
        "hex" -> this.readByteArray(offset, length).toHexStringLE(delimiter)
        "ascii" -> this.readByteArray(offset, length).map { it.toChar() }.joinToString(delimiter)
        else -> ""
    }
}

/**
 * Return the byte array from specified start position and read specified length bytes then cast them to string.<br>
 * The result bytes are stored in little endian.
 * @param offset The start position
 * @param length The number of bytes to be read
 * @param encoding "hex" or "ascii"
 * @param delimiter The delimiter for each encoded string.
 *
 * @return The string from read bytes which stored in little endian.
 */
fun ByteArray.readStringLE(
    offset: Int,
    length: Int,
    encoding: String = "hex",
    delimiter: String = ""
): String {
    return when (encoding.toLowerCase(Locale.getDefault())) {
        "hex" -> this.readByteArrayLE(offset, length).toHexStringLE()
        "ascii" -> this.readByteArrayLE(offset, length).map { it.toChar() }.joinToString(delimiter)
        else -> ""
    }
}

// =======================================================================

/**
 * Write the value to the specified position.
 * @param value The value to be written
 * @param offset The start position
 *
 * @return The original byte array for chain
 */
fun ByteArray.writeByte(value: Byte, offset: Int = 0): ByteArray {
    throwOffsetError(this, offset)
    this[offset] = value
    return this
}

/**
 * Write the value to the specified position.
 * @param value The value to be written
 * @param offset The start position
 *
 * @return The original byte array for chain
 */
fun ByteArray.writeShort(value: Short, offset: Int = 0): ByteArray {
    throwOffsetError(this, offset, 2)
    this[offset] = ((value.toInt() and 0xFF00) ushr 8).toByte()
    this[offset + 1] = (value and 0xFF).toByte()
    return this
}

fun ByteArray.writeShortLE(value: Int, offset: Int = 0): ByteArray {
    throwOffsetError(this, offset, 2)
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = (value and 0xFF00 ushr 8).toByte()
    return this
}

fun ByteArray.writeInt(value: Long, offset: Int = 0): ByteArray {
    throwOffsetError(this, offset, 4)
    this[offset] = (value and 0xFF000000 ushr 24).toByte()
    this[offset + 1] = (value and 0xFF0000 ushr 16).toByte()
    this[offset + 2] = (value and 0xFF00 ushr 8).toByte()
    this[offset + 3] = (value and 0xFF).toByte()
    return this
}

fun ByteArray.writeIntLE(value: Long, offset: Int = 0): ByteArray {
    throwOffsetError(this, offset, 4)
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = (value and 0xFF00 ushr 8).toByte()
    this[offset + 2] = (value and 0xFF0000 ushr 16).toByte()
    this[offset + 3] = (value and 0xFF000000 ushr 24).toByte()
    return this
}

fun ByteArray.writeFloatBE(value: Float, offset: Int = 0): ByteArray {
    throwOffsetError(this, offset, 4)
    this.writeInt(java.lang.Float.floatToIntBits(value).toLong(), offset)
    return this
}

fun ByteArray.writeFloatLE(value: Float, offset: Int = 0): ByteArray {
    throwOffsetError(this, offset, 4)
    this.writeIntLE(java.lang.Float.floatToIntBits(value).toLong(), offset)
    return this
}

fun ByteArray.writeByteArray(byteArray: ByteArray, offset: Int = 0, length: Int = byteArray.size): ByteArray {
    this.writeString(byteArray.toHexStringLE(), offset, length)
    return this
}

fun ByteArray.writeByteArrayAsLE(byteArray: ByteArray, offset: Int = 0, length: Int = byteArray.size): ByteArray {
    this.writeStringAsLE(byteArray.toHexStringLE(), offset, length)
    return this
}

// insertArrayLength: the number of array elements to be copied.
fun ByteArray.insertByteArray(
    insertArray: ByteArray,
    originalIndex: Int = 0,
    insertArrayOffset: Int = 0,
    insertArrayLength: Int = insertArray.size - insertArrayOffset
): ByteArray {
    val byteArrayPre = this.copyOfRange(0, originalIndex)
    val byteArrayLast = this.copyOfRange(originalIndex, this.size)
    val insertFinalArray = insertArray.copyOfRange(insertArrayOffset, insertArrayOffset + insertArrayLength)
    return byteArrayPre.plus(insertFinalArray).plus(byteArrayLast)
}

fun ByteArray.insertByteArrayAsLE(
    insertArray: ByteArray,
    originalIndex: Int = 0,
    insertArrayOffset: Int = 0,
    insertArrayLength: Int = insertArray.size - insertArrayOffset
): ByteArray {
    insertArray.reverse()
    val byteArrayPre = this.copyOfRange(0, originalIndex)
    val byteArrayLast = this.copyOfRange(originalIndex, this.size)
    val insertFinalArray = insertArray.copyOfRange(insertArrayOffset, insertArrayOffset + insertArrayLength)
    return byteArrayPre.plus(insertFinalArray).plus(byteArrayLast)
}


/**
 * Return the byte array from specified start position and read specified length bytes then cast them to string.<br>
 * The result bytes are stored in little endian.
 * @param offset The start position.
 * @param encoding "hex" or "ascii".
 * @param delimiter The delimiter for each encoded string. Only works in "hex" encoding.
 *
 * @return The string from read bytes which stored in big endian.
 */
fun ByteArray.writeString(str: String, offset: Int = 0, encoding: String = "hex", delimiter: String = " "): ByteArray {
    throwOffsetError(this, offset)
    when (encoding.toLowerCase(Locale.getDefault())) {
        "hex" -> {
            val hex = str.replace(delimiter, "")
            throwHexError(hex)
            for (i in 0 until hex.length / 2) {
                if (i + offset < this.size) {
                    this[i + offset] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            }
        }
        "ascii" -> {
            val hex = str.toCharArray().map { it.toInt() }.joinToString("") { it.toString(16) }
            this.writeString(hex, offset, "hex")
        }
    }
    return this
}

fun ByteArray.writeStringAsLE(
    str: String,
    offset: Int = 0,
    encoding: String = "hex",
    delimiter: String = " "
): ByteArray {
    when (encoding.toLowerCase(Locale.getDefault())) {
        "hex" -> {
            val hex = str.reversalEvery2Charts()
            this.writeString(hex, offset, encoding, delimiter)
        }
        "ascii" -> {
            val hex = str.toCharArray().map { it.toInt() }.joinToString("") { it.toString(16) }
            this.writeStringAsLE(hex, offset, "hex")
        }
    }
    return this
}

// The delimiter for each encoded string. Only works in "hex" encoding.
fun ByteArray.writeString(
    str: String, offset: Int, length: Int, encoding: String = "hex",
    delimiter: String = " "
): ByteArray {
    throwOffsetError(this, offset, length)
    when (encoding.toLowerCase(Locale.getDefault())) {
        "hex" -> {
            val hex = str.replace(" ", "").padStart(length * 2, '0').substring(0, length * 2)
            throwHexError(hex)
            this.writeString(hex, offset, delimiter)
        }
        "ascii" -> {
            val hex = str.toCharArray().map { it.toInt() }.map { it.toString(16) }.joinToString("")
            this.writeString(hex, offset, length, "hex", delimiter)
        }
    }
    return this
}

// The delimiter for each encoded string. Only works in "hex" encoding.
fun ByteArray.writeStringAsLE(
    str: String, offset: Int, length: Int, encoding: String = "hex",
    delimiter: String = " "
): ByteArray {
    when (encoding.toLowerCase(Locale.getDefault())) {
        "hex" -> {
            val hex = str.reversalEvery2Charts().padEnd(length * 2, '0').substring(0, length * 2)
            this.writeString(hex, offset, length, encoding, delimiter)
        }
        "ascii" -> {
            val hex = str.toCharArray().map { it.toInt() }.map { it.toString(16) }.joinToString("")
            this.writeStringAsLE(hex, offset, length, "hex", delimiter)
        }
    }
    return this
}

// Examples:
// Read unsigned short
// return readUnsigned(this, 2, offset, false).toInt()
// Read unsigned intLE
// return readUnsigned(this, 4, offset, true)
fun readUnsigned(byteArray: ByteArray, len: Int, offset: Int, littleEndian: Boolean): Long {
    var value = 0L
    for (count in 0 until len) {
        val shift = (if (littleEndian) count else (len - 1 - count)) shl 3
        value = value or (0xff.toLong() shl shift and (byteArray[offset + count].toLong() shl shift))
    }
    return value
}

// =======================================================================

private fun throwLenError(byteArray: ByteArray, byteLength: Int) {
    if (byteLength <= 0 || byteLength > 4) throw IllegalArgumentException("The value of \"byteLength\" is out of range. It must be >= 1 and <= 4. Received $byteLength")
    if (byteLength > byteArray.size) throw IllegalArgumentException("Attempt to write outside ByteArray bounds.")
}

private fun throwHexError(hex: String) {
    if (hex.length % 2 != 0) throw IllegalArgumentException("The value of \"hex\".length is out of range. It must be an even number")
}

private fun throwOffsetError(byteArray: ByteArray, offset: Int, length: Int = 1, byteLength: Int = 0) {
    if (offset > byteArray.size - length - byteLength) throw IllegalArgumentException("The value of \"offset\" is out of range. It must be >= 0 and <= ${byteArray.size - length - byteLength}. Received $offset")
}