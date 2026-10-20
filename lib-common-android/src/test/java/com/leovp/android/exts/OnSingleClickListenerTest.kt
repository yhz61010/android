package com.leovp.android.exts

import android.view.View
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnSingleClickListenerTest {
    @Test
    fun `single click listener uses its monotonic time source`() {
        var now = 0L
        var calls = 0
        val listener = OnSingleClickListener(
            interval = 500L,
            action = { calls++ },
            timeSource = { now }
        )
        val view = mockk<View>(relaxed = true)

        listener.doClick(view)
        now += 499L
        listener.doClick(view)
        now += 1L
        listener.doClick(view)

        assertEquals(2, calls)
    }
}
