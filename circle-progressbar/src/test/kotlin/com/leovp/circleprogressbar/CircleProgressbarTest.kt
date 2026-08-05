package com.leovp.circleprogressbar

import android.animation.ValueAnimator
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CircleProgressbarTest {
    @Test
    fun setIndeterminateDoesNotStartAnimatorWhileDetached() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val progressbar = CircleProgressbar(context)

        progressbar.setIndeterminate()

        val animatorField = CircleProgressbar::class.java
            .getDeclaredField("internalIndeterminateAnimator")
            .apply { isAccessible = true }
        val animator = animatorField.get(progressbar) as ValueAnimator
        assertFalse(animator.isStarted)
    }
}
