package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.androidbase.exts.kotlin.toAsciiByteArray
import com.leovp.androidbase.exts.kotlin.truncate
import com.leovp.kotlin.exts.autoFormatByte
import com.leovp.kotlin.exts.humanReadableByteCount
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Author: Michael Leo
 * Date: 2021/8/2 14:07
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class StringUnitTest {

    @Test
    fun testString() {
        val string = "abcABC012"
        assertArrayEquals(byteArrayOf(97, 98, 99, 65, 66, 67, 48, 49, 50), string.toAsciiByteArray())

        val hexString = "010203040506070809A0B1C2"
        assertArrayEquals(
            byteArrayOf(
                0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9,
                0xA0.toByte(), 0xB1.toByte(), 0xC2.toByte()
            ),
            hexString.hexToByteArray()
        )

        val longString = "I have a dream. A song to sing."
        assertEquals("I have", longString.truncate(6))
        assertEquals("have", longString.truncate(4, 2))
        assertEquals("ve a dream. A song to sing.", longString.truncate(90, 4))
        assertEquals(" have a dream. A song to sing.", longString.truncate(90, 1))
        assertEquals("I have a dream. A song to sing.", longString.truncate(31, 0))
        assertEquals("I have a dream. A song to sing.", longString.truncate(90))
        assertEquals("ream. A song to sing.", longString.truncate(90, 10))
        assertEquals("", longString.truncate(90, 50))
        assertEquals("", longString.truncate(2, 50))
        assertEquals("sing", longString.truncate(4, 26))
        assertEquals("sing.", longString.truncate(5, 26))
    }

    @Test
    fun formatTest() {
        assertEquals("NA", (-1L).autoFormatByte(false))
        assertEquals("NA", (-1234L).autoFormatByte(false))
        assertEquals("1B", 1L.autoFormatByte(false))
        assertEquals("0B", 0L.autoFormatByte(false))
        assertEquals("3B", 3L.autoFormatByte(false))
        assertEquals("1000B", 1000L.autoFormatByte(false))
        assertEquals("1.00KiB", 1024L.autoFormatByte(false))
        assertEquals("9.77KiB", 10000L.autoFormatByte(false))
        assertEquals("10.00KiB", 10240L.autoFormatByte(false))
        assertEquals("14.92MiB", 15645678L.autoFormatByte(false))
        assertEquals("630.39GiB", 676876567896L.autoFormatByte(false))

        assertEquals("NA", (-1L).autoFormatByte(true, 1))
        assertEquals("NA", (-1234L).autoFormatByte(true, 1))
        assertEquals("1B", 1L.autoFormatByte(true, 1))
        assertEquals("0B", 0L.autoFormatByte(true, 1))
        assertEquals("3B", 3L.autoFormatByte(true, 1))
        assertEquals("1.0kB", 1000L.autoFormatByte(true, 1))
        assertEquals("1.0kB", 1024L.autoFormatByte(true, 1))
        assertEquals("10.0kB", 10000L.autoFormatByte(true, 1))
        assertEquals("10.2kB", 10240L.autoFormatByte(true, 1))
        assertEquals("15.6MB", 15645678L.autoFormatByte(true, 1))
        assertEquals("676.9GB", 676876567896L.autoFormatByte(true, 1))

        assertEquals("NA", (-1L).humanReadableByteCount(false))
        assertEquals("NA", (-1234L).humanReadableByteCount(false))
        assertEquals("1B", 1L.humanReadableByteCount(false))
        assertEquals("0B", 0L.humanReadableByteCount(false))
        assertEquals("3B", 3L.humanReadableByteCount(false))
        assertEquals("1000B", 1000L.humanReadableByteCount(false))
        assertEquals("1.00KiB", 1024L.humanReadableByteCount(false))
        assertEquals("9.77KiB", 10000L.humanReadableByteCount(false))
        assertEquals("10.00KiB", 10240L.humanReadableByteCount(false))
        assertEquals("14.92MiB", 15645678L.humanReadableByteCount(false))
        assertEquals("630.39GiB", 676876567896L.humanReadableByteCount(false))
        assertEquals("61.56TiB", 67687656789659L.humanReadableByteCount(false))
        assertEquals("1.75PiB", 1967687656789659L.humanReadableByteCount(false))
        assertEquals("2.23EiB", 2567967687656789659L.humanReadableByteCount(false))

        assertEquals("NA", (-1L).humanReadableByteCount(true, 1))
        assertEquals("NA", (-1234L).humanReadableByteCount(true, 1))
        assertEquals("1B", 1L.humanReadableByteCount(true, 1))
        assertEquals("0B", 0L.humanReadableByteCount(true, 1))
        assertEquals("3B", 3L.humanReadableByteCount(true, 1))
        assertEquals("1.0kB", 1000L.humanReadableByteCount(true, 1))
        assertEquals("1.0kB", 1024L.humanReadableByteCount(true, 1))
        assertEquals("10.0kB", 10000L.humanReadableByteCount(true, 1))
        assertEquals("10.2kB", 10240L.humanReadableByteCount(true, 1))
        assertEquals("15.6MB", 15645678L.humanReadableByteCount(true, 1))
        assertEquals("676.9GB", 676876567896L.humanReadableByteCount(true, 1))
        assertEquals("67.7TB", 67687656789659L.humanReadableByteCount(true, 1))
        assertEquals("2.0PB", 1967687656789659L.humanReadableByteCount(true, 1))
        assertEquals("2.6EB", 2567967687656789659L.humanReadableByteCount(true, 1))
    }
}
