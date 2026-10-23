package com.leovp.demo.basiccomponents.examples

import android.content.Context
import android.os.Looper
import com.leovp.android.exts.toast
import com.leovp.androidbase.utils.network.InternetUtil
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkMonitorActivityTest {
    private val lookupCallback = slot<(List<String>) -> Unit>()

    @Before
    fun setUp() {
        mockkObject(InternetUtil)
        every { InternetUtil.getIpsByHost(any(), capture(lookupCallback)) } just Runs
        mockkStatic("com.leovp.android.exts.ToastExtKt")
        every {
            any<Context>().toast(
                msg = any<String>(),
                longDuration = any(),
                origin = any(),
                debug = any(),
                textColor = any(),
                bgColor = any(),
                error = any()
            )
        } just Runs
    }

    @After
    fun tearDown() {
        unmockkStatic("com.leovp.android.exts.ToastExtKt")
        unmockkObject(InternetUtil)
        stopKoin()
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    fun `a failed lookup queued before onDestroy does not show a late toast`() {
        val controller = Robolectric.buildActivity(NetworkMonitorActivity::class.java).setup()
        val lookup = Thread { lookupCallback.captured.invoke(emptyList()) }
        lookup.start()
        lookup.join(3_000)
        assertFalse(lookup.isAlive)

        // The interleaving this case is about only exists while the posted runnable is still
        // queued. Assert that rather than trust it: if a Robolectric upgrade ever idles the main
        // looper during a lifecycle transition, the toast would fire before onDestroy and this
        // case would report a harness change as a production regression. It proves only that
        // something is queued, and against the code that toasted inline from the lookup thread
        // this is what goes red first -- "looper was idle" then means "the toast already fired".
        assertFalse(shadowOf(Looper.getMainLooper()).isIdle)
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) {
            any<Context>().toast(
                msg = any<String>(),
                longDuration = any(),
                origin = any(),
                debug = any(),
                textColor = any(),
                bgColor = any(),
                error = any()
            )
        }
    }

    /**
     * The reverse control for the case above: without it a `mockkStatic` that failed to intercept
     * would make that `verify(exactly = 0)` vacuously green.
     */
    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    fun `a failed lookup while the Activity is alive shows a toast`() {
        val controller = Robolectric.buildActivity(NetworkMonitorActivity::class.java).setup()
        val lookup = Thread { lookupCallback.captured.invoke(emptyList()) }
        lookup.start()
        lookup.join(3_000)
        assertFalse(lookup.isAlive)

        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) {
            any<Context>().toast(
                msg = "Can't get IP from host.",
                longDuration = any(),
                origin = any(),
                debug = any(),
                textColor = any(),
                bgColor = any(),
                error = any()
            )
        }

        controller.pause().stop().destroy()
    }
}
