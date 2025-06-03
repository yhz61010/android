package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.utils.media.H264Util
import com.leovp.json.toJsonString
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Author: Michael Leo
 * Date: 20-8-3 上午11:37
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class H264UtilTest {

    @Test
    fun h264Test() {
        var byteArray = byteArrayOf(
            0, 0, 0, 1, 103, 66, -128, 31, -23, 3, -64, -41, 64, 54, -123, 9, -88,
            0, 0, 0, 1, 104, -50, 6, -30, 0, 0, 0, 1, 101, -72, 64, 7, -65, -1
        )
        var sps = H264Util.getSps(byteArray)
        assertEquals("[0,0,0,1,103,66,-128,31,-23,3,-64,-41,64,54,-123,9,-88]", sps.toJsonString())
        var pps = H264Util.getPps(byteArray)
        assertEquals("[0,0,0,1,104,-50,6,-30]", pps.toJsonString())
        assertEquals("SPS", H264Util.getNaluTypeName(sps!!))
        assertEquals("PPS", H264Util.getNaluTypeName(pps!!))

        byteArray = byteArrayOf(
            0, 0, 0, 1, 103, 66, -128, 31, -23, 3, -64, -41, 64, 54, -123, 9, -88,
            0, 0, 0, 1, 104, -50, 6, -30
        )
        sps = H264Util.getSps(byteArray)
        assertEquals("[0,0,0,1,103,66,-128,31,-23,3,-64,-41,64,54,-123,9,-88]", sps.toJsonString())
        pps = H264Util.getPps(byteArray)
        assertEquals("[0,0,0,1,104,-50,6,-30]", pps.toJsonString())
        assertEquals("SPS", H264Util.getNaluTypeName(sps!!))
        assertEquals("PPS", H264Util.getNaluTypeName(pps!!))

        byteArray = byteArrayOf(0, 0, 0, 1, 103, 66, -128, 31, -23, 3, -64, -41, 64, 54, -123, 9, -88)
        sps = H264Util.getSps(byteArray)
        assertEquals("[0,0,0,1,103,66,-128,31,-23,3,-64,-41,64,54,-123,9,-88]", sps.toJsonString())
        pps = H264Util.getPps(byteArray)
        assertNull(pps)

        // ================================

        byteArray = byteArrayOf(0, 0, 0, 1, 104, -50, 6, -30, 0, 0, 0, 1, 101, -72, 64, 7, -65, -1)
        sps = H264Util.getSps(byteArray)
        assertNull(sps)
        pps = H264Util.getPps(byteArray)
        assertEquals("[0,0,0,1,104,-50,6,-30]", pps.toJsonString())

        byteArray = byteArrayOf(0, 0, 0, 1, 104, -50, 6, -30)
        sps = H264Util.getSps(byteArray)
        assertNull(sps)
        pps = H264Util.getPps(byteArray)
        assertEquals("[0,0,0,1,104,-50,6,-30]", pps.toJsonString())

        // ================================

        val idrBytes = byteArrayOf(0, 0, 0, 1, 0x65, 1, 2, 3, 4)
        assertEquals(true, H264Util.isIdrFrame(idrBytes))
        assertEquals(false, H264Util.isNoneIdrFrame(idrBytes))
        assertEquals("I", H264Util.getNaluTypeName(idrBytes))

        val noneIdrBytes = byteArrayOf(0, 0, 0, 1, 0x41, 1, 2, 3, 4)
        assertEquals(true, H264Util.isNoneIdrFrame(noneIdrBytes))
        assertEquals(false, H264Util.isIdrFrame(noneIdrBytes))
        assertEquals("B/P", H264Util.getNaluTypeName(noneIdrBytes))
    }
}
