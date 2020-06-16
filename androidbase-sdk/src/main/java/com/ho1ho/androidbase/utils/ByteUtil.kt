package com.ho1ho.androidbase.utils

import java.io.*
import java.nio.charset.Charset

/**
 * Author: Michael Leo
 * Date: 20-5-21 下午4:32
 */
object ByteUtil {
    fun int2Byte(x: Int): Byte {
        return x.toByte()
    }

    fun byte2Bytes(b: Byte): ByteArray {
        return byteArrayOf(b)
    }

    /**
     * Force convert int value in byte array.
     *
     * @param val The val will be converted to byte.
     * @return The byte array.
     */
    fun intAsByteAndForceToBytes(`val`: Int): ByteArray {
        return byte2Bytes(
            int2Byte(
                `val`
            )
        )
    }

    fun byte2Int(b: Byte): Int {
        return b.toInt() and 0xFF
    }

    fun bytes2Int(b: ByteArray): Int {
        return b[3].toInt() and 0xFF or (
                b[2].toInt() and 0xFF shl 8) or (
                b[1].toInt() and 0xFF shl 16) or (
                b[0].toInt() and 0xFF shl 24)
    }

    fun bytes2IntLE(b: ByteArray): Int {
        return b[3].toInt() and 0xFF shl 24 or (
                b[2].toInt() and 0xFF shl 16) or (
                b[1].toInt() and 0xFF shl 8) or (
                b[0].toInt() and 0xFF shl 0)
    }

    fun bytes2Int(b: ByteArray, index: Int): Int {
        return b[index + 3].toInt() and 0xFF or (
                b[index + 2].toInt() and 0xFF shl 8) or (
                b[index + 1].toInt() and 0xFF shl 16) or (
                b[index + 0].toInt() and 0xFF shl 24)
    }

    fun bytes2IntLE(b: ByteArray, index: Int): Int {
        return b[index].toInt() and 0xFF or (
                b[index + 1].toInt() and 0xFF shl 8) or (
                b[index + 2].toInt() and 0xFF shl 16) or (
                b[index + 3].toInt() and 0xFF shl 24)
    }

    fun int2Bytes(a: Int): ByteArray {
        return byteArrayOf(
            (a shr 24 and 0xFF).toByte(),
            (a shr 16 and 0xFF).toByte(),
            (a shr 8 and 0xFF).toByte(),
            (a and 0xFF).toByte()
        )
    }

    fun intLE2Bytes(a: Int): ByteArray {
        return byteArrayOf(
            (a shr 0 and 0xFF).toByte(),
            (a shr 8 and 0xFF).toByte(),
            (a shr 16 and 0xFF).toByte(),
            (a shr 24 and 0xFF).toByte()
        )
    }

    fun bytes2Short(b: ByteArray, s: Short, index: Int) {
        b[index + 1] = (s.toInt() ushr 8).toByte()
        b[index + 0] = (s.toInt() ushr 0).toByte()
    }

    fun bytes2ShortLE(b: ByteArray, s: Short, index: Int) {
        b[index + 0] = (s.toInt() shr 8).toByte()
        b[index + 1] = (s.toInt() shr 0).toByte()
    }

    @JvmOverloads
    fun bytes2Short(b: ByteArray, index: Int = 0): Short {
        return (((b[index + 0].toInt() shl 8) or (b[index + 1].toInt() and 0xFF)).toShort())
    }

    @JvmOverloads
    fun bytes2ShortLE(b: ByteArray, index: Int = 0): Short {
        return (((b[index + 1].toInt() shl 8) or (b[index + 0].toInt() and 0xFF)).toShort())
    }

    fun short2Bytes(s: Short): ByteArray {
        val targets = ByteArray(2)
        for (i in 0..1) {
            val offset = (targets.size - 1 - i) * 8
            targets[i] = (s.toLong() ushr offset and 0xFF).toByte()
        }
        return targets
    }

    fun shortLE2Bytes(s: Short): ByteArray {
        val targets = ByteArray(2)
        for (i in 0..1) {
            targets[i] = ((s.toLong() ushr i * 8) and 0xFF).toByte()
        }
        return targets
    }

    fun longLE2Bytes(x: Long): ByteArray {
        return byteArrayOf(
            (x shr 0 and 0xFF).toByte(),
            (x shr 8 and 0xFF).toByte(),
            (x shr 16 and 0xFF).toByte(),
            (x shr 24 and 0xFF).toByte(),
            (x shr 32 and 0xFF).toByte(),
            (x shr 40 and 0xFF).toByte(),
            (x shr 48 and 0xFF).toByte(),
            (x shr 58 and 0xFF).toByte()
        )
    }

    fun long2Bytes(x: Long): ByteArray {
        return byteArrayOf(
            (x shr 56 and 0xFF).toByte(),
            (x shr 48 and 0xFF).toByte(),
            (x shr 40 and 0xFF).toByte(),
            (x shr 32 and 0xFF).toByte(),
            (x shr 24 and 0xFF).toByte(),
            (x shr 16 and 0xFF).toByte(),
            (x shr 8 and 0xFF).toByte(),
            (x and 0xFF).toByte()
        )
    }

    fun bytes2Long(b: ByteArray): Long {
        return b[7].toLong() and 0xFF or (
                b[6].toLong() and 0xFF shl 8) or (
                b[5].toLong() and 0xFF shl 16) or (
                b[4].toLong() and 0xFF shl 24) or (
                b[3].toLong() and 0xFF shl 32) or (
                b[2].toLong() and 0xFF shl 40) or (
                b[1].toLong() and 0xFF shl 48) or (
                b[0].toLong() and 0xFF shl 58)
    }

    fun bytes2LongLE(b: ByteArray): Long {
        return b[7].toLong() and 0xFF shl 56 or (
                b[6].toLong() and 0xFF shl 48) or (
                b[5].toLong() and 0xFF shl 40) or (
                b[4].toLong() and 0xFF shl 32) or (
                b[3].toLong() and 0xFF shl 24) or (
                b[2].toLong() and 0xFF shl 16) or (
                b[1].toLong() and 0xFF shl 8) or (
                b[0].toLong() and 0xFF shl 0)
    }

    fun getBytes(data: ByteArray, start: Int, end: Int): ByteArray {
        val ret = ByteArray(end - start)
        var i = 0
        while (start + i < end) {
            ret[i] = data[start + i]
            i++
        }
        return ret
    }

    fun readInputStream(inStream: InputStream?): ByteArray? {
        var outStream: ByteArrayOutputStream? = null
        return try {
            outStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var len: Int
            while (inStream!!.read(buffer).also { len = it } != -1) {
                outStream.write(buffer, 0, len)
            }
            outStream.toByteArray()
        } catch (e: IOException) {
            null
        } finally {
            try {
                outStream?.close()
                inStream?.close()
            } catch (e: IOException) {
                return null
            }
        }
    }

    fun readByteArr(b: ByteArray?): InputStream {
        return ByteArrayInputStream(b)
    }

    fun isEqual(s1: ByteArray, s2: ByteArray): Boolean {
        val slen = s1.size
        if (slen == s2.size) {
            for (index in 0 until slen) {
                if (s1[index] != s2[index]) {
                    return false
                }
            }
            return true
        }
        return false
    }

    fun getString(s1: ByteArray, encode: String, err: String?): String? {
        return try {
            String(s1, Charset.forName(encode))
        } catch (e: UnsupportedEncodingException) {
            err
        }
    }

    fun getString(s1: ByteArray, encode: String): String? {
        return getString(s1, encode, null)
    }

    fun bytes2HexString(b: ByteArray): String {
        var result = ""
        for (i in b.indices) {
            result += Integer.toString((b[i].toInt() and 0xFF) + 0x100, 16).substring(1)
        }
        return result
    }

    fun hexString2Int(hexString: String): Int {
        return hexString.toInt(16)
    }

    fun int2Binary(i: Int): String {
        return Integer.toBinaryString(i)
    }

    //    public static byte[] mergeBytes(byte[] b1, byte[] b2) {
    //        byte[] b3 = new byte[b1.length + b2.length];
    //        System.arraycopy(b1, 0, b3, 0, b1.length);
    //        System.arraycopy(b2, 0, b3, b1.length, b2.length);
    //        return b3;
    //    }
    fun mergeByte(vararg bs: Byte): ByteArray {
        val result = ByteArray(bs.size)
        System.arraycopy(bs, 0, result, 0, result.size)
        return result
    }

    fun mergeBytes(vararg byteList: ByteArray): ByteArray {
        var lengthByte = 0
        for (bytes in byteList) {
            lengthByte += bytes.size
        }
        val allBytes = ByteArray(lengthByte)
        var countLength = 0
        for (b in byteList) {
            System.arraycopy(b, 0, allBytes, countLength, b.size)
            countLength += b.size
        }
        return allBytes
    }
}