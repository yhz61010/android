package com.leovp.floatview.framework

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Point
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.leovp.floatview.FloatView
import com.leovp.floatview.FloatViewShowResult
import com.leovp.floatview.entities.DefaultConfig
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class FloatViewLifecycleTest {
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private val windows = mutableSetOf<View>()
    private val windowManager = mockk<WindowManager>(relaxed = true)
    private lateinit var context: Context
    private lateinit var view: View
    private var addedLayoutParams: WindowManager.LayoutParams? = null

    @Before
    fun setUp() {
        controller.setup()
        val activity = controller.get()
        context = object : ContextWrapper(activity) {
            override fun getSystemService(name: String): Any? =
                if (name == WINDOW_SERVICE) windowManager else super.getSystemService(name)
        }
        view = View(context)
        // Model registration before the first traversal attaches the view.
        every { windowManager.addView(any(), any()) } answers {
            addedLayoutParams = secondArg()
            check(windows.add(firstArg()))
        }
        every { windowManager.removeViewImmediate(any()) } answers {
            check(windows.remove(firstArg()))
        }
    }

    @After
    fun tearDown() {
        FloatView.removeAll(true)
        if (!controller.get().isDestroyed) controller.pause().stop().destroy()
    }

    @Test
    fun `remove before first attach unregisters the window`() {
        create()
        assertNull(view.windowToken)
        FloatView.default().remove(true)
        assertTrue(windows.isEmpty())
        assertFalse(FloatView.default().exist())
    }

    @Test
    fun `activity destruction removes windows without caller cleanup`() {
        create()
        controller.pause().stop().destroy()
        assertTrue(windows.isEmpty())
        assertFalse(FloatView.default().exist())
    }

    @Test
    fun `destroy takes over pending animated removal`() {
        create(animated = true)
        FloatView.default().remove()
        assertTrue(FloatView.default().exist())
        controller.pause().stop().destroy()
        assertTrue(windows.isEmpty())
        assertFalse(FloatView.default().exist())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(windows.isEmpty())
    }

    @Test
    fun `show failure after registration rolls back the window`() {
        val failingView = object : View(context) {
            override fun setVisibility(visibility: Int) {
                if (visibility == VISIBLE) error("show failure")
                super.setVisibility(visibility)
            }
        }
        FloatView.with(context).layout(failingView).show()
        assertTrue(windows.isEmpty())
        assertFalse(FloatView.default().exist())
    }

    @Test
    fun `repeated show replaces registration before first attach`() {
        create()
        FloatView.default().show()
        assertTrue(windows.size == 1)
        FloatView.default().remove(true)
        assertTrue(windows.isEmpty())
    }

    @Test
    fun `failed removal retains ownership for immediate retry`() {
        create()
        every { windowManager.removeViewImmediate(view) } throws
            IllegalStateException("transient removal failure")
        FloatView.default().remove(true)
        assertTrue(windows.contains(view))
        assertTrue(FloatView.default().exist())
        every { windowManager.removeViewImmediate(view) } answers {
            check(windows.remove(view))
        }
        FloatView.default().remove(true)
        assertTrue(windows.isEmpty())
        assertFalse(FloatView.default().exist())
    }

    @Test
    fun `tryShow preserves attached state when the previous window cannot be removed`() {
        create()
        every { windowManager.removeViewImmediate(view) } throws
            IllegalStateException("transient removal failure")
        FloatView.default().remove(true)

        try {
            val result = FloatView.with(context).layout(View(context)).tryShow()

            assertEquals(FloatViewShowResult.FAILED_BUT_ATTACHED_HIDDEN, result)
            assertTrue(FloatView.default().exist())
            assertTrue(windows.contains(view))
            assertEquals(View.GONE, view.visibility)
        } finally {
            every { windowManager.removeViewImmediate(view) } answers {
                check(windows.remove(view))
            }
            FloatView.default().remove(true)
        }
    }

    @Test
    fun `immediate takeover cancels callbacks before reusing the same view`() {
        create(animated = true)
        FloatView.default().remove()
        FloatView.default().remove(true)
        create()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(windows.contains(view))
        assertTrue(view.visibility == View.VISIBLE)
    }

    @Test
    fun `destroy removes a built but never shown instance`() {
        FloatView.with(context).layout(view).build()
        controller.pause().stop().destroy()
        assertFalse(FloatView.default().exist())
        assertTrue(windows.isEmpty())
    }

    @Test
    fun `destroy also removes Activity owned system overlays`() {
        FloatView.with(context).layout(view).meta { _, _ -> systemWindow = true }.show()
        assertTrue(windows.contains(view))
        controller.pause().stop().destroy()
        assertTrue(windows.isEmpty())
        assertFalse(FloatView.default().exist())
    }

    @Test
    fun `build on a finishing activity is skipped without throwing`() {
        controller.get().finish()
        FloatView.with(context).layout(view).build()
        assertFalse(FloatView.default().exist())
        assertTrue(windows.isEmpty())
    }

    @Test
    fun `finishing activity tryShow does not reuse an existing tag`() {
        create()
        controller.get().finish()

        val result = FloatView.with(context).layout(View(context)).tryShow()

        assertEquals(FloatViewShowResult.FAILED_AND_DETACHED, result)
        assertTrue(FloatView.default().exist())
        assertTrue(windows.contains(view))
    }

    @Test
    fun `destroy retries a failed detach and releases ownership after success`() {
        create()
        var attempts = 0
        every { windowManager.removeViewImmediate(view) } answers {
            attempts++
            if (attempts == 1) error("transient removal failure")
            check(windows.remove(view))
        }

        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))

        assertEquals(2, attempts)
        assertFalse(FloatView.default().exist())
    }

    @Test
    fun `non immediate remove does not cancel a destroyed owner retry`() {
        create()
        var attempts = 0
        every { windowManager.removeViewImmediate(view) } answers {
            attempts++
            if (attempts == 1) error("transient removal failure")
            check(windows.remove(view))
        }

        controller.pause().stop().destroy()
        FloatView.default().remove(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))

        assertEquals(2, attempts)
        assertFalse(FloatView.default().exist())
    }

    @Test
    fun `immediate remove replaces a destroyed owner retry without losing future retries`() {
        create()
        var attempts = 0
        every { windowManager.removeViewImmediate(view) } answers {
            attempts++
            if (attempts <= 2) error("transient removal failure")
            check(windows.remove(view))
        }

        controller.pause().stop().destroy()
        FloatView.default().remove(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))

        assertEquals(3, attempts)
        assertFalse(FloatView.default().exist())
    }

    @Test
    fun `destroy bounds repeated detach failures and releases library ownership`() {
        create()
        var attempts = 0
        every { windowManager.removeViewImmediate(view) } answers {
            attempts++
            error("persistent removal failure")
        }

        try {
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

            assertEquals(4, attempts)
            assertFalse(FloatView.default().exist())
        } finally {
            every { windowManager.removeViewImmediate(view) } answers { windows.remove(view) }
            FloatView.default().remove(true)
        }
    }

    @Test
    fun `attributes set after build are retained until first show`() {
        FloatView.with(context).layout(view).build()

        FloatView.default().gravity = Gravity.TOP or Gravity.START
        FloatView.default().position = Point(120, 160)

        assertEquals(Gravity.TOP or Gravity.START, FloatView.default().gravity)
        assertEquals(Point(120, 160), FloatView.default().position)

        FloatView.default().show()

        assertEquals(Gravity.TOP or Gravity.START, addedLayoutParams?.gravity)
    }

    @Test
    fun `tryShow reports a failed window attachment`() {
        every { windowManager.addView(any(), any()) } throws
            IllegalStateException("window rejected")

        val result = FloatView.with(context).layout(view).tryShow()

        assertEquals(FloatViewShowResult.FAILED_AND_DETACHED, result)
        assertFalse(FloatView.default().exist())
    }

    @Test
    fun `show failure keeps a hidden registration when window detachment fails`() {
        val failingView = object : View(context) {
            override fun setVisibility(visibility: Int) {
                if (visibility == VISIBLE) error("show failure")
                super.setVisibility(visibility)
            }
        }
        every { windowManager.removeViewImmediate(failingView) } throws
            IllegalStateException("transient removal failure")

        val result = FloatView.with(context).layout(failingView).tryShow()

        assertEquals(FloatViewShowResult.FAILED_BUT_ATTACHED_HIDDEN, result)
        assertTrue(FloatView.default().exist())
        assertEquals(View.GONE, failingView.visibility)

        every { windowManager.removeViewImmediate(failingView) } answers {
            check(windows.remove(failingView))
        }
        FloatView.default().remove(true)
    }

    @Test
    fun `repeated show hides the old window when detachment fails`() {
        create()
        every { windowManager.removeViewImmediate(view) } throws
            IllegalStateException("transient removal failure")

        val result = FloatViewManager.show(DefaultConfig.DEFAULT_FLOAT_VIEW_TAG)

        assertEquals(FloatViewShowResult.FAILED_BUT_ATTACHED_HIDDEN, result)
        assertTrue(FloatView.default().exist())
        assertEquals(View.GONE, view.visibility)

        every { windowManager.removeViewImmediate(view) } answers {
            check(windows.remove(view))
        }
        FloatView.default().remove(true)
    }

    @Test
    fun `tryShow does not convert fatal errors into ordinary failures`() {
        every { windowManager.addView(any(), any()) } throws AssertionError("fatal")

        val error = runCatching {
            FloatView.with(context).layout(view).tryShow()
        }.exceptionOrNull()

        assertTrue(error is AssertionError)
    }

    @Test
    fun `tryShow reports a successful window attachment`() {
        val result = FloatView.with(context).layout(view).tryShow()

        assertEquals(FloatViewShowResult.SHOWN, result)
        assertTrue(FloatView.default().exist())
    }

    @Test
    fun `duplicate tags are rejected as state errors`() {
        FloatView.with(context).layout(view).build()

        val error = runCatching {
            FloatView.with(context).layout(View(context)).build()
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `duplicate tryShow does not hide the existing healthy window`() {
        create()

        val result = FloatView.with(context).layout(View(context)).tryShow()

        assertEquals(FloatViewShowResult.FAILED_AND_DETACHED, result)
        assertTrue(FloatView.default().exist())
        assertTrue(windows.contains(view))
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `duplicate tryShow does not report an animated removal as failed`() {
        create(animated = true)
        FloatView.default().remove()

        val result = FloatView.with(context).layout(View(context)).tryShow()

        assertEquals(FloatViewShowResult.FAILED_AND_DETACHED, result)
        assertTrue(FloatView.default().exist())
        assertTrue(windows.contains(view))
        assertEquals(View.VISIBLE, view.visibility)
        FloatView.default().remove(true)
    }

    @Test
    fun `window already removed by the framework is treated as detached`() {
        create()
        // Simulate Activity teardown having removed the window behind our back.
        windows.remove(view)
        every { windowManager.removeViewImmediate(view) } throws
            IllegalArgumentException("View not attached to window manager")
        FloatView.default().remove(true)
        assertFalse(FloatView.default().exist())
    }

    private fun create(animated: Boolean = false) {
        FloatView.with(context).layout(view).meta { _, _ ->
            enableAlphaAnimation = animated
        }.show()
        assertTrue(windows.contains(view))
    }
}
