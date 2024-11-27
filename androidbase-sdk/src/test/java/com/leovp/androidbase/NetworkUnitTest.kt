package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.utils.network.InternetUtil
import com.leovp.log.LLog
import com.leovp.log.LogContext
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@PrepareForTest(Log::class)
@Config(sdk = [32], shadows = [ShadowLog::class])
class NetworkUnitTest {

    @BeforeEach
    fun setUp() {
        ShadowLog.stream = System.out
        LogContext.setLogImpl(LLog(tagPrefix = "LEO"))
    }

    @Test
    fun getIpsByName() {
        var ips = InternetUtil.getIpsByHost("50d.win").toTypedArray()
        assertArrayEquals(arrayOf("142.11.215.254"), ips)

        ips = InternetUtil.getIpsByHost("barcode.50d.win").toTypedArray()
        // InternetUtil.getIpsByName("lib.leovp.com").toTypedArray().forEach { println(it) }
        assertEquals("142.11.215.254", ips[0])

        ips = InternetUtil.getIpsByHost("leo vp.com").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("50d.win   ").toTypedArray()
        assertArrayEquals(arrayOf("142.11.215.254"), ips)

        ips = InternetUtil.getIpsByHost("   50d.win   ").toTypedArray()
        assertArrayEquals(arrayOf("142.11.215.254"), ips)

        ips = InternetUtil.getIpsByHost("203.107.43.165").toTypedArray()
        assertArrayEquals(arrayOf("203.107.43.165"), ips)

        ips = InternetUtil.getIpsByHost("203.1 7.43.165").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("203..43.165").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost(".203.43.165").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("203.107.43.165   ").toTypedArray()
        assertArrayEquals(arrayOf("203.107.43.165"), ips)

        ips = InternetUtil.getIpsByHost("   203.107.43.165   ").toTypedArray()
        assertArrayEquals(arrayOf("203.107.43.165"), ips)

        ips = InternetUtil.getIpsByHost("dummy.dummy").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("http://leovp.com").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("https://leovp.com").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("http://leovp.com/abc").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("https://leovp.com/abc").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("http://www.cip.cc/175.162.7.41").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("leovp.com/resource/dummy").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("12345").toTypedArray()
        assertArrayEquals(arrayOf("0.0.48.57"), ips)

        ips = InternetUtil.getIpsByHost("abc").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByHost("abc.b").toTypedArray()
        assertArrayEquals(emptyArray(), ips)
    }
}
