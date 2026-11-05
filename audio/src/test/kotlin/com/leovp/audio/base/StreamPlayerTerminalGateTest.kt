package com.leovp.audio.base

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamPlayerTerminalGateTest {
    @Test
    fun `failure ownership excludes explicit stop`() {
        val gate = StreamPlayerTerminalGate()

        assertTrue(gate.claimFailure())
        assertFalse(gate.claimFailure())
        assertFalse(gate.claimExplicitStop())
    }

    @Test
    fun `explicit stop ownership excludes failure`() {
        val gate = StreamPlayerTerminalGate()

        assertTrue(gate.claimExplicitStop())
        assertFalse(gate.claimExplicitStop())
        assertFalse(gate.claimFailure())
    }
}
