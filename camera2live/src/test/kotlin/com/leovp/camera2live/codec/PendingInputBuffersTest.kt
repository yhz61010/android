package com.leovp.camera2live.codec

import java.nio.ByteBuffer
import java.util.ArrayDeque
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class PendingInputBuffersTest {
    @Test
    fun `oversized frame keeps input buffer available`() {
        val pendingInputBuffers = PendingInputBuffers().apply { add(7) }
        val frames = ArrayDeque<ByteArray>().apply { add(ByteArray(4)) }
        var failure: Exception? = null

        pendingInputBuffers.drain(
            nextFrame = frames::poll,
            submit = { _, data ->
                ByteBuffer.allocate(2).putEncoderFrame(data)
            },
            onFailure = { failure = it }
        )

        assertIs<IllegalArgumentException>(failure)
        assertEquals(1, pendingInputBuffers.size)
        assertEquals(0, frames.size)

        frames.add(ByteArray(2))
        pendingInputBuffers.drain(
            nextFrame = frames::poll,
            submit = { _, data -> ByteBuffer.allocate(2).put(data) },
            onFailure = { throw it }
        )

        assertEquals(0, pendingInputBuffers.size)
        assertEquals(0, frames.size)
    }

    @Test
    fun `successful submission consumes input buffer`() {
        val pendingInputBuffers = PendingInputBuffers().apply { add(7) }
        val frames = ArrayDeque<ByteArray>().apply { add(ByteArray(2)) }

        pendingInputBuffers.drain(
            nextFrame = frames::poll,
            submit = { _, data -> ByteBuffer.allocate(2).put(data) },
            onFailure = { throw it }
        )

        assertEquals(0, pendingInputBuffers.size)
        assertEquals(0, frames.size)
    }
}
