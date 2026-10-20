package com.leovp.compose.viewmodel.lifecycle

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import com.leovp.mvvm.viewmodel.lifecycle.LifecycleAware
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LifecycleViewModelBridgeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `replacing view model replaces the lifecycle observer`() {
        val owner = TestLifecycleOwner()
        val first = TestViewModel()
        val second = TestViewModel()
        val current = mutableStateOf(first)

        composeRule.setContent { LifecycleViewModelBridge(current.value, owner) }
        composeRule.runOnIdle { current.value = second }
        composeRule.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }

        assertEquals(0, first.resumeCount)
        assertEquals(1, second.resumeCount)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }

    private class TestViewModel :
        ViewModel(),
        LifecycleAware {
        var resumeCount = 0
        override fun onResume() {
            resumeCount++
        }
    }
}
