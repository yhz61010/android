package com.leovp.camerax.analyzer

import kotlin.test.Test
import kotlin.test.assertEquals

class LuminosityAnalyzerTest {
    @Test
    fun `average luma treats bytes as unsigned without boxing`() {
        assertEquals(0.0, averageLuma(byteArrayOf()))
        assertEquals(127.5, averageLuma(byteArrayOf(0x00, 0xFF.toByte())))
        assertEquals(128.0, averageLuma(byteArrayOf(0x7F, 0x80.toByte(), 0x81.toByte())))
    }
}
