package com.ho1ho.leoandroidbaseutil

import com.ho1ho.androidbase.exts.*
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
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
        assertTrue(0.toByte() == byteArrayOf(0x00).readByte())
        assertTrue(0.toShort() == byteArrayOf(0x00, 0x00).readShort())
        assertTrue(0.toShort() == byteArrayOf(0x00, 0x00).readShortLE())
        assertTrue(0 == byteArrayOf(0x00, 0x00, 0x00, 0x00).readInt())
        assertTrue(0 == byteArrayOf(0x00, 0x00, 0x00, 0x00).readIntLE())
        assertTrue(0L == byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLong())
        assertTrue(0L == byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())

        assertTrue(1.toByte() == byteArrayOf(0x01).readByte())
        assertTrue(1.toShort() == byteArrayOf(0x00, 0x01).readShort())
        assertTrue(1.toShort() == byteArrayOf(0x01, 0x00).readShortLE())
        assertTrue(1 == byteArrayOf(0x00, 0x00, 0x00, 0x01).readInt())
        assertTrue(1 == byteArrayOf(0x01, 0x00, 0x00, 0x00).readIntLE())
        assertTrue(1L == byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01).readLong())
        assertTrue(1L == byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())

        assertTrue(127.toByte() == byteArrayOf(0x7F).readByte())
        assertTrue(127.toShort() == byteArrayOf(0x00, 0x7F).readShort())
        assertTrue(127.toShort() == byteArrayOf(0x7F, 0x00).readShortLE())
        assertTrue(127 == byteArrayOf(0x00, 0x00, 0x00, 0x7F).readInt())
        assertTrue(127 == byteArrayOf(0x7F, 0x00, 0x00, 0x00).readIntLE())
        assertTrue(127L == byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F).readLong())
        assertTrue(127L == byteArrayOf(0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())

        assertTrue(128.toByte() == byteArrayOf(0x80.toByte()).readByte())
        assertTrue(128.toShort() == byteArrayOf(0x00, 0x80.toByte()).readShort())
        assertTrue(128.toShort() == byteArrayOf(0x80.toByte(), 0x00).readShortLE())
        assertTrue(128 == byteArrayOf(0x00, 0x00, 0x00, 0x80.toByte()).readInt())
        assertTrue(128 == byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00).readIntLE())
        assertTrue(128L == byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80.toByte()).readLong())
        assertTrue(128L == byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())

        assertTrue(255.toByte() == byteArrayOf(0xFF.toByte()).readByte())
        assertTrue(255.toShort() == byteArrayOf(0x00, 0xFF.toByte()).readShort())
        assertTrue(255.toShort() == byteArrayOf(0xFF.toByte(), 0x00).readShortLE())
        assertTrue(255 == byteArrayOf(0x00, 0x00, 0x00, 0xFF.toByte()).readInt())
        assertTrue(255 == byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00).readIntLE())
        assertTrue(255L == byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte()).readLong())
        assertTrue(255L == byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())
    }
}
