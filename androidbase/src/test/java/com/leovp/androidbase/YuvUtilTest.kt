package com.leovp.androidbase

import com.leovp.androidbase.utils.media.YuvUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Author: Michael Leo
 *
 * Unit tests for the pure-Kotlin YUV420 frame transforms. These functions do not touch the
 * Android framework, so they run as plain JVM unit tests.
 */
class YuvUtilTest {
    private val width = 4
    private val height = 2

    // A full YUV420 frame is width * height * 3 / 2 bytes.
    private fun fullFrame(): ByteArray = ByteArray(width * height * 3 / 2) { it.toByte() }

    @Test fun `yuvRotate270 returns a full frame without going out of bounds`() {
        // Before the fix, the Y-plane loop read index width*height, throwing AIOOBE.
        val rotated = YuvUtil.yuvRotate270(fullFrame(), width, height)

        assertEquals(width * height * 3 / 2, rotated.size)
    }

    @Test fun `yuvRotate270 reads column zero of the source`() {
        // Column 0 of the source must appear in the output. The buggy `width downTo 1`
        // range skipped column 0 entirely.
        val src = fullFrame()
        val rotated = YuvUtil.yuvRotate270(src, width, height)

        // src[0] is the top-left Y sample; it must be present somewhere in the rotated Y plane.
        val yPlane = rotated.copyOfRange(0, width * height)
        assert(yPlane.contains(src[0])) { "Rotated frame lost column 0 of the source" }
    }

    @Test fun `yuvRotate90 returns a full frame`() {
        val rotated = YuvUtil.yuvRotate90(fullFrame(), width, height)

        assertEquals(width * height * 3 / 2, rotated.size)
    }

    @Test fun `truncated input is rejected with a clear message`() {
        val truncated = ByteArray(width * height) // missing the chroma half

        assertThrows(IllegalArgumentException::class.java) {
            YuvUtil.yuvRotate270(truncated, width, height)
        }
        assertThrows(IllegalArgumentException::class.java) {
            YuvUtil.yuvRotate90(truncated, width, height)
        }
        assertThrows(IllegalArgumentException::class.java) {
            YuvUtil.rotateYUV420Degree180(truncated, width, height)
        }
    }

    @Test fun `non positive dimensions are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            YuvUtil.yuvRotate270(fullFrame(), 0, height)
        }
    }
}
