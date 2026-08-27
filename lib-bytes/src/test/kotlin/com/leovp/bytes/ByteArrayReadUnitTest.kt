package com.leovp.bytes

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteArrayReadUnitTest {
    @Test
    fun `readByte preserves signed byte values`() {
        assertEquals(0x00.toByte(), byteArrayOf(0x00).readByte())
        assertEquals(0x7F.toByte(), byteArrayOf(0x7F).readByte())
        assertEquals(0x80.toByte(), byteArrayOf(0x80.toByte()).readByte())
        assertEquals(0xFF.toByte(), byteArrayOf(0xFF.toByte()).readByte())
    }

    @Test
    fun `readInt handles sign bits and offsets in big endian order`() {
        val bytes = byteArrayOf(0x55, 0x80.toByte(), 0x00, 0x00, 0x01, 0x66)

        assertEquals(0x80000001.toInt(), bytes.readInt(index = 1))
        assertEquals(
            0xFFEEDDCC.toInt(),
            byteArrayOf(0xFF.toByte(), 0xEE.toByte(), 0xDD.toByte(), 0xCC.toByte()).readInt()
        )
    }

    @Test
    fun `readIntLE handles sign bits and offsets in little endian order`() {
        val bytes = byteArrayOf(0x55, 0x01, 0x00, 0x00, 0x80.toByte(), 0x66)

        assertEquals(0x80000001.toInt(), bytes.readIntLE(index = 1))
        assertEquals(
            0xFFEEDDCC.toInt(),
            byteArrayOf(0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()).readIntLE()
        )
    }
}
