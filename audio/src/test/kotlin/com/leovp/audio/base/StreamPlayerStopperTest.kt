package com.leovp.audio.base

import com.leovp.audio.AudioTrackPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest

class StreamPlayerStopperTest {
    @Test
    fun `legacy stop releases resources in teardown order`() {
        val events = mutableListOf<String>()
        val scope = CoroutineScope(Job())
        val audioTrackPlayer = mockk<AudioTrackPlayer>()
        val decoder = Any()
        every { audioTrackPlayer.release() } answers { events += "audio-track" }
        val subject = StreamPlayerStopper("Test", scope, audioTrackPlayer) {
            events += "detach-decoder"
            decoder
        }

        subject.stop {
            assertEquals(decoder, it)
            events += "release-decoder"
        }

        assertEquals(
            listOf("detach-decoder", "audio-track", "release-decoder"),
            events
        )
        assertFalse(scope.isActive)
        verify(exactly = 1) { audioTrackPlayer.release() }
    }

    @Test
    fun `suspending stop propagates cancellation after common cleanup`() = runTest {
        val events = mutableListOf<String>()
        val scope = CoroutineScope(Job())
        val audioTrackPlayer = mockk<AudioTrackPlayer>()
        every { audioTrackPlayer.release() } answers { events += "audio-track" }
        val subject = StreamPlayerStopper("Test", scope, audioTrackPlayer) {
            events += "detach-decoder"
            Any()
        }

        val thrown = try {
            subject.stopAndJoin {
                events += "release-decoder"
                throw CancellationException("cancel teardown")
            }
            null
        } catch (e: CancellationException) {
            e
        }

        assertIs<CancellationException>(thrown)
        assertEquals(
            listOf("detach-decoder", "audio-track", "release-decoder"),
            events
        )
        assertFalse(scope.isActive)
        verify(exactly = 1) { audioTrackPlayer.release() }
    }
}
