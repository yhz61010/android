package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.androidbase.utils.cipher.GZipUtil
import com.leovp.bytes.toHexStringLE
import kotlin.test.assertEquals
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
class GZipUtilTest {

    // private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    //
    // private fun randomStringByKotlinRandom(len: Int) = (1..len)
    //     .map { Random.nextInt(0, charPool.size).let { charPool[it] } }
    //     .joinToString("")

    private val constString = "I have a dream. A song to sing. To help me cope with anything."

    @Test
    fun compress() {
        // for (i in 0 until 100) {
        //     val randLen = 100 * 1024 // Random.nextInt(10_000, 500_000)
        //     val randStr = randomStringByKotlinRandom(randLen)
        //     val st = System.nanoTime()
        //     GZipUtil.compress(randStr)
        //     val ed = System.nanoTime()
        //     println("cost[$randLen]=${(ed - st)/1000}us")
        // }

        val compressedBytes: ByteArray = GZipUtil.compress(constString)
        // println("=====")
        // println(compressedBytes.toHexStringLE(true,""))
        // println("=====")
        assertEquals(
            "1F8B08000000000000FF15C9C10980300C40D155FE04DDC1A37717081A9A824D8A0D8ADB8BEFFA564C6E45382E955E589" +
                "8E1950C66F35AD802D373D0953D86F2B434C4DFB4BF3F235960723E000000",
            compressedBytes.toHexStringLE(true, "")
        )
        assertEquals(true, GZipUtil.isGzip(compressedBytes))
    }

    @Test
    fun decompress() {
        val byteString = "1F8B08000000000000FF15C9C10980300C40D155FE04DDC1A37717081A9A824D8A0D8ADB8BEFFA564C6E45382E955E589" +
            "8E1950C66F35AD802D373D0953D86F2B434C4DFB4BF3F235960723E000000"
        val decompressString: String = GZipUtil.decompress(byteString.hexToByteArray())!!
        assertEquals(constString, decompressString)

        val errorBytes: ByteArray = byteArrayOf(0x1, 0x2, 0x3, 0x4)
        assertEquals(false, GZipUtil.isGzip(errorBytes))
        val errorString: String? = GZipUtil.decompress(errorBytes)
        assertEquals(null, errorString)
    }
}
