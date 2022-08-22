package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.utils.network.InternetUtil
import com.leovp.log.LLog
import com.leovp.log.LogContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class NetworkUnitTest {

    @Before
    fun preTest() {
        LogContext.setLogImp(LLog("LEO"))
    }

    @Test
    fun getIpsByName() {
        var ips = InternetUtil.getIpsByHost("50d.win").toTypedArray()
        assertArrayEquals(arrayOf("142.11.215.254"), ips)

        ips = InternetUtil.getIpsByHost("barcode.50d.win").toTypedArray()
//        InternetUtil.getIpsByName("lib.leovp.com").toTypedArray().forEach { println(it) }
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

    @Before
    fun beforeTest() {
        PowerMockito.mockStatic(Log::class.java)
        Mockito.`when`(Log.e(any(), any())).then {
            println(it.arguments[1] as String)
            1
        }
        Mockito.`when`(Log.w(any(String::class.java), any(String::class.java))).then {
            println(it.arguments[1])
            1
        }
        Mockito.`when`(Log.w(any(String::class.java), any(Throwable::class.java))).then {
            println((it.arguments[1] as Throwable).message)
            1
        }
        Mockito.`when`(Log.i(any(), any())).then {
            println(it.arguments[1] as String)
            1
        }
        Mockito.`when`(Log.d(any(), any())).then {
            println(it.arguments[1] as String)
            1
        }
    }
}
