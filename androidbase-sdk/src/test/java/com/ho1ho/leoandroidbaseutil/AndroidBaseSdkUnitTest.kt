package com.ho1ho.leoandroidbaseutil

import android.util.Log
import com.ho1ho.androidbase.utils.network.InternetUtil
import org.junit.Assert.assertArrayEquals
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
class AndroidBaseSdkUnitTest {
    @Test
    fun getIpsByName() {
        var ips = InternetUtil.getIpsByName("ho1ho.com").toTypedArray()
        assertArrayEquals(arrayOf("203.107.43.165"), ips)

        ips = InternetUtil.getIpsByName("lib.ho1ho.com").toTypedArray()
        assertArrayEquals(arrayOf("36.248.208.251"), ips)

        ips = InternetUtil.getIpsByName("ho1 ho.com").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName("ho1ho.com   ").toTypedArray()
        assertArrayEquals(arrayOf("203.107.43.165"), ips)

        ips = InternetUtil.getIpsByName("   ho1ho.com   ").toTypedArray()
        assertArrayEquals(arrayOf("203.107.43.165"), ips)

        ips = InternetUtil.getIpsByName("203.107.43.165").toTypedArray()
        assertArrayEquals(arrayOf("203.107.43.165"), ips)

        ips = InternetUtil.getIpsByName("203.1 7.43.165").toTypedArray()
        assertArrayEquals(arrayOf("203.0.0.1"), ips)

        ips = InternetUtil.getIpsByName("203..43.165").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName(".203.43.165").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName("203.107.43.165   ").toTypedArray()
        assertArrayEquals(arrayOf("203.107.43.165"), ips)

        ips = InternetUtil.getIpsByName("   203.107.43.165   ").toTypedArray()
        assertArrayEquals(arrayOf("203.107.43.165"), ips)

        ips = InternetUtil.getIpsByName(null).toTypedArray()
        assertArrayEquals(arrayOf("127.0.0.1"), ips)

        ips = InternetUtil.getIpsByName("dummy.dummy").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName("http://ho1ho.com").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName("https://ho1ho.com").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName("http://ho1ho.com/abc").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName("https://ho1ho.com/abc").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName("http://www.cip.cc/175.162.7.41").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName("12345").toTypedArray()
        assertArrayEquals(arrayOf("0.0.48.57"), ips)

        ips = InternetUtil.getIpsByName("abc").toTypedArray()
        assertArrayEquals(emptyArray(), ips)

        ips = InternetUtil.getIpsByName("abc.b").toTypedArray()
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
