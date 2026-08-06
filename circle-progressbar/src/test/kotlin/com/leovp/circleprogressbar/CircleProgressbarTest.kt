package com.leovp.circleprogressbar

import android.animation.ValueAnimator
import android.content.Context
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun CircleProgressbar.indeterminateAnimator(): ValueAnimator {
        val animatorField = CircleProgressbar::class.java
            .getDeclaredField("internalIndeterminateAnimator")
            .apply { isAccessible = true }
        return animatorField.get(this) as ValueAnimator
    }
}
