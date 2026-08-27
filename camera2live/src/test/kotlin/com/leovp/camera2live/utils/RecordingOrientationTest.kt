package com.leovp.camera2live.utils

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class RecordingOrientationTest {
    @Test
    fun `missing rotation preserves lens-specific portrait defaults`() {
        assertEquals(90, resolveRecordingRotation(null, frontFacing = false))
        assertEquals(270, resolveRecordingRotation(null, frontFacing = true))
    }

    @Test
    fun `explicit rotation overrides lens-specific default`() {
        assertEquals(0, resolveRecordingRotation(0, frontFacing = false))
        assertEquals(180, resolveRecordingRotation(180, frontFacing = true))
    }

    @Test
    fun `zero and 180 degree rotations preserve frame dimensions`() {
        assertEquals(FrameDimensions(1920, 1080), getRotatedFrameDimensions(1920, 1080, 0))
        assertEquals(FrameDimensions(1920, 1080), getRotatedFrameDimensions(1920, 1080, 180))
    }

    @Test
    fun `90 and 270 degree rotations swap frame dimensions`() {
        assertEquals(FrameDimensions(1080, 1920), getRotatedFrameDimensions(1920, 1080, 90))
        assertEquals(FrameDimensions(1080, 1920), getRotatedFrameDimensions(1920, 1080, 270))
    }

    @Test
    fun `unsupported rotation is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            getRotatedFrameDimensions(1920, 1080, 45)
        }
        assertFailsWith<IllegalArgumentException> {
            resolveRecordingRotation(45, frontFacing = true)
        }
    }

    @Test
    fun `non-positive frame dimensions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            getRotatedFrameDimensions(0, 1080, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            getRotatedFrameDimensions(1920, -1, 0)
        }
    }

    @Test
    fun `unsupported fused transform output format is rejected before JNI`() {
        assertFailsWith<IllegalArgumentException> {
            transformI420Frame(
                i420Data = ByteArray(6),
                width = 2,
                height = 2,
                rotationDegrees = 0,
                mirrorHorizontally = false,
                outputFormat = -1
            )
        }
    }
}
