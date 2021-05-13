package com.leovp.leoandroidbaseutil

import android.util.Log
import com.leovp.androidbase.utils.media.H265Util
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
        val vspByteArray = byteArrayOf(0, 0, 0, 1, 0x40, 1)
        val spsByteArray = byteArrayOf(0, 0, 0, 1, 0x42, 1)
        val ppsByteArray = byteArrayOf(0, 0, 0, 1, 0x44, 1)
        val idrByteArray = byteArrayOf(0, 0, 0, 1, 0x26, 1)

        val isVps = H265Util.isVps(vspByteArray)
        Assert.assertEquals(true, isVps)

        val naluVps = H265Util.getNaluType(vspByteArray)
        Assert.assertEquals(H265Util.NALU_TYPE_VPS, naluVps)

        val naluSps = H265Util.getNaluType(spsByteArray)
        Assert.assertEquals(H265Util.NALU_TYPE_SPS, naluSps)

        val naluPps = H265Util.getNaluType(ppsByteArray)
        Assert.assertEquals(H265Util.NALU_TYPE_PPS, naluPps)

        val naluIdr = H265Util.getNaluType(idrByteArray)
        Assert.assertEquals(H265Util.NALU_TYPE_IDR, naluIdr)
    }
}