package com.leovp.audio.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.CancellationException

class RunCatchingExtTest {
    @Test
    fun `cancellation is rethrown`() {
        assertFailsWith<CancellationException> {
            runCatchingPreservingCancellation<Unit> {
                throw CancellationException("cancelled")
            }
        }
    }

    @Test
    fun `ordinary failures stay in result`() {
        val result = runCatchingPreservingCancellation<Unit> { error("failed") }

        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `successful values are preserved`() {
        assertEquals(42, runCatchingPreservingCancellation { 42 }.getOrThrow())
    }
}
