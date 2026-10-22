package com.leovp.androidbase.framework

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.viewbinding.ViewBinding
import com.leovp.androidbase.utils.network.InternetUtil
import com.leovp.androidbase.utils.network.NetworkMonitor
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the publication handshake between the DNS lookup thread and the main thread in
 * [BaseActivity.startTrafficNetwork] and [BaseActivity.stopTrafficMonitor].
 *
 * The lookup callback is captured instead of being run, so the interleavings these tests care
 * about are driven explicitly rather than raced for: `InternetUtil.getIpsByHost` normally hands
 * its callback to a bare thread, which would make the ordering unobservable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BaseActivityTrafficMonitorTest {
    private val lookupCallback = slot<(List<String>) -> Unit>()

    @Before
    fun setUp() {
        mockkObject(InternetUtil)
        every { InternetUtil.getIpsByHost(any(), capture(lookupCallback)) } just Runs
        mockkConstructor(NetworkMonitor::class)
        every { anyConstructed<NetworkMonitor>().startMonitor(any()) } just Runs
        every { anyConstructed<NetworkMonitor>().stopMonitor() } just Runs
    }

    @After
    fun tearDown() {
        unmockkConstructor(NetworkMonitor::class)
        unmockkObject(InternetUtil)
    }

    @Test
    fun `a lookup that returns while the Activity is alive publishes and starts the monitor`() {
        val activity = Robolectric.buildActivity(TrafficMonitorTestActivity::class.java).get()

        activity.startTrafficNetwork(DOMAIN)
        lookupCallback.captured.invoke(listOf(SOCKET_IP))

        // The control case for the teardown tests below: without it they would all pass against
        // an implementation that never publishes anything at all.
        verify(exactly = 1) { anyConstructed<NetworkMonitor>().startMonitor(DEFAULT_FREQUENCY) }
        verify(exactly = 0) { anyConstructed<NetworkMonitor>().stopMonitor() }
    }

    @Test
    fun `a lookup that returns after an explicit stop is torn down instead of published`() {
        val activity = Robolectric.buildActivity(TrafficMonitorTestActivity::class.java).get()

        activity.startTrafficNetwork(DOMAIN)
        // Reaches an empty slot and so stops nothing, which is exactly the case that used to let
        // the late lookup publish a monitor nobody owned any more.
        activity.stopTrafficMonitor()
        lookupCallback.captured.invoke(listOf(SOCKET_IP))

        verify(exactly = 0) { anyConstructed<NetworkMonitor>().startMonitor(any()) }
        verify(exactly = 1) { anyConstructed<NetworkMonitor>().stopMonitor() }
    }

    @Test
    fun `a start that throws releases the slot so a later start is accepted`() {
        every { anyConstructed<NetworkMonitor>().startMonitor(any()) } throws
            IllegalStateException("traffic stat unavailable")
        val activity = Robolectric.buildActivity(TrafficMonitorTestActivity::class.java).get()

        activity.startTrafficNetwork(DOMAIN)
        lookupCallback.captured.invoke(listOf(SOCKET_IP))

        // A failed start must not escape into the bare lookup thread, and must not leave the slot
        // pointing at a dead monitor: a second call has to reach the lookup again rather than be
        // refused by the "already existed" guard.
        verify(exactly = 1) { anyConstructed<NetworkMonitor>().stopMonitor() }
        activity.startTrafficNetwork(DOMAIN)
        verify(exactly = 2) { InternetUtil.getIpsByHost(DOMAIN, any()) }
    }

    @Test
    fun `a lookup that returns after onDestroy never creates a monitor`() {
        val controller = Robolectric.buildActivity(TrafficMonitorTestActivity::class.java).setup()

        controller.get().startTrafficNetwork(DOMAIN)
        controller.pause().stop().destroy()
        lookupCallback.captured.invoke(listOf(SOCKET_IP))

        verify(exactly = 0) { anyConstructed<NetworkMonitor>().startMonitor(any()) }
    }

    @Test
    fun `onDestroy stops a monitor that was already published`() {
        val controller = Robolectric.buildActivity(TrafficMonitorTestActivity::class.java).setup()

        controller.get().startTrafficNetwork(DOMAIN)
        lookupCallback.captured.invoke(listOf(SOCKET_IP))
        verify(exactly = 1) { anyConstructed<NetworkMonitor>().startMonitor(DEFAULT_FREQUENCY) }

        controller.pause().stop().destroy()

        verify(exactly = 1) { anyConstructed<NetworkMonitor>().stopMonitor() }
    }

    @Test
    fun `stop waits for an in-progress monitor start before returning`() {
        val startEntered = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        every { anyConstructed<NetworkMonitor>().startMonitor(any()) } answers {
            startEntered.countDown()
            assertTrue(releaseStart.await(5, TimeUnit.SECONDS))
        }
        val activity = Robolectric.buildActivity(TrafficMonitorTestActivity::class.java).get()
        activity.startTrafficNetwork(DOMAIN)
        val lookup = Thread { lookupCallback.captured.invoke(listOf(SOCKET_IP)) }
        val stopper = Thread {
            activity.stopTrafficMonitor()
            stopReturned.countDown()
        }

        try {
            lookup.start()
            assertTrue(startEntered.await(3, TimeUnit.SECONDS))
            stopper.start()

            // stopTrafficMonitor() is a teardown fence: it must not return while the published
            // monitor is still inside startMonitor(), or that start can create resources later.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while ((stopper.state == Thread.State.NEW || stopper.state == Thread.State.RUNNABLE) &&
                System.nanoTime() < deadline
            ) {
                Thread.sleep(1)
            }
            assertFalse(stopper.state == Thread.State.NEW)
            assertTrue(stopper.isAlive)
            assertFalse(stopReturned.await(0, TimeUnit.MILLISECONDS))
        } finally {
            releaseStart.countDown()
            lookup.join(3_000)
            stopper.join(3_000)
        }

        assertFalse(lookup.isAlive)
        assertFalse(stopper.isAlive)
        assertTrue(stopReturned.await(0, TimeUnit.MILLISECONDS))
        verify(exactly = 1) { anyConstructed<NetworkMonitor>().stopMonitor() }
    }

    private companion object {
        const val DOMAIN = "leovp.com"
        const val SOCKET_IP = "127.0.0.1"

        /** Mirrors the `frequencyInSecond` default of `BaseActivity.TrafficConfig`. */
        const val DEFAULT_FREQUENCY = 3
    }
}

/**
 * `BaseActivity` drives an AppCompat delegate, so the theme has to be in place before
 * `super.onCreate()` runs. [onCreateBeginning] is the hook the base class calls first.
 */
internal class TrafficMonitorTestActivity : BaseActivity<ViewBinding>() {
    private val rootView: View by lazy { FrameLayout(this) }

    override fun getTagName(): String = "TrafficMonitorTestActivity"

    override fun onCreateBeginning() {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ViewBinding = object : ViewBinding {
        override fun getRoot(): View = rootView
    }
}
