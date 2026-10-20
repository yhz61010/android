package com.leovp.floatview.framework

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import com.leovp.floatview.FloatView
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25])
class FloatViewLegacyWindowTypeTest {
    private val controller = Robolectric.buildActivity(Activity::class.java)
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
        ShadowSettings.setCanDrawOverlays(true)
        every { windowManager.addView(any(), any()) } answers {
            addedLayoutParams = secondArg()
        }
    }

    @After
    fun tearDown() {
        FloatView.removeAll(true)
        shadowOf(controller.get().mainLooper).idle()
        if (!controller.get().isDestroyed) controller.pause().stop().destroy()
    }

    @Test
    fun `pre Android O overlay uses one valid legacy window type`() {
        FloatView.with(context)
            .layout(view)
            .meta { _, _ -> systemWindow = true }
            .show()

        @Suppress("DEPRECATION")
        assertEquals(WindowManager.LayoutParams.TYPE_PHONE, addedLayoutParams?.type)
    }
}
