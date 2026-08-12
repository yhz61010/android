package com.leovp.circleprogressbar

import android.animation.ValueAnimator
import android.content.Context
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import com.leovp.circleprogressbar.base.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CircleProgressbarTest {
    @Test
    fun progressValuesAreClampedAndExposedForAccessibility() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val progressbar = CircleProgressbar(context)

        progressbar.maxProgress = 0
        progressbar.setDeterminate()
        progressbar.currentProgress = -1

        assertEquals(1, progressbar.maxProgress)
        assertEquals(0, progressbar.currentProgress)
        assertEquals("Progress 0 of 1", ViewCompat.getStateDescription(progressbar))

        progressbar.currentProgress = 10

        assertEquals(1, progressbar.currentProgress)
        assertEquals("Progress 1 of 1", ViewCompat.getStateDescription(progressbar))
    }

    @Test
    fun changingAnimationDurationUpdatesTheAnimator() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val progressbar = CircleProgressbar(context)

        progressbar.progressAnimDuration = 250

        val animator = progressbar.indeterminateAnimator()
        assertEquals(250L, animator.duration)
    }

    @Test
    fun setIndeterminateDoesNotStartAnimatorWhileDetached() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val progressbar = CircleProgressbar(context)

        progressbar.setIndeterminate()

        val animator = progressbar.indeterminateAnimator()
        assertFalse(animator.isStarted)
    }

    @Test
    fun stateListenersCanMutateTheListenerListDuringCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val progressbar = CircleProgressbar(context)
        val events = mutableListOf<String>()
        lateinit var first: CircleProgressbar.OnStateChangedListener
        val addedDuringCallback = object : CircleProgressbar.OnStateChangedListener {
            override fun onStateChanged(newState: State.Type) {
                events += "added:${newState.name}"
            }
        }
        first = object : CircleProgressbar.OnStateChangedListener {
            override fun onStateChanged(newState: State.Type) {
                events += "first:${newState.name}"
                progressbar.removeOnStateChangedListener(first)
                progressbar.addOnStateChangedListeners(addedDuringCallback)
            }
        }
        val second = object : CircleProgressbar.OnStateChangedListener {
            override fun onStateChanged(newState: State.Type) {
                events += "second:${newState.name}"
            }
        }
        assertTrue(progressbar.addOnStateChangedListeners(first))
        assertTrue(progressbar.addOnStateChangedListeners(second))

        progressbar.setDeterminate()

        assertEquals(
            listOf("first:STATE_DETERMINATE", "second:STATE_DETERMINATE"),
            events
        )

        events.clear()
        progressbar.setFinish()

        assertEquals(
            listOf("second:STATE_FINISHED", "added:STATE_FINISHED"),
            events
        )
    }

    @Test
    fun clickListenersCanRemoveThemselvesDuringCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val progressbar = CircleProgressbar(context)
        val events = mutableListOf<String>()
        lateinit var first: CircleProgressbar.OnClickListener
        first = clickListener {
            events += "first"
            progressbar.removeOnClickListener(first)
        }
        val second = clickListener { events += "second" }
        assertTrue(progressbar.addOnClickListener(first))
        assertTrue(progressbar.addOnClickListener(second))

        progressbar.performClick()
        progressbar.performClick()

        assertEquals(listOf("first", "second", "second"), events)
    }

    private fun CircleProgressbar.indeterminateAnimator(): ValueAnimator {
        val animatorField = CircleProgressbar::class.java
            .getDeclaredField("internalIndeterminateAnimator")
            .apply { isAccessible = true }
        return animatorField.get(this) as ValueAnimator
    }

    private fun clickListener(onIdle: () -> Unit) = object : CircleProgressbar.OnClickListener {
        override fun onIdleButtonClick(view: android.view.View) = onIdle()

        override fun onCancelButtonClick(view: android.view.View) = Unit

        override fun onFinishButtonClick(view: android.view.View) = Unit

        override fun onErrorButtonClick(view: android.view.View) = Unit
    }
}
