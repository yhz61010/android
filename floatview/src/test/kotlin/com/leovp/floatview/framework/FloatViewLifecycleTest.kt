package com.leovp.floatview.framework

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.leovp.floatview.FloatView
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
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

    private fun create(animated: Boolean = false) {
        FloatView.with(context).layout(view).meta { _, _ ->
            enableAlphaAnimation = animated
        }.show()
        assertTrue(windows.contains(view))
    }
}
