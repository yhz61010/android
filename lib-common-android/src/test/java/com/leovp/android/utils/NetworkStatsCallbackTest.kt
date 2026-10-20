package com.leovp.android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        val context = mockk<Context> {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        }
        val callback: (NetworkUtil.WifiSignal) -> Unit = {}

        NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback)
        NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(context, callback)

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
    fun `failed framework unregister retains the callback for retry`() {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = mockk<Context> {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        }
        val callback: (NetworkUtil.WifiSignal) -> Unit = {}
        NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback)
        every {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        } throws
            IllegalStateException("transient unregister failure")

        NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(context, callback)
        every {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        } returns Unit

        NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(context, callback)

        verify(exactly = 2) {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        }
    }

    @Test
    fun `failed replacement unregister does not register a second callback`() {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = mockk<Context> {
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
}
