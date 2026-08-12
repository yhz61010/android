package com.leovp.camerax.fragments

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OneShotGateTest {
    @Test
    fun `gate allows one navigation and can be reset after view recreation`() {
        val gate = OneShotGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())

        gate.reset()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
    }
}
