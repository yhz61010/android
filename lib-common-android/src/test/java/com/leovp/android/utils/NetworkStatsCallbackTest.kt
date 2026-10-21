package com.leovp.android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkStatsCallbackTest {
    @Test
    fun `wifi stats listener is registered once and can be unregistered`() {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true) {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        }
        val callback: (NetworkUtil.WifiSignal) -> Unit = {}

        assertTrue(NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback))
        assertTrue(NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(context, callback))

        verify(exactly = 1) {
            connectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>()
            )
        }
        verify(exactly = 0) {
            connectivityManager.requestNetwork(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>()
            )
        }
        verify(exactly = 1) {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        }
    }

    @Test
    fun `failed framework unregister detaches the business callback`() {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true) {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        }
        var callbackCount = 0
        val callback: (NetworkUtil.WifiSignal) -> Unit = { callbackCount++ }
        val frameworkCallback = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                capture(frameworkCallback)
            )
        } returns Unit
        NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback)
        every {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        } throws
            IllegalStateException("transient unregister failure")

        val unregistered = NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(context, callback)
        frameworkCallback.captured.onLost(mockk(relaxed = true))

        assertFalse(unregistered)
        assertEquals(0, callbackCount)
        verify(exactly = 1) {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        }
    }

    @Test
    fun `failed replacement unregister does not register a second callback`() {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true) {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        }
        val callback: (NetworkUtil.WifiSignal) -> Unit = {}
        NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback)
        every {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        } throws IllegalStateException("transient unregister failure")

        NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback)

        verify(exactly = 1) {
            connectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>()
            )
        }
    }

    @Test
    fun `registration failure is reported without retaining a mapping`() {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val appContext = mockk<Context>(relaxed = true)
        val context = mockk<Context> {
            every { applicationContext } returns appContext
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        }
        val callback: (NetworkUtil.WifiSignal) -> Unit = {}
        every {
            connectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>()
            )
        } throws IllegalStateException("registration failed") andThen Unit

        val first = NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback)
        val second = NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback)

        assertFalse(first)
        assertTrue(second)
        verify(exactly = 2) {
            connectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>()
            )
        }
    }
}
