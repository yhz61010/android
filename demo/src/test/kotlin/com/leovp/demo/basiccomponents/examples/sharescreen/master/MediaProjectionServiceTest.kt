package com.leovp.demo.basiccomponents.examples.sharescreen.master

import android.media.projection.MediaProjection
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaProjectionServiceTest {
    @Test
    fun `start failure attempts every cleanup and retains both failures as suppressed`() {
        val processor = mockk<ScreenProcessor>()
        val projection = mockk<MediaProjection>()
        val startFailure = IllegalStateException("start failed")
        val releaseFailure = IllegalStateException("release failed")
        val projectionFailure = IllegalStateException("projection stop failed")
        every { processor.onRelease() } throws releaseFailure
        every { projection.stop() } throws projectionFailure

        releaseProcessorAfterStartFailure(processor, projection, startFailure)

        assertEquals(2, startFailure.suppressed.size)
        assertSame(releaseFailure, startFailure.suppressed[0])
        assertSame(projectionFailure, startFailure.suppressed[1])
    }

    @Test
    fun `start failure is not suppressed into itself when release rethrows it`() {
        val processor = mockk<ScreenProcessor>()
        val projection = mockk<MediaProjection>()
        val failure = IllegalStateException("shared failure")
        var projectionStopped = false
        every { processor.onRelease() } throws failure
        every { projection.stop() } answers { projectionStopped = true }

        assertDoesNotThrow { releaseProcessorAfterStartFailure(processor, projection, failure) }
        assertTrue(failure.suppressed.isEmpty())
        assertTrue(projectionStopped)
    }
}
