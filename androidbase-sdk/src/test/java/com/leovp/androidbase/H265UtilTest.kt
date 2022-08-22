package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.utils.media.H265Util
import com.leovp.bytes.toHexStringLE
import kotlin.system.measureNanoTime
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Author: Michael Leo
 * Date: 2021/5/13 3:43 PM
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class H265UtilTest {

    @Test
    fun h265Test() {
        val vspByteArray = byteArrayOf(
            0, 0, 0, 1, 0x40, 1, 0xC, 1, 0xFF.toByte(), 0xFF.toByte(), 1,
            60, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 78, 0x2C, 9
        )
        val spsByteArray = byteArrayOf(
            0, 0, 0, 1, 0x42, 1, 1, 1, 0x60, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0x78,
            0xA0.toByte(), 4, 0x62, 0, 0xFC.toByte(), 0x7C, 0xBA.toByte(), 0x2D, 0x24, 0xB0.toByte(),
            0x4B, 0xB2.toByte()
        )
        val ppsByteArray = byteArrayOf(
            0, 0, 0, 1, 0x44, 1, 0xC0.toByte(), 0x66, 0x3C, 0xE, 0xC6.toByte(),
            0x40
        )
        val seiByteArray = byteArrayOf(
            0, 0, 0, 1, 0x4E, 1, 5, 0x1A, 0x47, 0x56, 0x4A, 0xDC.toByte(),
            0x5C, 0x4C, 0x43, 0x3F
        )
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

        var isSei = H265Util.isSei(seiByteArray)
        Assert.assertEquals(true, isSei)
        isSei = H265Util.isSei(ppsByteArray)
        Assert.assertEquals(false, isSei)

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
            0, 0, 0, 1, 0x28, 0x1, 0xAF.toByte(), 0x78, 0xCD.toByte(), 0x3B,
            0, 0, 0, 1, 0x4E, 1, 5, 0x1A, 0x47, 0x56, 0x4A, 0xDC.toByte(), 0x5C, 0x4C, 0x43, 0x3F
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

        val seiBytes = H265Util.getSei(csdByteArray)
        println(seiBytes?.toHexStringLE())
        Assert.assertArrayEquals(
            byteArrayOf(
                0, 0, 0, 1, 0x4E, 1, 5, 0x1A, 0x47, 0x56, 0x4A, 0xDC.toByte(),
                0x5C, 0x4C, 0x43, 0x3F
            ),
            seiBytes
        )

        // ==================
        var fullCsdByteArray = byteArrayOf(0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5)

        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5), H265Util.getVps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getPps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(
            0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5,
            0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10,
        )
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5), H265Util.getVps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10), H265Util.getSps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getPps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(
            0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5,
            0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10,
            0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15,
        )
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5), H265Util.getVps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10), H265Util.getSps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15), H265Util.getPps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(
            0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5,
            0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10,
            0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15,
            0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24,
        )
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5), H265Util.getVps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10), H265Util.getSps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15), H265Util.getPps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24), H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(
            0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5,
            0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10,
            0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15,
            0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24,
            0, 0, 0, 1, 0x28, 0x1, 0xAF.toByte(), 0x78, 0xCD.toByte(), 0x3B
        )
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5), H265Util.getVps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10), H265Util.getSps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15), H265Util.getPps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24), H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(
            0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10,
            0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15,
            0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24,
            0, 0, 0, 1, 0x28, 0x1, 0xAF.toByte(), 0x78, 0xCD.toByte(), 0x3B
        )
        Assert.assertNull(null, H265Util.getVps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10), H265Util.getSps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15), H265Util.getPps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24), H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(
            0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15,
            0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24,
            0, 0, 0, 1, 0x28, 0x1, 0xAF.toByte(), 0x78, 0xCD.toByte(), 0x3B
        )
        Assert.assertNull(null, H265Util.getVps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15), H265Util.getPps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24), H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(
            0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24,
            0, 0, 0, 1, 0x28, 0x1, 0xAF.toByte(), 0x78, 0xCD.toByte(), 0x3B
        )
        Assert.assertNull(null, H265Util.getVps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24), H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5)
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x40, 1, 2, 3, 4, 5), H265Util.getVps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getPps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10)
        Assert.assertNull(null, H265Util.getVps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x42, 6, 7, 8, 9, 10), H265Util.getSps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getPps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15)
        Assert.assertNull(null, H265Util.getVps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x44, 11, 12, 13, 14, 15), H265Util.getPps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSei(fullCsdByteArray))

        // ==================

        fullCsdByteArray = byteArrayOf(0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24)
        Assert.assertNull(null, H265Util.getVps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getSps(fullCsdByteArray))
        Assert.assertNull(null, H265Util.getPps(fullCsdByteArray))
        Assert.assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x4E, 20, 21, 22, 23, 24), H265Util.getSei(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isVps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isSps(fullCsdByteArray))
        Assert.assertEquals(false, H265Util.isPps(fullCsdByteArray))
        Assert.assertEquals(true, H265Util.isSei(fullCsdByteArray))
    }
}
