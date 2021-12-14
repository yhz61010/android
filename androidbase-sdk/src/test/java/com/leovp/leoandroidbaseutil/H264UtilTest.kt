package com.leovp.leoandroidbaseutil

import android.util.Log
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.media.H264Util
import org.junit.Assert
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
class H264UtilTest {

    @Test
    fun h264Test() {
        val byteArray = byteArrayOf(0, 0, 0, 1, 103, 66, -128, 31, -23, 3, -64, -41, 64, 54, -123, 9, -88, 0, 0, 0, 1, 104, -50, 6, -30, 0, 0, 0, 1, 101, -72, 64, 7, -65, -1)
        val sps = H264Util.getSps(byteArray)!!
        Assert.assertEquals("[0,0,0,1,103,66,-128,31,-23,3,-64,-41,64,54,-123,9,-88]", sps.toJsonString())
        val pps = H264Util.getPps(byteArray)!!
        Assert.assertEquals("[0,0,0,1,104,-50,6,-30]", pps.toJsonString())

        val idrBytes = byteArrayOf(0, 0, 0, 1, 0x65, 1, 2, 3, 4)
        Assert.assertEquals(true, H264Util.isIdrFrame(idrBytes))
        Assert.assertEquals(false, H264Util.isNoneIdrFrame(idrBytes))
        Assert.assertEquals("I", H264Util.getNaluTypeName(idrBytes))

        val noneIdrBytes = byteArrayOf(0, 0, 0, 1, 0x41, 1, 2, 3, 4)
        Assert.assertEquals(true, H264Util.isNoneIdrFrame(noneIdrBytes))
        Assert.assertEquals(false, H264Util.isIdrFrame(noneIdrBytes))
        Assert.assertEquals("B/P", H264Util.getNaluTypeName(noneIdrBytes))

        Assert.assertEquals("SPS", H264Util.getNaluTypeName(sps))
        Assert.assertEquals("PPS", H264Util.getNaluTypeName(pps))
    }
}