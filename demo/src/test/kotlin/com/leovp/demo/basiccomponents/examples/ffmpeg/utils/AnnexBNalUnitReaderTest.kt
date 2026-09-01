package com.leovp.demo.basiccomponents.examples.ffmpeg.utils

import java.io.ByteArrayInputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AnnexBNalUnitReaderTest {
    @Test
    fun readsMixedStartCodesAndFinalNalUnit() {
        val input =
            byteArrayOf(
                0,
                0,
                0,
                1,
                0x67,
                0x11,
                0,
                0,
                1,
                0x68,
                0x22,
                0x00
            )

        AnnexBNalUnitReader(ByteArrayInputStream(input)).use { reader ->
            assertArrayEquals(
                byteArrayOf(0, 0, 0, 1, 0x67, 0x11),
                reader.nextNalUnit()
            )
            assertArrayEquals(
                byteArrayOf(0, 0, 1, 0x68, 0x22, 0x00),
                reader.nextNalUnit()
            )
            assertNull(reader.nextNalUnit())
        }
    }

    @Test
    fun ignoresLeadingBytesAndKeepsOnlyTheAnnexBStream() {
        val input = byteArrayOf(0x55, 0, 0, 0, 0, 1, 0x65, 0x33)

        AnnexBNalUnitReader(ByteArrayInputStream(input)).use { reader ->
            assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x65, 0x33), reader.nextNalUnit())
            assertNull(reader.nextNalUnit())
        }
    }

    @Test
    fun resolvesH264AndH265NalUnitTypesForBothStartCodes() {
        assertEquals(7, h264NalUnitType(byteArrayOf(0, 0, 0, 1, 0x67)))
        assertEquals(8, h264NalUnitType(byteArrayOf(0, 0, 1, 0x68.toByte())))
        assertEquals(32, h265NalUnitType(byteArrayOf(0, 0, 0, 1, 0x40, 0x01)))
        assertEquals(39, h265NalUnitType(byteArrayOf(0, 0, 1, 0x4E, 0x01)))
        assertEquals(-1, h264NalUnitType(byteArrayOf(0x67)))
    }

    @Test
    fun joinsRepeatedParameterSetsWithTheFollowingPictureNalUnit() {
        val input =
            byteArrayOf(
                0,
                0,
                0,
                1,
                0x67,
                0x11,
                0,
                0,
                0,
                1,
                0x68,
                0x22,
                0,
                0,
                1,
                0x65,
                0x33,
                0,
                0,
                0,
                1,
                0x41,
                0x44
            )

        AnnexBNalUnitReader(ByteArrayInputStream(input)).use { reader ->
            val keyFrameGroup =
                reader.nextNalUnitGroupEndingWith { h264NalUnitType(it) in 1..5 }
            assertArrayEquals(input.copyOfRange(0, 17), keyFrameGroup?.bytes)
            assertEquals(5, keyFrameGroup?.endingNalUnit?.let(::h264NalUnitType))

            val predictedFrameGroup =
                reader.nextNalUnitGroupEndingWith { h264NalUnitType(it) in 1..5 }
            assertArrayEquals(input.copyOfRange(17, input.size), predictedFrameGroup?.bytes)
            assertEquals(1, predictedFrameGroup?.endingNalUnit?.let(::h264NalUnitType))
            assertNull(reader.nextNalUnitGroupEndingWith { h264NalUnitType(it) in 1..5 })
        }
    }
}
