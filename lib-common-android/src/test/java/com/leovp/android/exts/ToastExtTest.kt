package com.leovp.android.exts

import android.app.Activity
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.leovp.floatview.FloatView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.concurrent.thread
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ToastExtTest {
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        controller.setup().visible()
        activity = controller.get()
        LeoToast.getInstance(activity).init()
    }

    @After
    fun tearDown() {
        FloatView.removeAll(true)
        if (!activity.isDestroyed) controller.pause().stop().destroy()
    }

    @Test
    fun `Android R and above falls back to system toast when float view cannot attach`() {
        val windowManager = createWindowManager()
        every { windowManager.addView(any(), any()) } throws
            IllegalStateException("window rejected")
        val context = createVisualContext(windowManager)
        ShadowSettings.setCanDrawOverlays(true)

        shadowOf(activity.mainLooper).runPaused { context.toast("fallback message") }

        assertTrue(ShadowToast.getTextOfLatestToast() == "fallback message")
        verify(exactly = 1) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `fatal float view errors are not downgraded to a system toast`() {
        val windowManager = createWindowManager()
        every { windowManager.addView(any(), any()) } throws AssertionError("fatal")
        val context = createVisualContext(windowManager)
        ShadowSettings.setCanDrawOverlays(true)

        val error = runCatching {
            shadowOf(activity.mainLooper).runPaused { context.toast("fatal message") }
        }.exceptionOrNull()

        assertTrue(error is AssertionError)
    }

    @Test
    fun `cancelToast marshals float view removal to the main thread`() {
        val tag = "leo-enhanced-custom-toast"
        val context = createVisualContext(createWindowManager())
        FloatView.with(context).layout(View(context)).meta { _, _ -> this.tag = tag }.show()
        assertTrue(FloatView.with(tag).exist())

        thread { cancelToast() }.join()
        shadowOf(activity.mainLooper).idle()

        assertFalse(FloatView.with(tag).exist())
    }

    @Test
    fun `concurrent toast requests do not remove each other from the main queue`() {
        val windowManager = createWindowManager()
        val context = createVisualContext(windowManager)
        ShadowSettings.setCanDrawOverlays(true)

        val first = thread { context.toast("first") }
        val second = thread { context.toast("second") }
        first.join()
        second.join()
        shadowOf(activity.mainLooper).idleFor(Duration.ZERO)

        verify(exactly = 2) { windowManager.addView(any(), any()) }
    }

    private fun createWindowManager(): WindowManager {
        val realWindowManager = activity.getSystemService(Activity.WINDOW_SERVICE) as WindowManager
        return mockk<WindowManager>(relaxed = true) {
            every { currentWindowMetrics } returns realWindowManager.currentWindowMetrics
        }
    }

    private fun createVisualContext(windowManager: WindowManager) =
        object : ContextWrapper(activity) {
            override fun getSystemService(name: String): Any? =
                if (name == WINDOW_SERVICE) windowManager else super.getSystemService(name)

            override fun getDisplay(): Display =
                (activity.getSystemService(DISPLAY_SERVICE) as DisplayManager)
                    .getDisplay(Display.DEFAULT_DISPLAY)
        }
}
