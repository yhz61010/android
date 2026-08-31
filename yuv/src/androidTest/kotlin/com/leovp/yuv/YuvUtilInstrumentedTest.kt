package com.leovp.yuv

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YuvUtilInstrumentedTest {
    @Test
    fun planeBasedInputSupportsPixelStrideAndNonZeroPosition() {
        val yBuffer = ByteBuffer.wrap(byteArrayOf(99, 10, 11, 12, 13, 0, 0, 20, 21, 22, 23))
        val uBuffer = ByteBuffer.wrap(byteArrayOf(99, 30, 0, 31))
        val vBuffer = ByteBuffer.wrap(byteArrayOf(99, 40, 0, 41))
        yBuffer.position(1)
        uBuffer.position(1)
        vBuffer.position(1)

        val result = YuvUtil.android420ToI420(
            yBuffer = yBuffer,
            uBuffer = uBuffer.asReadOnlyBuffer(),
            vBuffer = vBuffer,
            yRowStride = 6,
            uRowStride = 4,
            vRowStride = 4,
            yPixelStride = 1,
            uPixelStride = 2,
            vPixelStride = 2,
            width = 4,
            height = 2,
            verticallyFlip = false
        )

        assertArrayEquals(
            byteArrayOf(10, 11, 12, 13, 20, 21, 22, 23, 30, 31, 40, 41),
            result
        )
        assertEquals(1, yBuffer.position())
        assertEquals(1, uBuffer.position())
        assertEquals(1, vBuffer.position())
    }

    @Test
    fun planeBasedInputUsesDirectBuffersWithoutChangingPosition() {
        val yBuffer = ByteBuffer.allocateDirect(11).put(
            byteArrayOf(99, 10, 11, 12, 13, 0, 0, 20, 21, 22, 23)
        )
        val uBuffer = ByteBuffer.allocateDirect(4).put(byteArrayOf(99, 30, 0, 31))
        val vBuffer = ByteBuffer.allocateDirect(4).put(byteArrayOf(99, 40, 0, 41))
        yBuffer.position(1)
        uBuffer.position(1)
        vBuffer.position(1)

        val result = YuvUtil.android420ToI420(
            yBuffer = yBuffer,
            uBuffer = uBuffer.asReadOnlyBuffer(),
            vBuffer = vBuffer,
            yRowStride = 6,
            uRowStride = 4,
            vRowStride = 4,
            yPixelStride = 1,
            uPixelStride = 2,
            vPixelStride = 2,
            width = 4,
            height = 2,
            verticallyFlip = false
        )

        assertArrayEquals(
            byteArrayOf(10, 11, 12, 13, 20, 21, 22, 23, 30, 31, 40, 41),
            result
        )
        assertEquals(1, yBuffer.position())
        assertEquals(1, uBuffer.position())
        assertEquals(1, vBuffer.position())
    }

    @Test
    fun planeBasedInputSupportsArrayOffsetsAndDifferentChromaPixelStrides() {
        val yBuffer = ByteBuffer.wrap(byteArrayOf(99, 98, 10, 11, 12, 13, 20, 21, 22, 23))
            .positionedSlice(2)
        val uBuffer = ByteBuffer.wrap(byteArrayOf(99, 30, 31)).positionedSlice(1)
        val vBuffer = ByteBuffer.wrap(byteArrayOf(99, 40, 0, 41)).positionedSlice(1)

        val result = YuvUtil.android420ToI420(
            yBuffer = yBuffer,
            uBuffer = uBuffer,
            vBuffer = vBuffer,
            yRowStride = 4,
            uRowStride = 2,
            vRowStride = 4,
            yPixelStride = 1,
            uPixelStride = 1,
            vPixelStride = 2,
            width = 4,
            height = 2,
            verticallyFlip = false
        )

        assertArrayEquals(
            byteArrayOf(10, 11, 12, 13, 20, 21, 22, 23, 30, 31, 40, 41),
            result
        )
        assertEquals(0, yBuffer.position())
        assertEquals(0, uBuffer.position())
        assertEquals(0, vBuffer.position())
    }

    @Test
    fun nativeMethodsRejectInvalidDimensionsAndBufferSizes() {
        assertThrows(IllegalArgumentException::class.java) {
            YuvUtil.rotateI420(ByteArray(6), 3, 2, YuvUtil.ROTATE_90)
        }
        assertThrows(IllegalArgumentException::class.java) {
            YuvUtil.rotateI420(ByteArray(5), 2, 2, YuvUtil.ROTATE_90)
        }
        assertThrows(IllegalArgumentException::class.java) {
            YuvUtil.transformI420(ByteArray(6), 2, 2, 45, false, YuvUtil.I420)
        }
    }

    @Test
    fun rgb24OutputUsesThreeBytesPerPixel() {
        val blackI420 = byteArrayOf(16, 16, 16, 16, 128.toByte(), 128.toByte())
        val rgb = YuvUtil.i420ToRgb24(blackI420, 2, 2)

        assertEquals(12, rgb.size)
        assertArrayEquals(ByteArray(12), rgb)
    }

    private fun ByteBuffer.positionedSlice(offset: Int): ByteBuffer {
        position(offset)
        return slice()
    }
}
