package com.leovp.demo

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inspector.WindowInspector
import androidx.test.platform.app.InstrumentationRegistry
import com.leovp.demo.basiccomponents.examples.FloatViewActivity
import com.leovp.floatview.FloatView
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Exercises real WindowManager registration and Activity teardown on a connected device. */
class FloatViewWindowAndroidTest {
    @Test
    fun removeBeforeFirstAttach() {
        DeviceActivity().use { scenario ->
            scenario.onActivity { activity ->
                FloatView.removeAll(true)
                repeat(20) {
                    val view = View(activity)
                    FloatView.with(activity).layout(view).show()
                    assertTrue(FloatView.default().exist())
                    assertNull(view.windowToken)
                    FloatView.default().remove(true)
                    assertRemoved(view)
                }
            }
        }
    }

    @Test
    fun destructionTakesOverAnimatedRemoval() {
        val scenario = DeviceActivity()
        lateinit var view: View
        try {
            scenario.onActivity { activity ->
                FloatView.removeAll(true)
                view = View(activity)
                FloatView.with(activity).layout(view).meta { _, _ ->
                    enableAlphaAnimation = true
                }.show()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertTrue(view.isAttachedToWindow)
                FloatView.default().remove()
                assertTrue(FloatView.default().exist())
            }
        } finally {
            scenario.close()
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { assertRemoved(view) }
    }

    @Test
    fun repeatedPageExitReleasesAllFloatWindows() {
        repeat(5) {
            val views = mutableListOf<View>()
            DeviceActivity().use { scenario ->
                scenario.onActivity {
                    FloatView.allFloatViewTags().forEach { tag ->
                        FloatView.with(tag).customView?.let(views::add)
                    }
                    assertTrue(views.isNotEmpty())
                }
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(FloatView.allFloatViewTags().isEmpty())
                views.forEach { assertRemoved(it) }
            }
        }
    }

    private fun assertRemoved(view: View) {
        assertFalse(FloatView.default().exist())
        assertNull(view.parent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertFalse(WindowInspector.getGlobalWindowViews().contains(view))
        }
    }

    // Use platform Instrumentation directly: this demo's androidTest dependencies also contain
    // Robolectric, whose ActivityScenario service provider cannot run on a physical device.
    private class DeviceActivity :
        Closeable,
        Application.ActivityLifecycleCallbacks {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val application = instrumentation.targetContext.applicationContext as Application
        private val resumed = CountDownLatch(1)
        private val destroyed = CountDownLatch(1)
        private lateinit var activity: Activity

        init {
            instrumentation.runOnMainSync {
                application.registerActivityLifecycleCallbacks(this)
                application.startActivity(
                    Intent(application, FloatViewActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            if (!resumed.await(10, TimeUnit.SECONDS)) {
                instrumentation.runOnMainSync {
                    application.unregisterActivityLifecycleCallbacks(this)
                }
                error("Activity did not resume")
            }
        }

        fun onActivity(action: (Activity) -> Unit) {
            instrumentation.runOnMainSync { action(activity) }
        }

        override fun close() {
            try {
                onActivity { it.finish() }
                assertTrue(destroyed.await(10, TimeUnit.SECONDS), "Activity did not finish")
                instrumentation.waitForIdleSync()
            } finally {
                instrumentation.runOnMainSync {
                    application.unregisterActivityLifecycleCallbacks(this)
                }
            }
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity === this.activity) destroyed.countDown()
        }

        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            if (activity is FloatViewActivity) this.activity = activity
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            if (::activity.isInitialized && activity === this.activity) resumed.countDown()
        }
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
