package com.leovp.bytes

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BytesConversionUnitTest {

    @Test
    fun hexConverter() {
        assertEquals(
            "1,9,A,10,13,1A,36,78,D3,BA",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36, 0x78,
                0xD3.toByte(), 0xBA.toByte()
            ).toHexStringLE()
        )
        assertEquals(
            "10,90,A0,1,31,A1,63,87,3D,AB",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36, 0x78,
                0xD3.toByte(), 0xBA.toByte()
            ).toHexString()
        )

        assertEquals(
            "1,9,A,10,13,1A,36,78,D3,BA",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36, 0x78,
                0xD3.toByte(), 0xBA.toByte()
            ).toHexStringLE(false)
        )
        assertEquals(
            "10,90,A0,1,31,A1,63,87,3D,AB",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36, 0x78,
                0xD3.toByte(), 0xBA.toByte()
            ).toHexString(false)
        )

        assertEquals(
            "01,09,0A,10,13,1A,36,78,D3,BA",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36, 0x78,
                0xD3.toByte(), 0xBA.toByte()
            ).toHexStringLE(true)
        )
        assertEquals(
            "10,90,A0,01,31,A1,63,87,3D,AB",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36, 0x78,
                0xD3.toByte(), 0xBA.toByte()
            ).toHexString(true)
        )

        assertEquals(
            "01090A10131A3678D3BA",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36, 0x78,
                0xD3.toByte(), 0xBA.toByte()
            ).toHexStringLE(true, "")
        )
        assertEquals(
            "1090A00131A163873DAB",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36, 0x78,
                0xD3.toByte(), 0xBA.toByte()
            ).toHexString(true, "")
        )

        assertEquals(
            "1,9,A,10,13,1A,36,78,D3,BA",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36,
                0x78, 0xD3.toByte(), 0xBA.toByte()
            ).toHexStringLE(false, ",")
        )
        assertEquals(
            "10,90,A0,1,31,A1,63,87,3D,AB",
            byteArrayOf(
                0x01, 0x09, 0x0A, 0x10, 0x13, 0x1A, 0x36,
                0x78, 0xD3.toByte(), 0xBA.toByte()
            ).toHexString(false, ",")
        )
    }

    @Test
    fun shortArray2ByteArray() {
        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, 5), shortArrayOf(0x0100, 0x0302, 0x0504).toByteArrayLE())
        assertArrayEquals(byteArrayOf(0xA, 0xB, 0xC, 0xD, 0xE, 0xF), shortArrayOf(0x0B0A, 0x0D0C, 0x0F0E).toByteArrayLE())
        assertArrayEquals(
            byteArrayOf(0x30, 0x31, 0x42, 0x43, 0xA5.toByte(), 0xDE.toByte(), 0x5A, 0xBA.toByte()),
            shortArrayOf(0x3130, 0x4342, 0xDEA5.toShort(), 0xBA5A.toShort()).toByteArrayLE()
        )
        assertArrayEquals(
            byteArrayOf(0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            shortArrayOf(0xEEDD.toShort(), 0xEEFF.toShort(), 0xFFFF.toShort()).toByteArrayLE()
        )

        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, 5).toShortArray(), shortArrayOf(0x0001, 0x0203, 0x0405))
        assertArrayEquals(byteArrayOf(0xA, 0xB, 0xC, 0xD, 0xE, 0xF).toShortArray(), shortArrayOf(0x0A0B, 0x0C0D, 0x0E0F))
        assertArrayEquals(
            byteArrayOf(0x30, 0x31, 0x42, 0x43, 0xA5.toByte(), 0xDE.toByte(), 0x5A, 0xBA.toByte()).toShortArray(),
            shortArrayOf(0x3031, 0x4243, 0xA5DE.toShort(), 0x5ABA)
        )
        assertArrayEquals(
            byteArrayOf(0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0xFF.toByte()).toShortArray(),
            shortArrayOf(0xDDEE.toShort(), 0xFFEE.toShort(), 0xFFFF.toShort())
        )
    }

    @Test
    fun byteArray2ShortArray() {
        assertArrayEquals(shortArrayOf(0x0100, 0x0302, 0x0504), byteArrayOf(0, 1, 2, 3, 4, 5).toShortArrayLE())
        assertArrayEquals(shortArrayOf(0x0B0A, 0x0D0C, 0x0F0E), byteArrayOf(0xA, 0xB, 0xC, 0xD, 0xE, 0xF).toShortArrayLE())
        assertArrayEquals(
            shortArrayOf(0x3130, 0x4342, 0xDEA5.toShort(), 0xBA5A.toShort()),
            byteArrayOf(0x30, 0x31, 0x42, 0x43, 0xA5.toByte(), 0xDE.toByte(), 0x5A, 0xBA.toByte()).toShortArrayLE()
        )
        assertArrayEquals(
            shortArrayOf(0xEEDD.toShort(), 0xEEFF.toShort(), 0xFFFF.toShort()),
            byteArrayOf(0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0xFF.toByte()).toShortArrayLE()
        )

        assertArrayEquals(shortArrayOf(0x0001, 0x0203, 0x0405), byteArrayOf(0, 1, 2, 3, 4, 5).toShortArray())
        assertArrayEquals(shortArrayOf(0x0A0B, 0x0C0D, 0x0E0F), byteArrayOf(0xA, 0xB, 0xC, 0xD, 0xE, 0xF).toShortArray())
        assertArrayEquals(
            shortArrayOf(0x3031, 0x4243, 0xA5DE.toShort(), 0x5ABA),
            byteArrayOf(0x30, 0x31, 0x42, 0x43, 0xA5.toByte(), 0xDE.toByte(), 0x5A, 0xBA.toByte()).toShortArray()
        )
        assertArrayEquals(
            shortArrayOf(0xDDEE.toShort(), 0xFFEE.toShort(), 0xFFFF.toShort()),
            byteArrayOf(0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0xFF.toByte()).toShortArray()
        )
    }

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

        assertArrayEquals(byteArrayOf(0xDE.toByte(), 0xA5.toByte()), ((0xDEA5).toShort()).toBytes())
        assertArrayEquals(byteArrayOf(0xA5.toByte(), 0xDE.toByte()), (0xDEA5.toShort()).toBytesLE())
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

        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x70, 0x48, 0x86.toByte(), 0x0D, 0xDF.toByte(), 0x79.toByte()),
            123456789012345L.toBytes()
        )
        assertArrayEquals(byteArrayOf(0x79, 0xDF.toByte(), 0x0D, 0x86.toByte(), 0x48, 0x70, 0x00, 0x00), 123456789012345L.toBytesLE())
        assertArrayEquals(byteArrayOf(0x12, 0xD8.toByte(), 0x51, 0x4D, 0x4F, 0x3A, 0xD7.toByte(), 0x54), 1357924680135792468L.toBytes())
        assertArrayEquals(byteArrayOf(0x54, 0xD7.toByte(), 0x3A, 0x4F, 0X4D, 0x51, 0xD8.toByte(), 0x12), 1357924680135792468L.toBytesLE())
    }

    @Test
    fun bytesToNumber() {
        assertEquals(0.toByte(), byteArrayOf(0x00).readByte())
        assertEquals(0.toShort(), byteArrayOf(0x00, 0x00).readShort())
        assertEquals(0.toShort(), byteArrayOf(0x00, 0x00).readShortLE())
        assertEquals(0, byteArrayOf(0x00, 0x00, 0x00, 0x00).readInt())
        assertEquals(0, byteArrayOf(0x00, 0x00, 0x00, 0x00).readIntLE())
        assertEquals(0L, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLong())
        assertEquals(0L, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())

        assertEquals(1.toByte(), byteArrayOf(0x01).readByte())
        assertEquals(1.toShort(), byteArrayOf(0x00, 0x01).readShort())
        assertEquals(1.toShort(), byteArrayOf(0x01, 0x00).readShortLE())
        assertEquals(1, byteArrayOf(0x00, 0x00, 0x00, 0x01).readInt())
        assertEquals(1, byteArrayOf(0x01, 0x00, 0x00, 0x00).readIntLE())
        assertEquals(1L, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01).readLong())
        assertEquals(1L, byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())
        assertEquals(256L, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00).readLong(1))
        assertEquals(1L, byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE(1))

        assertEquals(127.toByte(), byteArrayOf(0x7F).readByte())
        assertEquals(127.toShort(), byteArrayOf(0x00, 0x7F).readShort())
        assertEquals(127.toShort(), byteArrayOf(0x7F, 0x00).readShortLE())
        assertEquals(127, byteArrayOf(0x00, 0x00, 0x00, 0x7F).readInt())
        assertEquals(127, byteArrayOf(0x7F, 0x00, 0x00, 0x00).readIntLE())
        assertEquals(127L, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F).readLong())
        assertEquals(127L, byteArrayOf(0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())
        assertEquals(8323330L, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F, 0x01, 0x02).readLong(2))
        assertEquals(
            144396663052566530L,
            byteArrayOf(0x7F, 0x01, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02).readLongLE(2)
        )

        assertEquals(128.toByte(), byteArrayOf(0x80.toByte()).readByte())
        assertEquals(128.toShort(), byteArrayOf(0x00, 0x80.toByte()).readShort())
        assertEquals(128.toShort(), byteArrayOf(0x80.toByte(), 0x00).readShortLE())
        assertEquals(128, byteArrayOf(0x00, 0x00, 0x00, 0x80.toByte()).readInt())
        assertEquals(128, byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00).readIntLE())
        assertEquals(128L, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80.toByte()).readLong())
        assertEquals(128L, byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())

        assertEquals(255.toByte(), byteArrayOf(0xFF.toByte()).readByte())
        assertEquals(255.toShort(), byteArrayOf(0x00, 0xFF.toByte()).readShort())
        assertEquals(255.toShort(), byteArrayOf(0xFF.toByte(), 0x00).readShortLE())
        assertEquals(255, byteArrayOf(0x00, 0x00, 0x00, 0xFF.toByte()).readInt())
        assertEquals(255, byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00).readIntLE())
        assertEquals(255L, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte()).readLong())
        assertEquals(255L, byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())

        assertEquals((-1).toByte(), byteArrayOf(0xFF.toByte()).readByte())
        assertEquals(61234.toShort(), byteArrayOf(0xEF.toByte(), 0x32).readShort())
        assertEquals(61234.toShort(), byteArrayOf(0x32, 0xEF.toByte()).readShortLE())
        assertEquals(255, byteArrayOf(0x00, 0x00, 0x00, 0xFF.toByte()).readInt())
        assertEquals(255, byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00).readIntLE())
        assertEquals(255, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte()).readLong())
        assertEquals(255, byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).readLongLE())
    }

    @Test
    fun otherTests() {
        assertArrayEquals(byteArrayOf(0x7F), byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(1))
        assertArrayEquals(byteArrayOf(0x7F, 0x01), byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(2))
        assertArrayEquals(byteArrayOf(0x7F, 0x01, 0x02), byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(3))
        assertArrayEquals(
            byteArrayOf(0x7F, 0x01, 0x02, 0x03),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(4)
        )
        assertArrayEquals(
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(5)
        )
        assertArrayEquals(
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(6)
        )
        assertArrayEquals(
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(7)
        )
        assertArrayEquals(
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(8)
        )

        assertArrayEquals(
            byteArrayOf(0x01),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(1, 1)
        )
        assertArrayEquals(
            byteArrayOf(0x02, 0x03, 0x04),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(3, 2)
        )
        assertArrayEquals(
            byteArrayOf(0x03, 0x04, 0x15),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(3, 3)
        )
        assertArrayEquals(
            byteArrayOf(0x02, 0x03, 0x04, 0x15, 0x16, 0x36),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(6, 2)
        )
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x15, 0x16),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(6, 1)
        )
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(7, 1)
        )
        assertArrayEquals(
            byteArrayOf(0x15, 0x16),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(2, 5)
        )
        assertArrayEquals(
            byteArrayOf(0x02, 0x03, 0x04, 0x15, 0x16, 0x36),
            byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x04, 0x15, 0x16, 0x36).readBytes(6, 2)
        )
    }
}
