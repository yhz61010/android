package com.leovp.bytes

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ByteBufferExtUnitTest {
    @Test
    fun toByteArrayReadsOnlyRemainingBytes() {
        val source = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).apply { position(1) }

        assertArrayEquals(byteArrayOf(2, 3, 4), source.toByteArray())
        assertEquals(source.limit(), source.position())
    }

    @Test
    fun copyPreservesWrittenContentStateAndByteOrder() {
        val source = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        source.putInt(0x01020304)
        val originalPosition = source.position()
        val originalLimit = source.limit()

        val copied = source.copy()

        assertEquals(originalPosition, source.position())
        assertEquals(originalLimit, source.limit())
        assertEquals(ByteOrder.LITTLE_ENDIAN, copied.order())
        assertEquals(0x01020304, copied.getInt(0))
        assertEquals(0, copied.position())
        assertEquals(Int.SIZE_BYTES, copied.limit())
    }

    @Test
    fun copyAllPreservesBytesThroughLimitAndSourcePosition() {
        val source = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5)).apply {
            position(2)
            limit(4)
        }

        val copied = source.copyAll()

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), copied.toByteArray())
        assertEquals(2, source.position())
        assertEquals(4, source.limit())
    }

    @Test
    fun emptyBuffersCanBeCopied() {
        val source = ByteBuffer.allocate(0)

        assertEquals(0, source.copy().remaining())
        assertEquals(0, source.copyAll().remaining())
        assertArrayEquals(byteArrayOf(), source.toByteArray())
    }
}
