package com.ho1ho.androidbase.utils

/**
 * Author: Michael Leo
 * Date: 20-5-21 下午4:32
 */
object ByteUtil {
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