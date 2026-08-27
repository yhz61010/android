package com.leovp.audio.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseMediaCodecSynchronousTest {
    @Suppress("DEPRECATION")
    @Test
    fun `legacy release waits for the active codec iteration`() {
        val enteredInput = CountDownLatch(1)
        val continueInput = CountDownLatch(1)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        every { mediaCodec.dequeueInputBuffer(0) } returns 0
        every { mediaCodec.getInputBuffer(0) } returns ByteBuffer.allocate(16)
        every { mediaCodec.dequeueOutputBuffer(any(), 0) } returns MediaCodec.INFO_TRY_AGAIN_LATER
        val subject = BlockingCodec(mediaCodec, enteredInput, continueInput)
        val releaseExecutor = Executors.newSingleThreadExecutor()

        try {
            subject.start()
            assertTrue(enteredInput.await(2, TimeUnit.SECONDS), "Worker did not enter onInputData")

            val releaseFuture = releaseExecutor.submit { subject.release() }

            assertFailsWith<TimeoutException> { releaseFuture.get(100, TimeUnit.MILLISECONDS) }
            assertFalse(releaseFuture.isDone, "release() must wait for the active codec iteration")
            verify(exactly = 0) { mediaCodec.release() }

            continueInput.countDown()
            releaseFuture.get(2, TimeUnit.SECONDS)
            verify(exactly = 1) { mediaCodec.release() }
        } finally {
            continueInput.countDown()
            releaseExecutor.shutdownNow()
        }
    }

    private class BlockingCodec(
        private val mediaCodec: MediaCodec,
        private val enteredInput: CountDownLatch,
        private val continueInput: CountDownLatch,
    ) : BaseMediaCodecSynchronous(
        codecName = MediaFormat.MIMETYPE_AUDIO_AAC,
        sampleRate = 8_000,
        channelCount = 1
    ) {
        override fun createMediaFormat() = Unit

        override fun createCodec() {
            codec = mediaCodec
        }

        override fun setFormatOptions(format: MediaFormat) = Unit

        override fun onInputData(inBuf: ByteBuffer): Int {
            enteredInput.countDown()
            assertTrue(continueInput.await(2, TimeUnit.SECONDS), "Timed out waiting to continue")
            return 0
        }

        override fun onOutputData(
            outBuf: ByteBuffer,
            info: MediaCodec.BufferInfo,
            isConfig: Boolean,
            isKeyFrame: Boolean,
        ) = Unit

        override fun computePresentationTimeUs(): Long = 0

        override fun onEndOfStream() = Unit
    }
}
