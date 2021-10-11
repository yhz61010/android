package com.leovp.leoandroidbaseutil

import android.util.Log
import com.leovp.androidbase.utils.cipher.GZipUtil
import com.leovp.min_base_sdk.bytes.toHexStringLE
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
class GZipUtilTest {

    @Test
    fun compress() {
        val string = "I have a dream. A song to sing. To help me cope with anything."
        val compressedBytes: ByteArray = GZipUtil.compress(string)
        Assert.assertEquals(
            "1F,8B,8,0,0,0,0,0,0,0,15,C9,C1,9,80,30,C,40,D1,55,FE,4,DD,C1,A3,77,17,8,1A,9A,82,4D,8A,D,8A,DB,8B,EF,FA,56,4C,6E,45,38,2E,95,5E,58,98,E1,95,C,66,F3,5A,D8,2,D3,73,D0,95,3D,86,F2,B4,34,C4,DF,B4,BF,3F,23,59,60,72,3E,0,0,0",
            compressedBytes.toHexStringLE()
        )
        Assert.assertEquals(true, GZipUtil.isGzip(compressedBytes))
    }

    @Test
    fun decompress() {
        val string = "I have a dream. A song to sing. To help me cope with anything."
        val compressedBytes: ByteArray = GZipUtil.compress(string)
        val decompressString: String = GZipUtil.decompress(compressedBytes)!!
        Assert.assertEquals(string, decompressString)

        val errorBytes: ByteArray = byteArrayOf(0x1, 0x2, 0x3, 0x4)
        Assert.assertEquals(false, GZipUtil.isGzip(errorBytes))
        val errorString: String? = GZipUtil.decompress(errorBytes)
        Assert.assertEquals(null, errorString)
    }
}