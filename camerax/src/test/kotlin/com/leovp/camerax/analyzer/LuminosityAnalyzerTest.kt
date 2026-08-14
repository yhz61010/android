package com.leovp.camerax.analyzer

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class LuminosityAnalyzerTest {
    @Test
    fun `average luma treats bytes as unsigned without boxing`() {
        assertEquals(0.0, averageLuma(ByteBuffer.wrap(byteArrayOf())))
        assertEquals(127.5, averageLuma(ByteBuffer.wrap(byteArrayOf(0x00, 0xFF.toByte()))))
        assertEquals(
            128.0,
            averageLuma(ByteBuffer.wrap(byteArrayOf(0x7F, 0x80.toByte(), 0x81.toByte())))
        )
    }

    @Test
    fun `average luma samples every stride-th byte`() {
        // stride 2 over [10, 250, 20, 240] samples indices 0 and 2 -> (10 + 20) / 2
        assertEquals(
            15.0,
            averageLuma(ByteBuffer.wrap(byteArrayOf(10, 250.toByte(), 20, 240.toByte())), 2)
        )
    }
}
