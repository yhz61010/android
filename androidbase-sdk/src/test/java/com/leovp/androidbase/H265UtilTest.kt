package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.utils.media.H265Util
import com.leovp.lib_bytes.toHexStringLE
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import kotlin.system.measureNanoTime

/**
 * Author: Michael Leo
 * Date: 2021/5/13 3:43 PM
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class H265UtilTest {

    @Test
    fun h265Test() {
        val vspByteArray = byteArrayOf(0, 0, 0, 1, 0x40, 1, 0xC, 1, 0xFF.toByte(), 0xFF.toByte(), 1, 60, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 78, 0x2C, 9)
        val spsByteArray = byteArrayOf(
            0, 0, 0, 1, 0x42, 1, 1, 1, 0x60, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0x78,
            0xA0.toByte(), 4, 0x62, 0, 0xFC.toByte(), 0x7C, 0xBA.toByte(), 0x2D, 0x24, 0xB0.toByte(), 0x4B, 0xB2.toByte()
        )
        val ppsByteArray = byteArrayOf(0, 0, 0, 1, 0x44, 1, 0xC0.toByte(), 0x66, 0x3C, 0xE, 0xC6.toByte(), 0x40)
        val idrByteArray = byteArrayOf(0, 0, 0, 1, 0x26, 1, 2, 3, 4, 5)
        val idrNLPByteArray = byteArrayOf(0, 0, 0, 1, 0x28, 1, 2, 3, 4, 5)
        val pByteArray = byteArrayOf(0, 0, 0, 1, 2, 1, 2, 3, 4, 5)

        var isVps = H265Util.isVps(vspByteArray)
        Assert.assertEquals(true, isVps)
        isVps = H265Util.isVps(spsByteArray)
        Assert.assertEquals(false, isVps)

        var isSps = H265Util.isSps(spsByteArray)
        Assert.assertEquals(true, isSps)
        isSps = H265Util.isSps(ppsByteArray)
        Assert.assertEquals(false, isSps)

        var isPps = H265Util.isPps(ppsByteArray)
        Assert.assertEquals(true, isPps)
        isPps = H265Util.isSps(idrByteArray)
        Assert.assertEquals(false, isPps)

        val naluVps = H265Util.getNaluType(vspByteArray)
        Assert.assertEquals(H265Util.NALU_TYPE_VPS, naluVps)

        val naluSps = H265Util.getNaluType(spsByteArray)
        Assert.assertEquals(H265Util.NALU_TYPE_SPS, naluSps)

        val naluPps = H265Util.getNaluType(ppsByteArray)
        Assert.assertEquals(H265Util.NALU_TYPE_PPS, naluPps)

        val naluIdr = H265Util.getNaluType(idrByteArray)
        Assert.assertEquals(H265Util.NALU_TYPE_IDR_W_RADL, naluIdr)

        val naluIdrNLP = H265Util.getNaluType(idrNLPByteArray)
        Assert.assertEquals(H265Util.NALU_TYPE_IDR_N_LP, naluIdrNLP)

        var cost = measureNanoTime {
            H265Util.getNaluType(pByteArray)
        }
        println("getNaluType cost=${cost / 1000}us")

        cost = measureNanoTime {
            H265Util.getNaluTypeName(pByteArray)
        }
        println("getNaluTypeName cost=${cost / 1000}us")

        Assert.assertEquals(H265Util.NALU_TRAIL_R, H265Util.getNaluType(pByteArray))
        Assert.assertEquals("P_TRAIL_R", H265Util.getNaluTypeName(pByteArray))

        val csdByteArray = byteArrayOf(
            0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5,
            0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10,
            0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15,
            0, 0, 0, 1, 0x28, 0x1, 0xAF.toByte(), 0x78, 0xCD.toByte(), 0x3B
        )

        val vpsBytes = H265Util.getVps(csdByteArray)
        println(vpsBytes?.toHexStringLE())
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5), vpsBytes)

        val spsBytes = H265Util.getSps(csdByteArray)
        println(spsBytes?.toHexStringLE())
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10), spsBytes)

        val ppsBytes = H265Util.getPps(csdByteArray)
        println(ppsBytes?.toHexStringLE())
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15), ppsBytes)
    }
}