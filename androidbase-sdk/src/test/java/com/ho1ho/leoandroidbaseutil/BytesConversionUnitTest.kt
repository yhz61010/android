package com.ho1ho.leoandroidbaseutil

import com.ho1ho.androidbase.exts.asByteAndForceToBytes
import com.ho1ho.androidbase.exts.toBytes
import com.ho1ho.androidbase.exts.toBytesLE
import com.ho1ho.androidbase.utils.JsonUtil
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
        println(JsonUtil.toHexadecimalString((1.toLong()).toBytesLE()))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01), 1L.toBytes())
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 1L.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x0F), (15.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x0F, 0x00), (15.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x0F), 15.toBytes())
        assertArrayEquals(byteArrayOf(0x0F, 0x00, 0x00, 0x00), 15.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x10), (16.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x10, 0x00), (16.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x10), 16.toBytes())
        assertArrayEquals(byteArrayOf(0x10, 0x00, 0x00, 0x00), 16.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x7F), (127.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x7F, 0x00), (127.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x7F), 127.toBytes())
        assertArrayEquals(byteArrayOf(0x7F, 0x00, 0x00, 0x00), 127.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0x80.toByte()), (128.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x00), (128.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x80.toByte()), 128.toBytes())
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00), 128.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0xFE.toByte()), (254.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0x00), (254.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0xFE.toByte()), 254.toBytes())
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0x00, 0x00, 0x00), 254.toBytesLE())

        assertArrayEquals(byteArrayOf(0x00, 0xFF.toByte()), (255.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00), (255.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0xFF.toByte()), 255.toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00), 255.toBytesLE())

        assertArrayEquals(byteArrayOf(0x01, 0x00.toByte()), (256.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x01), (256.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x01, 0x00), 256.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00), 256.toBytesLE())

        assertArrayEquals(byteArrayOf(0x01, 0xFF.toByte()), (511.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x01), (511.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x01, 0xFF.toByte()), 511.toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x01, 0x00, 0x00), 511.toBytesLE())

        assertArrayEquals(byteArrayOf(0x02, 0x00), (512.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x02), (512.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x02, 0x00), 512.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x02, 0x00, 0x00), 512.toBytesLE())

        assertArrayEquals(byteArrayOf(0x02, 0x4A), (586.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x4A, 0x02), (586.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x02, 0x4A), 586.toBytes())
        assertArrayEquals(byteArrayOf(0x4A, 0x02, 0x00, 0x00), 586.toBytesLE())

        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), (65535.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), (65535.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xFF.toByte()), 65535.toBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00), 65535.toBytesLE())

        // If value is greater than 65535, [toShort()] will only return the least significant 16 bits
        assertArrayEquals(byteArrayOf(0x00, 0x00), (65536.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x00), (65536.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00), 65536.toBytes())
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x01, 0x00), 65536.toBytesLE())

        // If value is greater than 65535, [toShort()] will only return the least significant 16 bits
        assertArrayEquals(byteArrayOf(0x09, 0x32), (67890.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x32, 0x09), (67890.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x09, 0x32), 67890.toBytes())
        assertArrayEquals(byteArrayOf(0x32, 0x09, 0x01, 0x00), 67890.toBytesLE())

        // If value is greater than 65535, [toShort()] will only return the least significant 16 bits
        assertArrayEquals(byteArrayOf(0xE2.toByte(), 0x40), (123456.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0x40, 0xE2.toByte()), (123456.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0xE2.toByte(), 0x40), 123456.toBytes())
        assertArrayEquals(byteArrayOf(0x40, 0xE2.toByte(), 0x01, 0x00), 123456.toBytesLE())

        // If value is greater than 65535, [toShort()] will only return the least significant 16 bits
        assertArrayEquals(byteArrayOf(0x02, 0xD2.toByte()), (1234567890.toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xD2.toByte(), 0x02), (1234567890.toShort()).toBytesLE())
        assertArrayEquals(byteArrayOf(0x49, 0x96.toByte(), 0x02, 0xD2.toByte()), 1234567890.toBytes())
        assertArrayEquals(byteArrayOf(0xD2.toByte(), 0x02, 0x96.toByte(), 0x49), 1234567890.toBytesLE())
    }

    @Test
    fun bytesToNumber() {
//        assertTrue(0.toShort() == byteArrayOf(0x00).toShort())
//        assertTrue(0.toShort() == byteArrayOf(0x00).toShortLE())
//        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), 0.toBytes())
//        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00), 0.toBytesLE())
//        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 0L.toBytes())
//        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), 0L.toBytesLE())
    }
}
