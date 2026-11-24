package com.leovp.android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import java.util.concurrent.TimeUnit
import org.junit.After
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
    private val managers = mutableListOf<ConnectivityManager>()

    private fun context(manager: ConnectivityManager): Context {
        managers += manager
        val app = mockk<Context>(relaxed = true)
        every { app.applicationContext } returns app
        every { app.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        return app
    }

    @After
    fun releaseRegistrations() {
        managers.forEach { manager ->
            every {
                manager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
            } returns Unit
        }
        val callbacksField = NetworkUtil::class.java.getDeclaredField("wifiNetworkCallbacks")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val callbacks = callbacksField.get(NetworkUtil) as Map<(NetworkUtil.WifiSignal) -> Unit, *>
        callbacks.keys.toList().forEach { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(it) }
        // A pending callback is intentionally no longer addressable by its business key.
        val pendingField = NetworkUtil::class.java.getDeclaredField("pendingWifiCleanup")
            .apply { isAccessible = true }
        val lockField = NetworkUtil::class.java.getDeclaredField("wifiNetworkCallbackLock")
            .apply { isAccessible = true }
        val lock = checkNotNull(lockField.get(NetworkUtil))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (synchronized(lock) { (pendingField.get(NetworkUtil) as Set<*>).isNotEmpty() }) {
            assertTrue("pending test cleanup did not finish", System.nanoTime() < deadline)
            Thread.sleep(10)
        }
    }

    @Test
    fun `wifi stats listener is registered once and can be unregistered`() {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = context(connectivityManager)
        val callback: (NetworkUtil.WifiSignal) -> Unit = {}

        assertTrue(NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback))
        assertTrue(NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(callback))

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
        val context = context(connectivityManager)
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

        val unregistered = NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(callback)
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
    fun `failed framework unregister retries independently of repeated calls`() {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = context(connectivityManager)
        val callback: (NetworkUtil.WifiSignal) -> Unit = {}
        NetworkUtil.getWifiNetworkStatsAboveAndroidS(context, callback)
        every {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        } throws IllegalStateException("transient unregister failure") andThen Unit

        assertFalse(NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(callback))
        assertTrue(NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(callback))

        verify(timeout = 5_000, exactly = 2) {
            connectivityManager.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        }
    }

    @Test
    fun `failed replacement unregister does not register a second callback`() {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = context(connectivityManager)
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
        val context = context(connectivityManager)
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
