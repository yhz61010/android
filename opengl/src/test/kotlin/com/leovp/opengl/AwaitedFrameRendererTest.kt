package com.leovp.opengl

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AwaitedFrameRendererTest {
    @Test
    fun `a batch cannot overwrite a frame before the GL consumer draws it`() = runTest {
        val renderer = AwaitedFrameRenderer()
        val uploaded = mutableListOf<Int>()
        val drawn = mutableListOf<Int>()
        var requested = 0
        val producer = launch {
            for (frame in 1..3) {
                renderer.render({ uploaded += frame }, { requested++ })
            }
        }
        runCurrent()
        assertTrue(uploaded.isEmpty())
        assertEquals(1, requested)
        assertFalse(producer.isCompleted)
        for (frame in 1..3) {
            renderer.drawFrame { drawn += uploaded.last() }
            runCurrent()
            assertEquals((1..frame).toList(), drawn)
        }
        producer.join()
        assertEquals(listOf(1, 2, 3), uploaded)
    }

    @Test
    fun `cancelling while the surface is unavailable removes the queued upload`() = runTest {
        val renderer = AwaitedFrameRenderer()
        var uploads = 0
        val waiting = launch { renderer.render({ uploads++ }, {}) }
        runCurrent()
        waiting.cancel()
        waiting.join()
        renderer.drawFrame { }
        assertEquals(0, uploads)
        val next = launch { renderer.render({ uploads++ }, {}) }
        runCurrent()
        renderer.drawFrame { }
        next.join()
        assertEquals(1, uploads)
    }

    @Test
    fun `drawing failure reaches the producer and permits the next frame`() = runTest {
        val renderer = AwaitedFrameRenderer()
        val producer = launch {
            assertFailsWith<IllegalStateException> { renderer.render({}, {}) }
        }
        runCurrent()
        renderer.drawFrame { error("GL upload failed") }
        producer.join()
        val next = launch { renderer.render({}, {}) }
        runCurrent()
        renderer.drawFrame { }
        next.join()
    }

    @Test
    fun `a rejected render request does not strand the next producer`() = runTest {
        val renderer = AwaitedFrameRenderer()
        assertFailsWith<IllegalStateException> {
            renderer.render({}, { error("Surface request rejected") })
        }
        val producer = launch { renderer.render({}, {}) }
        runCurrent()
        renderer.drawFrame { }
        producer.join()
    }

    @Test
    fun `concurrent producers each wait for their own GL consumption`() = runTest {
        val renderer = AwaitedFrameRenderer()
        val uploaded = mutableListOf<Int>()
        val first = launch { renderer.render({ uploaded += 1 }, {}) }
        val second = launch { renderer.render({ uploaded += 2 }, {}) }
        runCurrent()
        assertTrue(uploaded.isEmpty())
        renderer.drawFrame { }
        runCurrent()
        assertTrue(first.isCompleted)
        assertFalse(second.isCompleted)
        assertEquals(listOf(1), uploaded)
        renderer.drawFrame { }
        second.join()
        assertEquals(listOf(1, 2), uploaded)
    }

    @Test
    fun `a fatal draw error wakes the producer and remains fatal on the GL thread`() = runTest {
        val renderer = AwaitedFrameRenderer()
        val producer = launch {
            assertFailsWith<AssertionError> { renderer.render({}, {}) }
        }
        runCurrent()
        assertFailsWith<AssertionError> { renderer.drawFrame { throw AssertionError("fatal") } }
        producer.join()
    }

    @Test
    fun `unsolicited surface redraws still render the current frame`() {
        val renderer = AwaitedFrameRenderer()
        var draws = 0
        renderer.drawFrame { draws++ }
        assertEquals(1, draws)
    }
}
