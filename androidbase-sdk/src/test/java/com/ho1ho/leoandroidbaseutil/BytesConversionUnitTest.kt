package com.ho1ho.leoandroidbaseutil

import com.ho1ho.androidbase.exts.asByteAndForceToBytes
import com.ho1ho.androidbase.exts.toBytes
import com.ho1ho.androidbase.exts.toBytesLE
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BytesConversionUnitTest {
    @Test
    fun numberToBytes() {
        assertArrayEquals(byteArrayOf(0), 0.asByteAndForceToBytes())
        assertArrayEquals(byteArrayOf(1), 1.asByteAndForceToBytes())
        assertArrayEquals(byteArrayOf(15), 15.asByteAndForceToBytes())
        assertArrayEquals(byteArrayOf(16), 16.asByteAndForceToBytes())
        assertArrayEquals(byteArrayOf(127), 127.asByteAndForceToBytes())
        assertArrayEquals(byteArrayOf(0x80.toByte()), 128.asByteAndForceToBytes())
        assertArrayEquals(byteArrayOf(0xFE.toByte()), 254.asByteAndForceToBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte()), 255.asByteAndForceToBytes())
        // If value is greater than 255, [asByteAndForceToBytes()] will only return the least significant 8 bits
        assertArrayEquals(byteArrayOf(0x00), 256.asByteAndForceToBytes())
        assertArrayEquals(byteArrayOf(0x01), 257.asByteAndForceToBytes())

        // ==================================================

        assertArrayEquals(byteArrayOf(0x00, 0x00), (0.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x00), (0.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), 0.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), 0.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 0L.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 0L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x01), (1.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x01, 0x00), (1.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x01), 1.toBytes())
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00, 0x00), 1.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01), 1L.toBytes())
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 1L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x0F), (15.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x0F, 0x00), (15.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x0F), 15.toBytes())
        assertArrayEquals(byteArrayOf(0x0F, 0x00, 0x00, 0x00), 15.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0F), 15L.toBytes())
        assertArrayEquals(byteArrayOf(0x0F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 15L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x10), (16.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x10, 0x00), (16.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x10), 16.toBytes())
        assertArrayEquals(byteArrayOf(0x10, 0x00, 0x00, 0x00), 16.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10), 16L.toBytes())
        assertArrayEquals(byteArrayOf(0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 16L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x7F), (127.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x7F, 0x00), (127.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x7F), 127.toBytes())
        assertArrayEquals(byteArrayOf(0x7F, 0x00, 0x00, 0x00), 127.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F), 127L.toBytes())
        assertArrayEquals(byteArrayOf(0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 127L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x80.toByte()), (128.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x00), (128.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x80.toByte()), 128.toBytes())
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00), 128.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80.toByte()), 128L.toBytes())
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 128L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0xFE.toByte()), (254.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0x00), (254.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0xFE.toByte()), 254.toBytes())
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0x00, 0x00, 0x00), 254.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFE.toByte()), 254L.toBytes())
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 254L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0xFF.toByte()), (255.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00), (255.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0xFF.toByte()), 255.toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00), 255.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte()), 255L.toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 255L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x01, 0x00.toByte()), (256.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x01), (256.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x01, 0x00), 256.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00), 256.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00), 256L.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 256L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x01, 0xFF.toByte()), (511.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x01), (511.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x01, 0xFF.toByte()), 511.toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x01, 0x00, 0x00), 511.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0xFF.toByte()), 511L.toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 511L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x02, 0x00), (512.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x02), (512.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x02, 0x00), 512.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x02, 0x00, 0x00), 512.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00), 512L.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 512L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x02, 0x4A), (586.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x4A, 0x02), (586.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x02, 0x4A), 586.toBytes())
        assertArrayEquals(byteArrayOf(0x4A, 0x02, 0x00, 0x00), 586.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x4A), 586L.toBytes())
        assertArrayEquals(byteArrayOf(0x4A, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 586L.toBytesLE())

        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), (65535.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), (65535.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xFF.toByte()), 65535.toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00), 65535.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte()), 65535L.toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 65535L.toBytesLE())

        // If value is greater than 65535, [toShort()] will only return the least significant 16 bits
        assertArrayEquals(byteArrayOf(0x00, 0x00), (65536.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x00), (65536.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00), 65536.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x01, 0x00), 65536.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00), 65536L.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00), 65536L.toBytesLE())

        // If value is greater than 65535, [toShort()] will only return the least significant 16 bits
        assertArrayEquals(byteArrayOf(0x09, 0x32), (67890.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x32, 0x09), (67890.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x09, 0x32), 67890.toBytes())
        assertArrayEquals(byteArrayOf(0x32, 0x09, 0x01, 0x00), 67890.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x09, 0x32), 67890L.toBytes())
        assertArrayEquals(byteArrayOf(0x32, 0x09, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00), 67890L.toBytesLE())

        // If value is greater than 65535, [toShort()] will only return the least significant 16 bits
        assertArrayEquals(byteArrayOf(0xE2.toByte(), 0x40), (123456.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x40, 0xE2.toByte()), (123456.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0xE2.toByte(), 0x40), 123456.toBytes())
        assertArrayEquals(byteArrayOf(0x40, 0xE2.toByte(), 0x01, 0x00), 123456.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0xE2.toByte(), 0x40), 123456L.toBytes())
        assertArrayEquals(byteArrayOf(0x40, 0xE2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00, 0x00), 123456L.toBytesLE())

        // If value is greater than 65535, [toShort()] will only return the least significant 16 bits
        assertArrayEquals(byteArrayOf(0x02, 0xD2.toByte()), (1234567890.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xD2.toByte(), 0x02), (1234567890.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x49, 0x96.toByte(), 0x02, 0xD2.toByte()), 1234567890.toBytes())
        assertArrayEquals(byteArrayOf(0xD2.toByte(), 0x02, 0x96.toByte(), 0x49), 1234567890.toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x49, 0x96.toByte(), 0x02, 0xD2.toByte()), 1234567890L.toBytes())
        assertArrayEquals(byteArrayOf(0xD2.toByte(), 0x02, 0x96.toByte(), 0x49, 0x00, 0x00, 0x00, 0x00), 1234567890L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x70, 0x48, 0x86.toByte(), 0x0D, 0xDF.toByte(), 0x79.toByte()), 123456789012345L.toBytes())
        assertArrayEquals(byteArrayOf(0x79, 0xDF.toByte(), 0x0D, 0x86.toByte(), 0x48, 0x70, 0x00, 0x00), 123456789012345L.toBytesLE())
        assertArrayEquals(byteArrayOf(0x12, 0xD8.toByte(), 0x51, 0x4D, 0x4F, 0x3A, 0xD7.toByte(), 0x54), 1357924680135792468L.toBytes())
        assertArrayEquals(byteArrayOf(0x54, 0xD7.toByte(), 0x3A, 0x4F, 0X4D, 0x51, 0xD8.toByte(), 0x12), 1357924680135792468L.toBytesLE())
    }

    @Test
    fun bytesToNumber() {
//        println(JsonUtil.toHexadecimalString((1.toLong()).toBytesLE()))

//        assertTrue(0.toShort() == byteArrayOf(0x00).toShort())
//        assertTrue(0.toShort() == byteArrayOf(0x00).toShortLE())
//        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), 0.toBytes())
//        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), 0.toBytesLE())
//        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 0L.toBytes())
//        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 0L.toBytesLE())
    }
}
