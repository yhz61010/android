package com.leovp.camera2live.codec

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class BoundedFrameQueueTest {
    @Test
    fun `offer drops oldest frame and reports the drop`() {
        var droppedFrames = 0
        val queue = BoundedFrameQueue(2) { droppedFrames++ }

        assertTrue(queue.offer(byteArrayOf(1)))
        assertTrue(queue.offer(byteArrayOf(2)))
        assertTrue(queue.offer(byteArrayOf(3)))

        assertEquals(1, droppedFrames)
        assertContentEquals(byteArrayOf(2), queue.poll())
        assertContentEquals(byteArrayOf(3), queue.poll())
    }

    @Test
    fun `addAll applies the same bounded drop policy`() {
        var droppedFrames = 0
        val queue = BoundedFrameQueue(2) { droppedFrames++ }

        assertTrue(queue.addAll(listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))))

        assertEquals(1, droppedFrames)
        assertEquals(2, queue.size)
        assertContentEquals(byteArrayOf(2), queue.poll())
        assertContentEquals(byteArrayOf(3), queue.poll())
    }

    @Test
    fun `capacity must be positive`() {
        assertFailsWith<IllegalArgumentException> { BoundedFrameQueue(0) }
    }
}
