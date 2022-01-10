package com.leovp.lib_bytes

import android.util.Log
import com.leovp.lib_compress.compress
import com.leovp.lib_compress.decompress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner


/**
 * Author: Michael Leo
 * Date: 20-8-3 上午11:37
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class FlaterUnitTest {

    @Test
    fun testString() {
        val string = "Welcome to Leo's World"
        val stringArray: ByteArray = string.encodeToByteArray()
        val compressedData = stringArray.compress()

        val decompressedData = compressedData.decompress()
        assertEquals(string, decompressedData.decodeToString())
    }

    @Test
    fun testBinary() {
        val binaryArray = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
        )
        val compressedData = binaryArray.compress()
        val decompressed = compressedData.decompress()
        assertArrayEquals(binaryArray, decompressed)
    }
}