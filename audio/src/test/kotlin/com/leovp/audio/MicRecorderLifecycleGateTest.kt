package com.leovp.audio

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MicRecorderLifecycleGateTest {
    @Test
    fun `start is one-shot and stop has one leader`() {
        val gate = MicRecorderLifecycleGate()

        gate.claimStart()

        assertFailsWith<IllegalStateException> { gate.claimStart() }
        assertTrue(gate.claimStop())
        assertFalse(gate.claimStop())
        assertFailsWith<IllegalStateException> { gate.claimStart() }
    }

    @Test
    fun `stop before start rejects later start`() {
        val gate = MicRecorderLifecycleGate()

        assertTrue(gate.claimStop())

        assertFailsWith<IllegalStateException> { gate.claimStart() }
    }
}
