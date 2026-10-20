package com.leovp.compose.ext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DebounceExtTest {
    @Test
    fun `published no argument constructor remains available to JVM callers`() {
        val handler = DebounceHandler::class.java.getConstructor().newInstance()

        assertEquals(DebounceHandler::class.java, handler.javaClass)
    }

    @Test
    fun `first debounce action is accepted when monotonic clock starts at zero`() {
        var calls = 0
        val handler = DebounceHandler(defaultDebounceTime = 500L, timeSource = { 0L })

        handler.handle { calls++ }

        assertEquals(1, calls)
    }

    @Test
    fun `debounce handler uses its monotonic time source`() {
        var now = 1_000L
        var calls = 0
        val handler = DebounceHandler(defaultDebounceTime = 500L, timeSource = { now })

        handler.handle { calls++ }
        now += 400L
        handler.handle { calls++ }
        now += 100L
        handler.handle { calls++ }
        now += 1L
        handler.handle { calls++ }

        assertEquals(2, calls)
    }

    @Test
    fun `multi debounce handler keeps independent monotonic timestamps`() {
        var now = 0L
        val calls = mutableListOf<String>()
        val handler = MultiDebounceHandler(timeSource = { now })

        handler.handle("first", 500L) { calls += "first" }
        handler.handle("second", 500L) { calls += "second" }
        now += 500L
        handler.handle("first", 500L) { calls += "first" }
        now += 1L
        handler.handle("first", 500L) { calls += "first" }

        assertEquals(listOf("first", "second", "first"), calls)
    }
}
