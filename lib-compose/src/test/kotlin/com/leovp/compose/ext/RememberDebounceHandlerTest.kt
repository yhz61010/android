package com.leovp.compose.ext

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RememberDebounceHandlerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `changing debounce time creates a handler with the new configuration`() {
        val debounceTime = mutableStateOf(100L)
        val observed = mutableListOf<DebounceHandler>()

        composeRule.setContent { observed += rememberDebounceHandler(debounceTime.value) }
        composeRule.runOnIdle { debounceTime.value = 200L }
        composeRule.runOnIdle { }

        assertNotSame(observed.first(), observed.last())
    }
}
