package com.leovp.audio.base

import com.leovp.audio.AudioTrackPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class StreamPlayerStopperTest {
    @Test
    fun `concurrent callers share one teardown`() = runTest {
        val releaseStarted = CompletableDeferred<Unit>()
        val allowRelease = CompletableDeferred<Unit>()
        var detachCount = 0
        val scope = CoroutineScope(Job())
        val audioTrackPlayer = mockk<AudioTrackPlayer>(relaxed = true)
        val subject = StreamPlayerStopper("Test", scope, audioTrackPlayer) {
            detachCount++
            Any()
        }

        val leader = async {
            subject.stopAndJoin {
                releaseStarted.complete(Unit)
                allowRelease.await()
            }
        }
        releaseStarted.await()
        val follower = async {
            subject.stopAndJoin {
                error("Follower must not release the decoder")
            }
        }
        yield()

        assertFalse(follower.isCompleted)
        allowRelease.complete(Unit)
        leader.await()
        follower.await()

        assertEquals(1, detachCount)
        verify(exactly = 1) { audioTrackPlayer.stop() }
        verify(exactly = 1) { audioTrackPlayer.release() }
    }

    @Test
    fun `suspending stop propagates cancellation after common cleanup`() = runTest {
        val events = mutableListOf<String>()
        val scope = CoroutineScope(Job())
        val audioTrackPlayer = mockk<AudioTrackPlayer>()
        every { audioTrackPlayer.stop() } answers { events += "stop-audio-track" }
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
            listOf(
                "detach-decoder",
                "stop-audio-track",
                "release-decoder",
                "audio-track"
            ),
            events
        )
        assertFalse(scope.isActive)
        verify(exactly = 1) { audioTrackPlayer.stop() }
        verify(exactly = 1) { audioTrackPlayer.release() }
    }

    @Test
    fun `suspending stop logs ordinary decoder failures without throwing`() = runTest {
        val events = mutableListOf<String>()
        val scope = CoroutineScope(Job())
        val audioTrackPlayer = mockk<AudioTrackPlayer>()
        every { audioTrackPlayer.stop() } answers { events += "stop-audio-track" }
        every { audioTrackPlayer.release() } answers { events += "audio-track" }
        val subject = StreamPlayerStopper("Test", scope, audioTrackPlayer) {
            events += "detach-decoder"
            Any()
        }

        subject.stopAndJoin {
            events += "release-decoder"
            error("decoder release failed")
        }

        assertEquals(
            listOf(
                "detach-decoder",
                "stop-audio-track",
                "release-decoder",
                "audio-track"
            ),
            events
        )
        assertFalse(scope.isActive)
        verify(exactly = 1) { audioTrackPlayer.stop() }
        verify(exactly = 1) { audioTrackPlayer.release() }
    }

    @Test
    fun `suspending stop rejects calls from its owned scope before side effects`() = runTest {
        val ownerJob = Job()
        val scope = CoroutineScope(ownerJob)
        val audioTrackPlayer = mockk<AudioTrackPlayer>(relaxed = true)
        val subject = StreamPlayerStopper("Test", scope, audioTrackPlayer) { Any() }

        val child = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            assertIs<IllegalArgumentException>(
                runCatching { subject.stopAndJoin {} }.exceptionOrNull()
            )
        }
        child.join()

        assertTrue(scope.isActive)
        verify(exactly = 0) { audioTrackPlayer.stop() }
        verify(exactly = 0) { audioTrackPlayer.release() }
        ownerJob.cancel()
    }
}
