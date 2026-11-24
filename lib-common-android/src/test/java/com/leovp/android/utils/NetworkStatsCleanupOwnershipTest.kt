package com.leovp.android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkStatsCleanupOwnershipTest {
    @Test
    fun `blocked cleanup does not stall registration rejection or another listener stop`() {
        val manager = mockk<ConnectivityManager>(relaxed = true)
        val app = mockk<Context>(relaxed = true)
        every { app.applicationContext } returns app
        every { app.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        val callbacks = mutableListOf<ConnectivityManager.NetworkCallback>()
        every {
            manager.registerNetworkCallback(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>()
            )
        } answers {
            callbacks += secondArg<ConnectivityManager.NetworkCallback>()
        }
        val first: (NetworkUtil.WifiSignal) -> Unit = {}
        val second: (NetworkUtil.WifiSignal) -> Unit = {}
        assertTrue(NetworkUtil.getWifiNetworkStatsAboveAndroidS(app, first))
        assertTrue(NetworkUtil.getWifiNetworkStatsAboveAndroidS(app, second))
        val attempts = AtomicInteger()
        val retryEntered = CountDownLatch(1)
        val releaseRetry = CountDownLatch(1)
        every { manager.unregisterNetworkCallback(callbacks.first()) } answers {
            if (attempts.incrementAndGet() == 1) error("transient binder failure")
            retryEntered.countDown()
            assertTrue(releaseRetry.await(10, TimeUnit.SECONDS))
        }
        val otherLifecycleFinished = CountDownLatch(1)
        val rejected = AtomicBoolean(false)
        val stopped = AtomicBoolean(false)
        val third: (NetworkUtil.WifiSignal) -> Unit = {}
        val lifecycle = Thread {
            rejected.set(!NetworkUtil.getWifiNetworkStatsAboveAndroidS(app, third))
            stopped.set(NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(second))
            otherLifecycleFinished.countDown()
        }
        var executor: ScheduledThreadPoolExecutor? = null
        try {
            assertFalse(NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(first))
            executor = NetworkUtil::class.java.getDeclaredField("wifiCleanupExecutor")
                .apply { isAccessible = true }.get(NetworkUtil) as ScheduledThreadPoolExecutor
            assertTrue(retryEntered.await(5, TimeUnit.SECONDS))
            lifecycle.start()
            assertTrue(
                "a worker binder call must not hold the lifecycle registry lock",
                otherLifecycleFinished.await(3, TimeUnit.SECONDS)
            )
            assertTrue(rejected.get())
            assertTrue(stopped.get())
        } finally {
            releaseRetry.countDown()
            lifecycle.join(3_000)
            NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(second)
            NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(third)
            assertTrue(executor?.awaitTermination(5, TimeUnit.SECONDS) != false)
        }
    }

    @Test
    fun `pending cleanup blocks new registrations but keeps other active listeners`() {
        val manager = mockk<ConnectivityManager>(relaxed = true)
        val app = mockk<Context>(relaxed = true)
        every { app.applicationContext } returns app
        every { app.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        val framework = mutableListOf<ConnectivityManager.NetworkCallback>()
        every {
            manager.registerNetworkCallback(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>()
            )
        } answers {
            framework += secondArg<ConnectivityManager.NetworkCallback>()
        }
        val canUnregister = AtomicBoolean(false)
        val cleaned = CountDownLatch(1)
        every {
            manager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        } answers {
            if (!canUnregister.get()) error("service unavailable")
            cleaned.countDown()
        }
        val first: (NetworkUtil.WifiSignal) -> Unit = {}
        val emissions = AtomicInteger()
        val second: (NetworkUtil.WifiSignal) -> Unit = { emissions.incrementAndGet() }
        try {
            assertTrue(NetworkUtil.getWifiNetworkStatsAboveAndroidS(app, first))
            assertTrue(NetworkUtil.getWifiNetworkStatsAboveAndroidS(app, second))
            assertFalse(NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(first))
            val executor = NetworkUtil::class.java.getDeclaredField("wifiCleanupExecutor")
                .apply { isAccessible = true }.get(NetworkUtil) as ScheduledThreadPoolExecutor
            repeat(10) {
                assertFalse(NetworkUtil.getWifiNetworkStatsAboveAndroidS(app) {})
            }
            framework.last().onLost(mockk(relaxed = true))
            assertTrue("existing subscriptions must remain active", emissions.get() == 1)
            verify(exactly = 2) {
                manager.registerNetworkCallback(
                    any<NetworkRequest>(),
                    any<ConnectivityManager.NetworkCallback>()
                )
            }
            canUnregister.set(true)
            assertTrue(cleaned.await(5, TimeUnit.SECONDS))
            // Framework return precedes registry removal. Wait for the empty worker to shut down
            // before asserting that registration admission has reopened.
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            val fresh: (NetworkUtil.WifiSignal) -> Unit = {}
            assertTrue(NetworkUtil.getWifiNetworkStatsAboveAndroidS(app, fresh))
            assertTrue(NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(fresh))
        } finally {
            canUnregister.set(true)
            NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(first)
            NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(second)
        }
    }

    @Test
    fun `failed unregister releases the business key and retries without its owner`() {
        val manager = mockk<ConnectivityManager>(relaxed = true)
        val app = mockk<Context>(relaxed = true) {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        }
        val activity = mockk<Context>(relaxed = true) {
            every { applicationContext } returns app
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        }
        val framework = slot<ConnectivityManager.NetworkCallback>()
        every {
            manager.registerNetworkCallback(any<NetworkRequest>(), capture(framework))
        } returns Unit
        val retried = CountDownLatch(1)
        var attempts = 0
        every {
            manager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        } answers {
            if (++attempts == 1) error("transient binder failure")
            retried.countDown()
        }
        val callback: (NetworkUtil.WifiSignal) -> Unit = { error("detached listener called") }
        var executor: ScheduledThreadPoolExecutor? = null
        try {
            assertTrue(NetworkUtil.getWifiNetworkStatsAboveAndroidS(activity, callback))
            assertFalse(NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(callback))
            executor = NetworkUtil::class.java.getDeclaredField("wifiCleanupExecutor")
                .apply { isAccessible = true }.get(NetworkUtil) as ScheduledThreadPoolExecutor
            val field = NetworkUtil::class.java.getDeclaredField("wifiNetworkCallbacks")
                .apply { isAccessible = true }
            val callbacks = field.get(NetworkUtil) as Map<*, *>
            assertFalse(
                "cleanup must not retain the business lambda as its map key",
                callbacks.containsKey(callback)
            )
            framework.captured.onLost(mockk(relaxed = true))
            assertTrue(
                "cleanup must retry after the Activity has released its monitor",
                retried.await(5, TimeUnit.SECONDS)
            )
        } finally {
            every {
                manager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
            } returns Unit
            NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(callback)
            assertTrue(executor?.awaitTermination(5, TimeUnit.SECONDS) != false)
        }
    }
}
