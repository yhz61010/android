package com.leovp.androidbase.utils.network

import android.app.Activity
import android.app.Application
import android.content.Context
import com.leovp.android.utils.NetworkUtil
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkMonitorLifecycleTest {
    @Test
    fun `monitor can start again after stop`() {
        mockkObject(NetworkUtil)
        every { NetworkUtil.getLatency(any(), any(), any()) } returns 1.0
        every { NetworkUtil.getWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        every { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(any(), any()) } returns true

        val emissions = AtomicInteger()
        val schedulers = mutableListOf<FakeScheduler>()
        val context = RuntimeEnvironment.getApplication() as Application
        val monitor = NetworkMonitor(
            context,
            "127.0.0.1",
            { emissions.incrementAndGet() },
            schedulerFactory = { FakeScheduler().also(schedulers::add) }
        )

        try {
            monitor.startMonitor()
            schedulers.single().runNext()
            assertEquals(1, emissions.get())
            monitor.stopMonitor()

            monitor.startMonitor()
            schedulers.last().runNext()
            assertEquals(2, emissions.get())
        } finally {
            monitor.stopMonitor()
            unmockkObject(NetworkUtil)
        }
    }

    @Test
    fun `monitor continues after a recoverable callback failure`() {
        mockkObject(NetworkUtil)
        every { NetworkUtil.getLatency(any(), any(), any()) } returns 1.0
        every { NetworkUtil.getWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        every { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(any(), any()) } returns true

        val attempts = AtomicInteger()
        val scheduler = FakeScheduler()
        val context = RuntimeEnvironment.getApplication() as Application
        val monitor = NetworkMonitor(
            context,
            "127.0.0.1",
            {
                if (attempts.incrementAndGet() == 1) {
                    error("transient callback failure")
                }
            },
            schedulerFactory = { scheduler }
        )

        try {
            monitor.startMonitor()
            scheduler.runNext()
            scheduler.runNext()
            assertEquals(2, attempts.get())
        } finally {
            monitor.stopMonitor()
            unmockkObject(NetworkUtil)
        }
    }

    @Test
    fun `stop and restart suppresses an in-flight callback from the old generation`() {
        verifyInFlightCallbackIsSuppressed { monitor ->
            monitor.stopMonitor()
            monitor.startMonitor()
        }
    }

    @Test
    fun `repeated start suppresses an in-flight callback from the old generation`() {
        verifyInFlightCallbackIsSuppressed { monitor -> monitor.startMonitor() }
    }

    @Test
    fun `stop between sampling and dispatch suppresses the old callback`() {
        mockkObject(NetworkUtil)
        every { NetworkUtil.getLatency(any(), any(), any()) } returns 1.0
        every { NetworkUtil.getWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        every { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(any(), any()) } returns true

        val dispatchReached = CountDownLatch(1)
        val releaseDispatch = CountDownLatch(1)
        val scheduler = FakeScheduler()
        val emissions = AtomicInteger()
        val context = RuntimeEnvironment.getApplication() as Application
        val monitor = NetworkMonitor(
            context,
            "127.0.0.1",
            { emissions.incrementAndGet() },
            schedulerFactory = { scheduler },
            beforeResultDispatch = {
                dispatchReached.countDown()
                assertTrue(releaseDispatch.await(3, TimeUnit.SECONDS))
            }
        )

        try {
            monitor.startMonitor()
            val iteration = Thread { scheduler.runNext() }
            iteration.start()
            assertTrue(dispatchReached.await(3, TimeUnit.SECONDS))

            monitor.stopMonitor()
            releaseDispatch.countDown()
            iteration.join(3_000)
            assertFalse(iteration.isAlive)

            assertEquals(0, emissions.get())
        } finally {
            releaseDispatch.countDown()
            monitor.stopMonitor()
            unmockkObject(NetworkUtil)
        }
    }

    @Test
    fun `failed initial scheduling rolls back the monitor session`() {
        mockkObject(NetworkUtil)
        every { NetworkUtil.getWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        every { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(any(), any()) } returns true

        val scheduler = FakeScheduler(failOnPost = true)
        val context = RuntimeEnvironment.getApplication() as Application
        val monitor = NetworkMonitor(
            context,
            "127.0.0.1",
            {},
            schedulerFactory = { scheduler }
        )

        try {
            val error = runCatching { monitor.startMonitor() }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(scheduler.closed)
            assertFalse(scheduler.hasTasks())
        } finally {
            monitor.stopMonitor()
            unmockkObject(NetworkUtil)
        }
    }

    @Test
    fun `repeated start does not reuse Wi-Fi signal from the previous generation`() {
        mockkObject(NetworkUtil)
        every { NetworkUtil.getLatency(any(), any(), any()) } returns 1.0
        val registrations = AtomicInteger()
        every { NetworkUtil.getWifiNetworkStatsAboveAndroidS(any(), any()) } answers {
            if (registrations.incrementAndGet() == 1) {
                secondArg<(NetworkUtil.WifiSignal) -> Unit>().invoke(
                    NetworkUtil.WifiSignal(72, -45, 4, 90)
                )
            }
            true
        }
        every { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(any(), any()) } returns true

        val schedulers = mutableListOf<FakeScheduler>()
        val results = mutableListOf<NetworkMonitor.NetworkMonitorResult>()
        val context = RuntimeEnvironment.getApplication() as Application
        val monitor = NetworkMonitor(
            context,
            "127.0.0.1",
            results::add,
            schedulerFactory = { FakeScheduler().also(schedulers::add) }
        )

        try {
            monitor.startMonitor()
            schedulers.last().runNext()
            monitor.startMonitor()
            schedulers.last().runNext()

            assertEquals(90, results.first().wifiScore)
            assertEquals(-1, results.last().wifiScore)
        } finally {
            monitor.stopMonitor()
            unmockkObject(NetworkUtil)
        }
    }

    @Test
    fun `late Wi-Fi callback from an old session cannot overwrite the current signal`() {
        mockkObject(NetworkUtil)
        every { NetworkUtil.getLatency(any(), any(), any()) } returns 1.0
        val callbacks = mutableListOf<(NetworkUtil.WifiSignal) -> Unit>()
        every { NetworkUtil.getWifiNetworkStatsAboveAndroidS(any(), any()) } answers {
            callbacks += secondArg<(NetworkUtil.WifiSignal) -> Unit>()
            true
        }
        every { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        val schedulers = mutableListOf<FakeScheduler>()
        val results = mutableListOf<NetworkMonitor.NetworkMonitorResult>()
        val monitor = NetworkMonitor(
            RuntimeEnvironment.getApplication(),
            "127.0.0.1",
            results::add,
            schedulerFactory = { FakeScheduler().also(schedulers::add) }
        )

        try {
            monitor.startMonitor()
            monitor.startMonitor()
            callbacks.last().invoke(NetworkUtil.WifiSignal(100, -40, 5, 95))
            callbacks.first().invoke(NetworkUtil.WifiSignal(-1, -1, -1, -1))
            schedulers.last().runNext()

            assertEquals(95, results.single().wifiScore)
        } finally {
            monitor.stopMonitor()
            unmockkObject(NetworkUtil)
        }
    }

    @Test
    fun `monitor and traffic helper retain only application context`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val app = activity.applicationContext
        val monitorContext = slot<Context>()
        mockkObject(NetworkUtil)
        every { NetworkUtil.getLatency(capture(monitorContext), any(), any()) } returns 1.0
        every { NetworkUtil.getWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        every { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        val scheduler = FakeScheduler()
        val monitor = NetworkMonitor(activity, "127.0.0.1", {}, schedulerFactory = { scheduler })
        val constructor = TrafficStatHelper::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
        val helper = constructor.newInstance(activity)

        try {
            monitor.startMonitor()
            scheduler.runNext()
            assertSame(app, monitorContext.captured)
            assertSame(app, helper.ctx)
        } finally {
            monitor.stopMonitor()
            unmockkObject(NetworkUtil)
            activity.finish()
        }
    }

    @Test
    fun `fatal callback failure does not schedule another iteration`() {
        mockkObject(NetworkUtil)
        every { NetworkUtil.getLatency(any(), any(), any()) } returns 1.0
        every { NetworkUtil.getWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        every { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        val scheduler = FakeScheduler()
        val monitor = NetworkMonitor(
            RuntimeEnvironment.getApplication(),
            "127.0.0.1",
            { throw AssertionError("fatal callback failure") },
            schedulerFactory = { scheduler }
        )
        try {
            monitor.startMonitor()
            assertTrue(runCatching { scheduler.runNext() }.exceptionOrNull() is AssertionError)
            assertFalse(scheduler.hasTasks())
        } finally {
            monitor.stopMonitor()
            unmockkObject(NetworkUtil)
        }
    }

    private fun verifyInFlightCallbackIsSuppressed(restart: (NetworkMonitor) -> Unit) {
        mockkObject(NetworkUtil)
        val probeStarted = CountDownLatch(1)
        val releaseProbe = CountDownLatch(1)
        every { NetworkUtil.getLatency(any(), any(), any()) } answers {
            probeStarted.countDown()
            assertTrue(releaseProbe.await(3, TimeUnit.SECONDS))
            1.0
        }
        every { NetworkUtil.getWifiNetworkStatsAboveAndroidS(any(), any()) } returns true
        every { NetworkUtil.unregisterWifiNetworkStatsAboveAndroidS(any(), any()) } returns true

        val schedulers = mutableListOf<FakeScheduler>()
        val emissions = AtomicInteger()
        val context = RuntimeEnvironment.getApplication() as Application
        val monitor = NetworkMonitor(
            context,
            "127.0.0.1",
            { emissions.incrementAndGet() },
            schedulerFactory = { FakeScheduler().also(schedulers::add) }
        )

        try {
            monitor.startMonitor()
            val oldIteration = Thread { schedulers.single().runNext() }
            oldIteration.start()
            assertTrue(probeStarted.await(3, TimeUnit.SECONDS))

            restart(monitor)
            releaseProbe.countDown()
            oldIteration.join(3_000)
            assertFalse(oldIteration.isAlive)

            assertEquals(0, emissions.get())
            schedulers.last().runNext()
            assertEquals(1, emissions.get())
        } finally {
            releaseProbe.countDown()
            monitor.stopMonitor()
            unmockkObject(NetworkUtil)
        }
    }

    private class FakeScheduler(private val failOnPost: Boolean = false) : NetworkMonitorScheduler {
        private val tasks = ArrayDeque<Runnable>()
        var closed = false
            private set

        @Synchronized
        override fun post(task: Runnable) {
            if (failOnPost) error("initial scheduling failed")
            if (!closed) tasks.addLast(task)
        }

        @Synchronized
        override fun postDelayed(task: Runnable, delayMillis: Long) {
            if (!closed) tasks.addLast(task)
        }

        @Synchronized
        override fun cancelAll() {
            tasks.clear()
        }

        @Synchronized
        override fun close() {
            closed = true
            tasks.clear()
        }

        fun runNext() {
            val task = synchronized(this) { tasks.removeFirst() }
            task.run()
        }

        @Synchronized
        fun hasTasks(): Boolean = tasks.isNotEmpty()
    }
}
