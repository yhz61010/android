package com.leovp.demo.basiccomponents.examples.audio

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PcmFramePaddingTest {
    @Test
    fun returnsOriginalArrayWhenInputIsFrameAligned() {
        val input = byteArrayOf(1, 2, 3, 4)

        assertSame(input, input.padPcmWithSilence(frameBytes = 4))
    }

    @Test
    fun padsIncompleteFrameWithSilence() {
        val input = byteArrayOf(1, 2, 3, 4, 5)

        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 0, 0, 0),
            input.padPcmWithSilence(frameBytes = 4)
        )
    }

    @Test
    fun rejectsInvalidFrameSize() {
        assertThrows(IllegalArgumentException::class.java) {
            byteArrayOf(1).padPcmWithSilence(frameBytes = 0)
        }
    }
}
