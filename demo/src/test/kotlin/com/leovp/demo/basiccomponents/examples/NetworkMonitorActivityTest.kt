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
