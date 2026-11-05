package com.leovp.audio.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class BaseMediaCodecAsynchronousTest {
    @Test
    fun `late input callback is ignored after release begins`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.attachCallback()
        runBlocking { subject.releaseAndJoin() }
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 1)

        verify(exactly = 0) { mediaCodec.getInputBuffer(any()) }
        verify(exactly = 0) { mediaCodec.queueInputBuffer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `release waits for an active asynchronous input callback`() {
        val enteredInput = CountDownLatch(1)
        val continueInput = CountDownLatch(1)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(2) } returns ByteBuffer.allocate(16)
        val subject = TestCodec(mediaCodec, enteredInput, continueInput)
        val callbackExecutor = Executors.newSingleThreadExecutor()
        val releaseExecutor = Executors.newSingleThreadExecutor()

        try {
            subject.attachCallback()
            val callbackFuture = callbackExecutor.submit {
                callbackSlot.captured.onInputBufferAvailable(mediaCodec, 2)
            }
            assertTrue(enteredInput.await(2, TimeUnit.SECONDS), "Callback did not enter input")

            val releaseFuture = releaseExecutor.submit { runBlocking { subject.releaseAndJoin() } }
            assertFailsWith<TimeoutException> {
                releaseFuture.get(100, TimeUnit.MILLISECONDS)
            }
            verify(exactly = 0) { mediaCodec.release() }

            continueInput.countDown()
            callbackFuture.get(2, TimeUnit.SECONDS)
            releaseFuture.get(2, TimeUnit.SECONDS)
            verify(exactly = 1) { mediaCodec.queueInputBuffer(2, 0, 0, 0, 0) }
            verify(exactly = 1) { mediaCodec.release() }
        } finally {
            continueInput.countDown()
            callbackExecutor.shutdownNow()
            releaseExecutor.shutdownNow()
        }
    }

    @Test
    fun `input callback failure returns dequeued buffer`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(4) } returns ByteBuffer.allocate(16)
        val subject = TestCodec(mediaCodec, failInput = true)

        subject.attachCallback()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 4)
        runBlocking { subject.releaseAndJoin() }

        verify(exactly = 1) { mediaCodec.queueInputBuffer(4, 0, 0, 0, 0) }
        assertEquals(1, subject.failureCount)
    }

    @Test
    fun `null input buffer is returned to codec`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(5) } returns null
        val subject = TestCodec(mediaCodec)

        subject.attachCallback()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 5)
        runBlocking { subject.releaseAndJoin() }

        verify(exactly = 1) { mediaCodec.queueInputBuffer(5, 0, 0, 0, 0) }
    }

    @Test
    fun `oversized input returns buffer and reports failure`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(6) } returns ByteBuffer.allocate(8)
        val subject = TestCodec(mediaCodec, inputSize = 16)

        subject.attachCallback()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 6)
        runBlocking { subject.releaseAndJoin() }

        verify(exactly = 1) { mediaCodec.queueInputBuffer(6, 0, 0, 0, 0) }
        assertEquals(1, subject.failureCount)
    }

    @Test
    fun `failed EOS submission preserves EOS flag during compensation`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(7) } returns ByteBuffer.allocate(8)
        var attempts = 0
        every {
            mediaCodec.queueInputBuffer(
                7,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        } answers {
            if (attempts++ == 0) error("queue EOS failed")
        }
        val subject = TestCodec(mediaCodec, inputPtsUs = -1)

        subject.attachCallback()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 7)
        runBlocking { subject.releaseAndJoin() }

        verify(exactly = 2) {
            mediaCodec.queueInputBuffer(
                7,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
        assertEquals(1, subject.failureCount)
    }

    @Test
    fun `output lookup failure still returns buffer and reports failure`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getOutputBuffer(8) } throws IllegalStateException("lookup failed")
        val subject = TestCodec(mediaCodec)

        subject.attachCallback()
        callbackSlot.captured.onOutputBufferAvailable(
            mediaCodec,
            8,
            MediaCodec.BufferInfo()
        )
        runBlocking { subject.releaseAndJoin() }

        verify(exactly = 1) { mediaCodec.releaseOutputBuffer(8, false) }
        assertEquals(1, subject.failureCount)
    }

    @Test
    fun `late callbacks are ignored after terminal release`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        val initialFormat = mockk<MediaFormat>()
        val changedFormat = mockk<MediaFormat>()
        val codecException = mockk<MediaCodec.CodecException>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.attachCallback(initialFormat)
        runBlocking { subject.releaseAndJoin() }
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 1)
        callbackSlot.captured.onOutputBufferAvailable(
            mediaCodec,
            2,
            MediaCodec.BufferInfo()
        )
        callbackSlot.captured.onOutputFormatChanged(mediaCodec, changedFormat)
        callbackSlot.captured.onError(mediaCodec, codecException)

        verify(exactly = 0) { mediaCodec.getInputBuffer(any()) }
        verify(exactly = 0) { mediaCodec.getOutputBuffer(any()) }
        assertSame(initialFormat, subject.currentFormat())
        assertEquals(0, subject.errorCount)
    }

    @Test
    fun `EOS output data is delivered before completion and buffer is released`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getOutputBuffer(3) } returns
            ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
        val subject = TestCodec(mediaCodec)
        val info = MediaCodec.BufferInfo().apply {
            offset = 0
            size = 4
            presentationTimeUs = 0
            flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
        }

        subject.attachCallback()
        callbackSlot.captured.onOutputBufferAvailable(mediaCodec, 3, info)
        runBlocking { subject.releaseAndJoin() }

        assertEquals(1, subject.outputCount)
        assertEquals(1, subject.endCount)
        verify(exactly = 1) { mediaCodec.releaseOutputBuffer(3, false) }
    }

    @Test
    fun `null EOS output still reports completion and releases the buffer`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getOutputBuffer(9) } returns null
        val subject = TestCodec(mediaCodec)
        val info = MediaCodec.BufferInfo().apply {
            flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
        }

        subject.attachCallback()
        callbackSlot.captured.onOutputBufferAvailable(mediaCodec, 9, info)
        runBlocking { subject.releaseAndJoin() }

        assertEquals(0, subject.outputCount)
        assertEquals(1, subject.endCount)
        verify(exactly = 1) { mediaCodec.releaseOutputBuffer(9, false) }
    }

    @Test
    fun `flush replaces the codec and ignores late callbacks from the retired session`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val replacement = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        val replacementCallback = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { replacement.setCallback(capture(replacementCallback)) } just Runs
        val subject = TestCodec(mediaCodec, replacementCodec = replacement)

        subject.attachCallback()
        subject.flush()

        verify(exactly = 1) { mediaCodec.flush() }
        verify(exactly = 1) { mediaCodec.release() }
        verify(exactly = 1) { replacement.start() }
        val replacementFormat = subject.currentFormat()
        val info = MediaCodec.BufferInfo()
        callbackSlot.captured.onOutputBufferAvailable(mediaCodec, 9, info)
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 11)
        callbackSlot.captured.onError(mediaCodec, mockk(relaxed = true))
        callbackSlot.captured.onOutputFormatChanged(mediaCodec, mockk(relaxed = true))
        verify(exactly = 0) { mediaCodec.getOutputBuffer(any()) }
        verify(exactly = 0) { mediaCodec.releaseOutputBuffer(any(), any<Boolean>()) }
        verify(exactly = 0) { mediaCodec.getInputBuffer(any()) }
        assertEquals(0, subject.failureCount)
        assertSame(replacementFormat, subject.currentFormat())

        replacementCallback.captured.onOutputBufferAvailable(replacement, 10, info)
        replacementCallback.captured.onInputBufferAvailable(replacement, 12)
        verify(exactly = 1) { replacement.getOutputBuffer(10) }
        verify(exactly = 1) { replacement.releaseOutputBuffer(10, false) }
        verify(exactly = 1) { replacement.getInputBuffer(12) }
        runBlocking { subject.releaseAndJoin() }
        verify(exactly = 1) { mediaCodec.release() }
        verify(exactly = 1) { replacement.release() }
    }

    @Test
    fun `codec failure is terminal and owner is notified once`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(11) } returns ByteBuffer.allocate(8)
        val subject = TestCodec(mediaCodec)

        subject.attachCallback()
        callbackSlot.captured.onError(mediaCodec, mockk(relaxed = true))
        callbackSlot.captured.onError(mediaCodec, mockk(relaxed = true))
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 11)

        assertEquals(1, subject.failureCount)
        verify(exactly = 0) { mediaCodec.getInputBuffer(11) }
        runBlocking { subject.releaseAndJoin() }
    }

    @Test
    fun `new callbacks during reset start are processed while old callbacks are rejected`() {
        val oldCodec = mockk<MediaCodec>(relaxed = true)
        val newCodec = mockk<MediaCodec>(relaxed = true)
        val oldCallback = slot<MediaCodec.Callback>()
        val newCallback = slot<MediaCodec.Callback>()
        every { oldCodec.setCallback(capture(oldCallback)) } just Runs
        every { newCodec.setCallback(capture(newCallback)) } just Runs
        val subject = TestCodec(oldCodec, replacementCodec = newCodec)
        subject.attachCallback()
        every { newCodec.start() } answers {
            oldCallback.captured.onOutputBufferAvailable(oldCodec, 1, MediaCodec.BufferInfo())
            newCallback.captured.onOutputBufferAvailable(newCodec, 2, MediaCodec.BufferInfo())
        }

        subject.flush()

        verify(exactly = 0) { oldCodec.getOutputBuffer(1) }
        verify(exactly = 0) { oldCodec.releaseOutputBuffer(1, false) }
        verify(exactly = 1) { newCodec.releaseOutputBuffer(2, false) }
        assertEquals(0, subject.failureCount)
        runBlocking { subject.releaseAndJoin() }
    }

    @Test
    fun `consecutive resets retain only the latest callback session`() {
        val codecs = List(3) { mockk<MediaCodec>(relaxed = true) }
        val callbacks = List(3) { slot<MediaCodec.Callback>() }
        codecs.forEachIndexed { index, codec ->
            every { codec.setCallback(capture(callbacks[index])) } just Runs
        }
        val subject = TestCodec(codecs[0], replacementCodec = codecs[1])
        subject.attachCallback()
        subject.flush()
        subject.nextCodec = codecs[2]
        subject.flush()

        codecs.forEachIndexed { index, codec ->
            callbacks[index].captured.onOutputBufferAvailable(codec, index, MediaCodec.BufferInfo())
        }

        verify(exactly = 0) { codecs[0].getOutputBuffer(any()) }
        verify(exactly = 0) { codecs[1].getOutputBuffer(any()) }
        verify(exactly = 1) { codecs[2].releaseOutputBuffer(2, false) }
        runBlocking { subject.releaseAndJoin() }
        codecs.forEach { codec -> verify(exactly = 1) { codec.release() } }
    }

    @Test
    fun `reset failure still retires old codec and reports only once outside the lock`() {
        val oldCodec = mockk<MediaCodec>(relaxed = true)
        val failure = IllegalStateException("native flush failed")
        every { oldCodec.flush() } throws failure
        val subject = TestCodec(oldCodec, probeLockOnFailure = true)
        subject.attachCallback()

        subject.flush()

        assertEquals(1, subject.failureCount)
        assertSame(failure, subject.lastFailure)
        assertTrue(subject.lockFreeDuringFailure == true)
        assertFailsWith<IllegalStateException> { subject.flush() }
        runBlocking { subject.releaseAndJoin() }
        verify(exactly = 1) { oldCodec.release() }
    }

    @Test
    fun `replacement start failure is terminal and partial codec is released by owner`() {
        val oldCodec = mockk<MediaCodec>(relaxed = true)
        val newCodec = mockk<MediaCodec>(relaxed = true)
        every { newCodec.start() } throws IllegalStateException("start failed")
        val subject = TestCodec(oldCodec, replacementCodec = newCodec)
        subject.attachCallback()

        subject.flush()

        assertEquals(1, subject.failureCount)
        runBlocking { subject.releaseAndJoin() }
        verify(exactly = 1) { oldCodec.release() }
        verify(exactly = 1) { newCodec.release() }
    }

    @Test
    fun `replacement creation failure leaves no retired codec to release twice`() {
        val oldCodec = mockk<MediaCodec>(relaxed = true)
        val subject = TestCodec(oldCodec)
        subject.attachCallback()

        subject.flush()

        assertEquals(1, subject.failureCount)
        runBlocking { subject.releaseAndJoin() }
        verify(exactly = 1) { oldCodec.release() }
        assertEquals(1, subject.releaseHookCount)
    }

    @Test
    fun `release racing reset waits for ownership and prevents replacement start`() {
        val oldCodec = mockk<MediaCodec>(relaxed = true)
        val newCodec = mockk<MediaCodec>(relaxed = true)
        val enteredRetirement = CountDownLatch(1)
        val continueRetirement = CountDownLatch(1)
        val releaseThread = AtomicReference<Thread>()
        val resetExecutor = Executors.newSingleThreadExecutor()
        val releaseExecutor = Executors.newSingleThreadExecutor()
        val subject = TestCodec(oldCodec, replacementCodec = newCodec)
        subject.attachCallback()
        every { oldCodec.release() } answers {
            enteredRetirement.countDown()
            check(continueRetirement.await(2, TimeUnit.SECONDS))
        }

        try {
            val reset = resetExecutor.submit { subject.flush() }
            assertTrue(enteredRetirement.await(2, TimeUnit.SECONDS))
            val release = releaseExecutor.submit {
                releaseThread.set(Thread.currentThread())
                runBlocking { subject.releaseAndJoin() }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (releaseThread.get()?.state != Thread.State.WAITING) {
                check(System.nanoTime() < deadline) { "Release did not wait for codec lock" }
                Thread.yield()
            }
            continueRetirement.countDown()
            reset.get(2, TimeUnit.SECONDS)
            release.get(2, TimeUnit.SECONDS)

            verify(exactly = 0) { newCodec.start() }
            verify(exactly = 1) { oldCodec.release() }
            verify(exactly = 1) { newCodec.release() }
            assertEquals(0, subject.failureCount)
        } finally {
            continueRetirement.countDown()
            resetExecutor.shutdownNow()
            releaseExecutor.shutdownNow()
        }
    }

    @Test
    fun `native retirement failures are aggregated without double release`() {
        val oldCodec = mockk<MediaCodec>(relaxed = true)
        val flushError = IllegalStateException("flush failed")
        val releaseError = IllegalStateException("release failed")
        every { oldCodec.flush() } throws flushError
        every { oldCodec.release() } throws releaseError
        val subject = TestCodec(oldCodec)
        subject.attachCallback()

        subject.flush()

        assertSame(flushError, subject.lastFailure)
        assertTrue(flushError.suppressed.contains(releaseError))
        runBlocking { subject.releaseAndJoin() }
        verify(exactly = 1) { oldCodec.release() }
    }

    @Test
    fun `native cleanup cancellation still releases codec and subclass state`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val cancellation = CancellationException("native flush cancelled")
        every { mediaCodec.flush() } throws cancellation
        val subject = TestCodec(mediaCodec)
        subject.attachCallback()

        assertFailsWith<CancellationException> {
            runBlocking { subject.releaseAndJoin() }
        }

        verify(exactly = 1) { mediaCodec.release() }
        assertEquals(1, subject.releaseHookCount)
        runBlocking { subject.releaseAndJoin() }
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Test
    fun `start failure remains primary when cleanup throws cancellation`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val failure = AssertionError("start failed")
        val cancellation = CancellationException("native flush cancelled")
        every { mediaCodec.flush() } throws cancellation
        val subject = TestCodec(mediaCodec, startHookFailure = failure)

        val thrown = assertFailsWith<AssertionError> { subject.attachCallback() }

        assertSame(failure, thrown)
        assertTrue(failure.suppressed.contains(cancellation))
        verify(exactly = 1) { mediaCodec.release() }
        assertEquals(1, subject.releaseHookCount)
    }

    @Test
    fun `input callback after EOS does not queue another buffer`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(any()) } returns ByteBuffer.allocate(8)
        val subject = TestCodec(mediaCodec, inputPtsUs = -1)

        subject.attachCallback()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 12)
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 13)

        verify(exactly = 1) {
            mediaCodec.queueInputBuffer(
                12,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
        verify(exactly = 0) { mediaCodec.queueInputBuffer(13, any(), any(), any(), any()) }
        runBlocking { subject.releaseAndJoin() }
    }

    @Test
    fun `format callback failure is contained and terminates the session`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec, failFormat = true)

        subject.attachCallback()
        callbackSlot.captured.onOutputFormatChanged(mediaCodec, mockk())

        assertEquals(1, subject.failureCount)
        runBlocking { subject.releaseAndJoin() }
    }

    @Test
    fun `CancellationException from input callback becomes a codec failure`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(14) } returns ByteBuffer.allocate(8)
        val failure = CancellationException("input callback cancelled itself")
        val subject = TestCodec(mediaCodec, inputFailure = failure)

        subject.attachCallback()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 14)

        assertSame(failure, subject.lastFailure)
        assertEquals(1, subject.failureCount)
        verify(exactly = 1) { mediaCodec.queueInputBuffer(14, 0, 0, 0, 0) }
        runBlocking { subject.releaseAndJoin() }
    }

    @Test
    fun `CancellationException from output callback becomes a codec failure`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getOutputBuffer(15) } returns ByteBuffer.allocate(4)
        val failure = CancellationException("output callback cancelled itself")
        val subject = TestCodec(mediaCodec, outputFailure = failure)
        val info = MediaCodec.BufferInfo().apply { size = 4 }

        subject.attachCallback()
        callbackSlot.captured.onOutputBufferAvailable(mediaCodec, 15, info)

        assertSame(failure, subject.lastFailure)
        assertEquals(1, subject.failureCount)
        verify(exactly = 1) { mediaCodec.releaseOutputBuffer(15, false) }
        runBlocking { subject.releaseAndJoin() }
    }

    @Test
    fun `CancellationException from format callback becomes a codec failure`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val failure = CancellationException("format callback cancelled itself")
        val subject = TestCodec(mediaCodec, formatFailure = failure)

        subject.attachCallback()
        callbackSlot.captured.onOutputFormatChanged(mediaCodec, mockk())

        assertSame(failure, subject.lastFailure)
        assertEquals(1, subject.failureCount)
        runBlocking { subject.releaseAndJoin() }
    }

    @Test
    fun `CancellationException from error hook is suppressed on original codec error`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val codecFailure = mockk<MediaCodec.CodecException>(relaxed = true)
        val hookFailure = CancellationException("error hook cancelled itself")
        val subject = TestCodec(mediaCodec, errorHookFailure = hookFailure)

        subject.attachCallback()
        callbackSlot.captured.onError(mediaCodec, codecFailure)

        assertSame(codecFailure, subject.lastFailure)
        verify(exactly = 1) { codecFailure.addSuppressed(hookFailure) }
        assertEquals(1, subject.failureCount)
        runBlocking { subject.releaseAndJoin() }
    }

    @Test
    fun `CancellationException from owner failure hook does not escape callback thread`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(16) } returns ByteBuffer.allocate(8)
        val codecFailure = IllegalStateException("input failed")
        val ownerFailure = CancellationException("owner callback cancelled itself")
        val subject = TestCodec(
            mediaCodec,
            inputFailure = codecFailure,
            ownerFailure = ownerFailure
        )

        subject.attachCallback()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 16)

        assertEquals(1, subject.failureCount)
        assertTrue(codecFailure.suppressed.contains(ownerFailure))
        runBlocking { subject.releaseAndJoin() }
    }

    @Test
    fun `concurrent start admits only one caller`() {
        val ready = CountDownLatch(2)
        val startTogether = CountDownLatch(1)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)
        val startExecutor = Executors.newFixedThreadPool(2)

        try {
            val starts = List(2) {
                startExecutor.submit<Boolean> {
                    ready.countDown()
                    assertTrue(startTogether.await(2, TimeUnit.SECONDS), "Start gate timed out")
                    runCatching { subject.start() }.isSuccess
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS), "Start callers were not ready")
            startTogether.countDown()
            assertEquals(1, starts.count { it.get(2, TimeUnit.SECONDS) })
            runBlocking { subject.releaseAndJoin() }

            verify(exactly = 1) { mediaCodec.start() }
            verify(exactly = 1) { mediaCodec.release() }
        } finally {
            startTogether.countDown()
            startExecutor.shutdownNow()
        }
    }

    @Test
    fun `successful release remains exactly once across repeated calls`() = runTest {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.start()
        subject.releaseAndJoin()
        subject.releaseAndJoin()
        subject.releaseAndJoin()

        assertEquals(1, subject.releaseHookCount)
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Test
    fun `release waits for active format callback`() {
        val enteredFormat = CountDownLatch(1)
        val continueFormat = CountDownLatch(1)
        assertReleaseWaitsForCallback(
            enteredFormat,
            continueFormat,
            TestCodec(
                mockk(relaxed = true),
                enteredFormat = enteredFormat,
                continueFormat = continueFormat
            )
        ) { callback, codec ->
            callback.onOutputFormatChanged(codec, mockk())
        }
    }

    @Test
    fun `error callback notifies owner once outside the codec lock`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec, probeLockOnFailure = true)

        subject.attachCallback()
        callbackSlot.captured.onError(mediaCodec, mockk(relaxed = true))

        assertEquals(1, subject.errorCount)
        assertEquals(1, subject.failureCount)
        assertEquals(true, subject.lockFreeDuringFailure)
    }

    @Test
    fun `error hook failure is isolated and still notifies owner`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec, failError = true)

        subject.attachCallback()
        // A throwing subclass hook must not escape the MediaCodec callback thread.
        callbackSlot.captured.onError(mediaCodec, mockk(relaxed = true))

        assertEquals(1, subject.errorCount)
        assertEquals(1, subject.failureCount)
    }

    @Test
    fun `release waits for active error callback`() {
        val enteredError = CountDownLatch(1)
        val continueError = CountDownLatch(1)
        assertReleaseWaitsForCallback(
            enteredError,
            continueError,
            TestCodec(
                mockk(relaxed = true),
                enteredError = enteredError,
                continueError = continueError
            )
        ) { callback, codec ->
            callback.onError(codec, mockk())
        }
    }

    @Test
    fun `codec session can only be started once`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.start()
        val error = assertFailsWith<IllegalStateException> { subject.start() }
        runBlocking { subject.releaseAndJoin() }

        assertEquals("A BaseMediaCodec instance can only be started once", error.message)
        verify(exactly = 1) { mediaCodec.start() }
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Test
    fun `Error during codec start is rethrown after native cleanup`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val startFailure = AssertionError("start hook failed")
        val subject = TestCodec(mediaCodec, startHookFailure = startFailure)

        val thrown = runCatching { subject.start() }.exceptionOrNull()

        assertSame(startFailure, thrown)
        verify(exactly = 1) { mediaCodec.release() }
        runBlocking { subject.releaseAndJoin() }
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Test
    fun `start failure releases partially initialized codec and is terminal`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.start() } throws IllegalStateException("start failed")
        val subject = TestCodec(mediaCodec)

        val startError = assertFailsWith<IllegalStateException> { subject.start() }
        val restartError = assertFailsWith<IllegalStateException> { subject.start() }
        runBlocking { subject.releaseAndJoin() }

        assertEquals("start failed", startError.message)
        assertEquals("A BaseMediaCodec instance can only be started once", restartError.message)
        assertEquals(1, subject.releaseHookCount)
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Test
    fun `release before start makes the one-shot session terminal`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val subject = TestCodec(mediaCodec)

        runBlocking { subject.releaseAndJoin() }
        val error = assertFailsWith<IllegalStateException> { subject.start() }

        assertEquals("A BaseMediaCodec instance can only be started once", error.message)
        assertEquals(1, subject.releaseHookCount)
        verify(exactly = 0) { mediaCodec.start() }
        verify(exactly = 0) { mediaCodec.release() }
    }

    @Test
    fun `release racing start cleans partial codec and keeps session terminal`() {
        val enteredStart = CountDownLatch(1)
        val continueStart = CountDownLatch(1)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(
            mediaCodec,
            enteredStart = enteredStart,
            continueStart = continueStart
        )
        val startExecutor = Executors.newSingleThreadExecutor()
        val releaseExecutor = Executors.newSingleThreadExecutor()

        try {
            val startFuture = startExecutor.submit { subject.start() }
            assertTrue(enteredStart.await(2, TimeUnit.SECONDS), "start() did not create codec")

            val releaseFuture = releaseExecutor.submit { runBlocking { subject.releaseAndJoin() } }
            assertFailsWith<TimeoutException> { releaseFuture.get(100, TimeUnit.MILLISECONDS) }
            verify(exactly = 0) { mediaCodec.release() }

            continueStart.countDown()
            val startFailure = assertFailsWith<ExecutionException> {
                startFuture.get(2, TimeUnit.SECONDS)
            }
            assertIs<IllegalStateException>(startFailure.cause)
            releaseFuture.get(2, TimeUnit.SECONDS)

            assertFailsWith<IllegalStateException> { subject.start() }
            verify(exactly = 1) { mediaCodec.release() }
        } finally {
            continueStart.countDown()
            startExecutor.shutdownNow()
            releaseExecutor.shutdownNow()
        }
    }

    @Test
    fun `deterministic release invokes subclass cleanup hook`() = runTest {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.attachCallback()
        subject.releaseAndJoin()

        assertEquals(1, subject.releaseHookCount)
        verify(exactly = 1) { mediaCodec.release() }
    }

    private fun assertReleaseWaitsForCallback(
        enteredCallback: CountDownLatch,
        continueCallback: CountDownLatch,
        subject: TestCodec,
        invokeCallback: (MediaCodec.Callback, MediaCodec) -> Unit,
    ) {
        val callbackExecutor = Executors.newSingleThreadExecutor()
        val releaseExecutor = Executors.newSingleThreadExecutor()
        try {
            val callbackSlot = slot<MediaCodec.Callback>()
            every { subject.mediaCodec.setCallback(capture(callbackSlot)) } just Runs
            subject.attachCallback()
            val callbackFuture = callbackExecutor.submit {
                invokeCallback(callbackSlot.captured, subject.mediaCodec)
            }
            assertTrue(enteredCallback.await(2, TimeUnit.SECONDS), "Callback did not enter")

            val releaseFuture = releaseExecutor.submit { runBlocking { subject.releaseAndJoin() } }
            assertFailsWith<TimeoutException> { releaseFuture.get(100, TimeUnit.MILLISECONDS) }

            continueCallback.countDown()
            callbackFuture.get(2, TimeUnit.SECONDS)
            releaseFuture.get(2, TimeUnit.SECONDS)
            verify(exactly = 1) { subject.mediaCodec.release() }
        } finally {
            continueCallback.countDown()
            callbackExecutor.shutdownNow()
            releaseExecutor.shutdownNow()
        }
    }

    @Suppress("LongParameterList")
    private class TestCodec(
        val mediaCodec: MediaCodec,
        private val enteredInput: CountDownLatch? = null,
        private val continueInput: CountDownLatch? = null,
        private val enteredStart: CountDownLatch? = null,
        private val continueStart: CountDownLatch? = null,
        private val enteredFormat: CountDownLatch? = null,
        private val continueFormat: CountDownLatch? = null,
        private val enteredError: CountDownLatch? = null,
        private val continueError: CountDownLatch? = null,
        private val failInput: Boolean = false,
        private val failError: Boolean = false,
        private val failFormat: Boolean = false,
        private val inputFailure: Exception? = null,
        private val outputFailure: Exception? = null,
        private val formatFailure: Exception? = null,
        private val errorHookFailure: Exception? = null,
        private val ownerFailure: Exception? = null,
        private val startHookFailure: Throwable? = null,
        private val probeLockOnFailure: Boolean = false,
        private val inputSize: Int = 0,
        private val inputPtsUs: Long = 0,
        private val replacementCodec: MediaCodec? = null,
    ) : BaseMediaCodecAsynchronous(
        codecName = MediaFormat.MIMETYPE_AUDIO_AAC,
        sampleRate = 8_000,
        channelCount = 1
    ) {
        var nextCodec: MediaCodec? = replacementCodec
        var errorCount: Int = 0
            private set
        var releaseHookCount: Int = 0
            private set
        var outputCount: Int = 0
            private set
        var endCount: Int = 0
            private set
        var failureCount: Int = 0
            private set
        var lastFailure: Throwable? = null
            private set

        /** Set by [notifyCodecFailure] when [probeLockOnFailure] is on. */
        var lockFreeDuringFailure: Boolean? = null
            private set

        fun attachCallback(initialFormat: MediaFormat? = null) {
            start()
            initialFormat?.let { format = it }
        }

        fun currentFormat(): MediaFormat = format

        override fun setFormatOptions(format: MediaFormat) = Unit

        override fun createMediaFormat() {
            format = mockk(relaxed = true)
        }

        override fun createCodec() {
            codec = if (created) requireNotNull(nextCodec) else mediaCodec
            created = true
            enteredStart?.countDown()
            continueStart?.let {
                assertTrue(it.await(2, TimeUnit.SECONDS), "Timed out waiting to continue start()")
            }
            setMediaCodecOptions(codec)
        }

        private var created = false

        override fun onCodecStarted() {
            startHookFailure?.let { throw it }
        }

        override fun onInputData(inBuf: ByteBuffer): Int {
            inputFailure?.let { throw it }
            if (failInput) error("input failed")
            enteredInput?.countDown()
            continueInput?.let {
                assertTrue(it.await(2, TimeUnit.SECONDS), "Timed out waiting to continue")
            }
            return inputSize
        }

        override fun onOutputData(
            outBuf: ByteBuffer,
            info: MediaCodec.BufferInfo,
            isConfig: Boolean,
            isKeyFrame: Boolean,
        ) {
            outputFailure?.let { throw it }
            outputCount++
        }

        override fun computePresentationTimeUs(): Long = inputPtsUs

        override fun notifyCodecFailure(error: Throwable) {
            failureCount++
            lastFailure = error
            if (probeLockOnFailure) lockFreeDuringFailure = probeCodecLockIsFree()
            ownerFailure?.let { throw it }
        }

        /** `true` when another thread can take the codec operation lock right now. */
        private fun probeCodecLockIsFree(): Boolean {
            val probe = Executors.newSingleThreadExecutor()
            return try {
                val acquired = probe.submit { withCodecOperationLock { } }
                runCatching { acquired.get(500, TimeUnit.MILLISECONDS) }.isSuccess
            } finally {
                probe.shutdownNow()
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            enteredError?.countDown()
            continueError?.let {
                assertTrue(it.await(2, TimeUnit.SECONDS), "Timed out waiting for error callback")
            }
            errorCount++
            errorHookFailure?.let { throw it }
            if (failError) error("error hook failed")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            enteredFormat?.countDown()
            continueFormat?.let {
                assertTrue(it.await(2, TimeUnit.SECONDS), "Timed out waiting for format callback")
            }
            formatFailure?.let { throw it }
            if (failFormat) error("format hook failed")
        }

        override fun onCodecReleased() {
            releaseHookCount++
        }

        override fun onEndOfStream() {
            endCount++
        }
    }
}
