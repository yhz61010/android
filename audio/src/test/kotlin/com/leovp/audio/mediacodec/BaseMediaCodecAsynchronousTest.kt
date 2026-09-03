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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BaseMediaCodecAsynchronousTest {
    @Suppress("DEPRECATION")
    @Test
    fun `late input callback is ignored after release begins`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.attachCallback()
        subject.release()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 1)

        verify(exactly = 0) { mediaCodec.getInputBuffer(any()) }
        verify(exactly = 0) { mediaCodec.queueInputBuffer(any(), any(), any(), any(), any()) }
    }

    @Suppress("DEPRECATION")
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

            val releaseFuture = releaseExecutor.submit { subject.release() }
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
    fun `late callbacks are ignored after terminal release`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        val initialFormat = mockk<MediaFormat>()
        val changedFormat = mockk<MediaFormat>()
        val codecException = mockk<MediaCodec.CodecException>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.attachCallback(initialFormat)
        subject.release()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 1)
        callbackSlot.captured.onOutputFormatChanged(mediaCodec, changedFormat)
        callbackSlot.captured.onError(mediaCodec, codecException)

        verify(exactly = 0) { mediaCodec.getInputBuffer(any()) }
        assertSame(initialFormat, subject.currentFormat())
        assertEquals(0, subject.errorCount)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `codec session can only be started once`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.start()
        val error = assertFailsWith<IllegalStateException> { subject.start() }
        subject.release()

        assertEquals("A BaseMediaCodec instance can only be started once", error.message)
        verify(exactly = 1) { mediaCodec.start() }
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `start failure releases partially initialized codec and is terminal`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.start() } throws IllegalStateException("start failed")
        val subject = TestCodec(mediaCodec)

        val startError = assertFailsWith<IllegalStateException> { subject.start() }
        val restartError = assertFailsWith<IllegalStateException> { subject.start() }
        subject.release()

        assertEquals("start failed", startError.message)
        assertEquals("A BaseMediaCodec instance can only be started once", restartError.message)
        assertEquals(1, subject.releaseHookCount)
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `release before start makes the one-shot session terminal`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val subject = TestCodec(mediaCodec)

        subject.release()
        val error = assertFailsWith<IllegalStateException> { subject.start() }

        assertEquals("A BaseMediaCodec instance can only be started once", error.message)
        assertEquals(1, subject.releaseHookCount)
        verify(exactly = 0) { mediaCodec.start() }
        verify(exactly = 0) { mediaCodec.release() }
    }

    @Suppress("DEPRECATION")
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

            val releaseFuture = releaseExecutor.submit { subject.release() }
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

    private class TestCodec(
        private val mediaCodec: MediaCodec,
        private val enteredInput: CountDownLatch? = null,
        private val continueInput: CountDownLatch? = null,
        private val enteredStart: CountDownLatch? = null,
        private val continueStart: CountDownLatch? = null,
    ) : BaseMediaCodecAsynchronous(
        codecName = MediaFormat.MIMETYPE_AUDIO_AAC,
        sampleRate = 8_000,
        channelCount = 1
    ) {
        var errorCount: Int = 0
            private set
        var releaseHookCount: Int = 0
            private set

        fun attachCallback(initialFormat: MediaFormat? = null) {
            codec = mediaCodec
            initialFormat?.let { format = it }
            setMediaCodecOptions(mediaCodec)
        }

        fun currentFormat(): MediaFormat = format

        override fun setFormatOptions(format: MediaFormat) = Unit

        override fun createMediaFormat() {
            format = mockk(relaxed = true)
        }

        override fun createCodec() {
            codec = mediaCodec
            enteredStart?.countDown()
            continueStart?.let {
                assertTrue(it.await(2, TimeUnit.SECONDS), "Timed out waiting to continue start()")
            }
            setMediaCodecOptions(codec)
        }

        override fun onInputData(inBuf: ByteBuffer): Int {
            enteredInput?.countDown()
            continueInput?.let {
                assertTrue(it.await(2, TimeUnit.SECONDS), "Timed out waiting to continue")
            }
            return 0
        }

        override fun onOutputData(
            outBuf: ByteBuffer,
            info: MediaCodec.BufferInfo,
            isConfig: Boolean,
            isKeyFrame: Boolean,
        ) = Unit

        override fun computePresentationTimeUs(): Long = 0

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            errorCount++
        }

        override fun onCodecReleased() {
            releaseHookCount++
        }
    }
}
