package com.leovp.androidbase.framework

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import com.leovp.android.exts.connectivityManager
import com.leovp.androidbase.utils.network.NetworkMonitor
import com.leovp.androidbase.utils.network.NetworkMonitorScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkMonitorCleanupOwnershipTest {
    @Test
    fun `destroy releases the monitor while detached framework cleanup continues`() {
        val manager = mockk<ConnectivityManager>(relaxed = true)
        val framework = slot<ConnectivityManager.NetworkCallback>()
        every {
            manager.registerNetworkCallback(any<NetworkRequest>(), capture(framework))
        } returns Unit
        val attempts = AtomicInteger()
        val cleaned = CountDownLatch(1)
        every {
            manager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        } answers {
            if (attempts.incrementAndGet() == 1) error("transient binder failure")
            cleaned.countDown()
        }
        mockkStatic("com.leovp.android.exts.ContextExtKt")
        every { any<Context>().connectivityManager } returns manager
        val controller = Robolectric.buildActivity(TrafficMonitorTestActivity::class.java).setup()
        val activity = controller.get()
        val scheduler = IdleScheduler()
        val monitor = NetworkMonitor(activity, "127.0.0.1", { activity.title }, { scheduler })
        val field = BaseActivity::class.java.getDeclaredField("networkMonitor")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val holder = field.get(activity) as AtomicReference<NetworkMonitor?>
        try {
            monitor.startMonitor()
            holder.set(monitor)
            controller.pause().stop().destroy()

            assertNull(holder.get())
            assertTrue(scheduler.closed)
            val listener = framework.captured.javaClass.getDeclaredField("listener")
                .apply { isAccessible = true }
            assertNull(listener.get(framework.captured))
            val callback = NetworkMonitor::class.java.getDeclaredField("wifiSignalCallback")
                .apply { isAccessible = true }
            assertNull(
                "the destroyed owner must not retain a failed session handle",
                callback.get(monitor)
            )
            assertTrue(
                "cleanup must have an owner after onDestroy",
                cleaned.await(5, TimeUnit.SECONDS)
            )
        } finally {
            every {
                manager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
            } returns Unit
            monitor.stopMonitor()
            unmockkStatic("com.leovp.android.exts.ContextExtKt")
        }
    }

    private class IdleScheduler : NetworkMonitorScheduler {
        var closed = false
            private set

        override fun post(task: Runnable) = Unit

        override fun postDelayed(task: Runnable, delayMillis: Long) = Unit

        override fun cancelAll() = Unit

        override fun close() {
            closed = true
        }
    }
}
