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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

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

    @Test
    fun `worker drains delayed output EOS and reports completion once`() = runTest {
        val endOfStream = CountDownLatch(1)
        val outputDequeueCount = AtomicInteger(0)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        every { mediaCodec.dequeueInputBuffer(0) } returns 0
        every { mediaCodec.getInputBuffer(0) } returns ByteBuffer.allocate(16)
        every { mediaCodec.dequeueOutputBuffer(any(), any()) } answers {
            val info = firstArg<MediaCodec.BufferInfo>()
            if (outputDequeueCount.getAndIncrement() == 0) {
                MediaCodec.INFO_TRY_AGAIN_LATER
            } else {
                info.offset = 0
                info.size = 4
                info.presentationTimeUs = 0
                info.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                1
            }
        }
        every { mediaCodec.getOutputBuffer(1) } returns ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
        val subject = EosCodec(mediaCodec, endOfStream)

        subject.start()
        assertTrue(endOfStream.await(2, TimeUnit.SECONDS), "Output EOS was not reported")
        subject.releaseAndJoin()

        assertEquals(1, subject.outputCount.get())
        assertEquals(1, subject.endCount.get())
        verify(exactly = 1) {
            mediaCodec.queueInputBuffer(0, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        verify(exactly = 1) { mediaCodec.releaseOutputBuffer(1, false) }
    }

    @Test
    fun `worker reports immediate output EOS exactly once`() = runTest {
        val endOfStream = CountDownLatch(1)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        every { mediaCodec.dequeueInputBuffer(0) } returns 0
        every { mediaCodec.getInputBuffer(0) } returns ByteBuffer.allocate(16)
        every { mediaCodec.dequeueOutputBuffer(any(), any()) } answers {
            firstArg<MediaCodec.BufferInfo>().flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
            1
        }
        every { mediaCodec.getOutputBuffer(1) } returns ByteBuffer.allocate(0)
        val subject = EosCodec(mediaCodec, endOfStream)

        subject.start()
        assertTrue(endOfStream.await(2, TimeUnit.SECONDS), "Output EOS was not reported")
        subject.releaseAndJoin()

        assertEquals(1, subject.endCount.get())
        verify(exactly = 1) { mediaCodec.releaseOutputBuffer(1, false) }
    }

    @Test
    fun `worker reports EOS even when its output buffer is null`() = runTest {
        val endOfStream = CountDownLatch(1)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        every { mediaCodec.dequeueInputBuffer(0) } returns 0
        every { mediaCodec.getInputBuffer(0) } returns ByteBuffer.allocate(16)
        every { mediaCodec.dequeueOutputBuffer(any(), any()) } answers {
            firstArg<MediaCodec.BufferInfo>().flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
            1
        }
        every { mediaCodec.getOutputBuffer(1) } returns null
        val subject = EosCodec(mediaCodec, endOfStream)

        subject.start()
        assertTrue(endOfStream.await(2, TimeUnit.SECONDS), "Output EOS was not reported")
        subject.releaseAndJoin()

        assertEquals(0, subject.outputCount.get())
        assertEquals(1, subject.endCount.get())
        verify(exactly = 1) { mediaCodec.releaseOutputBuffer(1, false) }
    }

    @Test
    fun `missing output EOS reports timeout failure`() = runTest {
        val failure = CountDownLatch(1)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        every { mediaCodec.dequeueInputBuffer(0) } returns 0
        every { mediaCodec.getInputBuffer(0) } returns ByteBuffer.allocate(16)
        every { mediaCodec.dequeueOutputBuffer(any(), any()) } returns
            MediaCodec.INFO_TRY_AGAIN_LATER
        val subject = FailureCodec(mediaCodec, failure, inputPtsUs = -1)

        subject.start()
        assertTrue(failure.await(2, TimeUnit.SECONDS), "EOS timeout was not reported")
        subject.releaseAndJoin()

        assertEquals(1, subject.failureCount.get())
        assertEquals(0, subject.endCount.get())
    }

    @Test
    fun `failure inside the EOS drain loop is reported exactly once`() = runTest {
        val failure = CountDownLatch(1)
        val outputDequeueCount = AtomicInteger(0)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        every { mediaCodec.dequeueInputBuffer(0) } returns 0
        every { mediaCodec.getInputBuffer(0) } returns ByteBuffer.allocate(16)
        // Input EOS is queued cleanly; the codec only breaks once the drain loop polls again.
        every { mediaCodec.dequeueOutputBuffer(any(), any()) } answers {
            if (outputDequeueCount.getAndIncrement() == 0) {
                MediaCodec.INFO_TRY_AGAIN_LATER
            } else {
                error("codec died while draining")
            }
        }
        val subject = FailureCodec(mediaCodec, failure, inputPtsUs = -1)

        subject.start()
        assertTrue(failure.await(2, TimeUnit.SECONDS), "Codec failure was not reported")
        subject.releaseAndJoin()

        // The drain timeout must not synthesize a second, misleading failure.
        assertEquals(1, subject.failureCount.get())
        assertEquals(0, subject.endCount.get())
    }

    @Test
    fun `illegal codec state reports failure`() = runTest {
        val failure = CountDownLatch(1)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        every { mediaCodec.dequeueInputBuffer(0) } throws IllegalStateException("broken")
        val subject = FailureCodec(mediaCodec, failure)

        subject.start()
        assertTrue(failure.await(2, TimeUnit.SECONDS), "Codec failure was not reported")
        subject.releaseAndJoin()

        assertEquals(1, subject.failureCount.get())
        assertEquals(0, subject.endCount.get())
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

    private class EosCodec(
        private val mediaCodec: MediaCodec,
        private val endOfStream: CountDownLatch,
    ) : BaseMediaCodecSynchronous(
        codecName = MediaFormat.MIMETYPE_AUDIO_AAC,
        sampleRate = 8_000,
        channelCount = 1
    ) {
        val outputCount = AtomicInteger(0)
        val endCount = AtomicInteger(0)

        override fun createMediaFormat() = Unit

        override fun createCodec() {
            codec = mediaCodec
        }

        override fun setFormatOptions(format: MediaFormat) = Unit

        override fun onInputData(inBuf: ByteBuffer): Int = 0

        override fun onOutputData(
            outBuf: ByteBuffer,
            info: MediaCodec.BufferInfo,
            isConfig: Boolean,
            isKeyFrame: Boolean,
        ) {
            outputCount.incrementAndGet()
        }

        override fun computePresentationTimeUs(): Long = -1

        override fun onEndOfStream() {
            endCount.incrementAndGet()
            endOfStream.countDown()
        }
    }

    private class FailureCodec(
        private val mediaCodec: MediaCodec,
        private val failure: CountDownLatch,
        private val inputPtsUs: Long = 0,
    ) : BaseMediaCodecSynchronous(
        codecName = MediaFormat.MIMETYPE_AUDIO_AAC,
        sampleRate = 8_000,
        channelCount = 1
    ) {
        val failureCount = AtomicInteger(0)
        val endCount = AtomicInteger(0)

        override val eosDrainTimeoutMs: Long = 50

        override fun createMediaFormat() = Unit

        override fun createCodec() {
            codec = mediaCodec
        }

        override fun setFormatOptions(format: MediaFormat) = Unit

        override fun onInputData(inBuf: ByteBuffer): Int = 0

        override fun onOutputData(
            outBuf: ByteBuffer,
            info: MediaCodec.BufferInfo,
            isConfig: Boolean,
            isKeyFrame: Boolean,
        ) = Unit

        override fun computePresentationTimeUs(): Long = inputPtsUs

        override fun notifyCodecFailure(error: Throwable) {
            failureCount.incrementAndGet()
            failure.countDown()
        }

        override fun onEndOfStream() {
            endCount.incrementAndGet()
        }
    }
}
