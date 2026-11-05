package com.leovp.demo.basiccomponents.examples.audio

import com.leovp.audio.base.AudioType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingIntentCoordinatorTest {
    @Test
    fun `latest format request invalidates an older permission result`() {
        val coordinator = RecordingIntentCoordinator()
        val pcm = coordinator.requestStart(AudioType.PCM)
        val opus = coordinator.requestStart(AudioType.OPUS)

        assertFalse(coordinator.isCurrent(pcm, AudioType.PCM))
        assertTrue(coordinator.isCurrent(opus, AudioType.OPUS))
    }

    @Test
    fun `turning off an old format does not stop the new intent`() {
        val coordinator = RecordingIntentCoordinator()
        coordinator.requestStart(AudioType.PCM)
        val opus = coordinator.requestStart(AudioType.OPUS)

        assertFalse(coordinator.requestStop(AudioType.PCM))
        assertTrue(coordinator.isCurrent(opus, AudioType.OPUS))
        assertTrue(coordinator.requestStop(AudioType.OPUS))
        assertFalse(coordinator.isCurrent(opus, AudioType.OPUS))
    }

    @Test
    fun `destroy invalidates every pending permission result`() {
        val coordinator = RecordingIntentCoordinator()
        val pending = coordinator.requestStart(AudioType.AAC)

        coordinator.stopAll()

        assertFalse(coordinator.isCurrent(pending, AudioType.AAC))
    }

    @Test
    fun `late failure from an old same-format attempt does not stop its replacement`() {
        val coordinator = RecordingIntentCoordinator()
        val oldAttempt = coordinator.requestStart(AudioType.AAC)
        val replacement = coordinator.requestStart(AudioType.AAC)

        assertFalse(coordinator.failIfCurrent(oldAttempt, AudioType.AAC))
        assertTrue(coordinator.isCurrent(replacement, AudioType.AAC))
        assertTrue(coordinator.failIfCurrent(replacement, AudioType.AAC))
        assertFalse(coordinator.isCurrent(replacement, AudioType.AAC))
    }
}
