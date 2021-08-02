package com.leovp.leoandroidbaseutil

import android.util.Log
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.androidbase.exts.kotlin.toAsciiByteArray
import com.leovp.androidbase.exts.kotlin.truncate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
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
        assertArrayEquals(byteArrayOf(0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xA0.toByte(), 0xB1.toByte(), 0xC2.toByte()), hexString.hexToByteArray())

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
    }
}