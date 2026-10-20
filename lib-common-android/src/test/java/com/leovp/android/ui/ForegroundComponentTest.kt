package com.leovp.android.ui

import android.app.Application
import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ForegroundComponentTest {
    @Before
    fun setUp() = resetSingleton()

    @After
    fun tearDown() = resetSingleton()

    @Test
    fun `background listener runs on the main looper`() {
        val component = ForegroundComponent()
        var callbackLooper: Looper? = null
        component.addListener(object : ForegroundComponent.AppStateListener {
            override fun onBecameForeground() = Unit
            override fun onBecameBackground() {
                callbackLooper = Looper.myLooper()
            }
        })

        val activity = mockk<android.app.Activity>(relaxed = true)
        component.onActivityResumed(activity)
        component.onActivityPaused(activity)
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper())
            .idleFor(Duration.ofMillis(500))

        assertSame(Looper.getMainLooper(), callbackLooper)
    }

    @Test
    fun `singleton is published only after lifecycle registration`() {
        var publishedDuringRegistration = false
        val application = mockk<Application>(relaxed = true)
        every { application.registerActivityLifecycleCallbacks(any()) } answers {
            publishedDuringRegistration = runCatching { ForegroundComponent.get() }.isSuccess
        }

        ForegroundComponent.init(application)

        assertFalse(publishedDuringRegistration)
    }

    private fun resetSingleton() {
        ForegroundComponent::class.java.getDeclaredField("instance")
            .apply { isAccessible = true }
            .set(null, null)
    }
}
