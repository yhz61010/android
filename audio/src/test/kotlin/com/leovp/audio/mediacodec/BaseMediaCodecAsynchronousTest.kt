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

    @Suppress("DEPRECATION")
    @Test
    fun `input callback failure returns dequeued buffer`() {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        every { mediaCodec.getInputBuffer(4) } returns ByteBuffer.allocate(16)
        val subject = TestCodec(mediaCodec, failInput = true)

        subject.attachCallback()
        callbackSlot.captured.onInputBufferAvailable(mediaCodec, 4)
        subject.release()

        verify(exactly = 1) { mediaCodec.queueInputBuffer(4, 0, 0, 0, 0) }
    }

    @Suppress("DEPRECATION")
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

    @Suppress("DEPRECATION")
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
        subject.release()

        assertEquals(1, subject.outputCount)
        assertEquals(1, subject.endCount)
        verify(exactly = 1) { mediaCodec.releaseOutputBuffer(3, false) }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `concurrent start admits only one caller`() {
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

        try {
            val firstStart = startExecutor.submit { subject.start() }
            assertTrue(enteredStart.await(2, TimeUnit.SECONDS), "First start did not enter")

            assertFailsWith<IllegalStateException> { subject.start() }
            continueStart.countDown()
            firstStart.get(2, TimeUnit.SECONDS)
            subject.release()

            verify(exactly = 1) { mediaCodec.start() }
            verify(exactly = 1) { mediaCodec.release() }
        } finally {
            continueStart.countDown()
            startExecutor.shutdownNow()
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `successful release remains exactly once across both release APIs`() = runTest {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.start()
        subject.release()
        subject.release()
        subject.releaseAndJoin()

        assertEquals(1, subject.releaseHookCount)
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Suppress("DEPRECATION")
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

    @Suppress("DEPRECATION")
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

    @Suppress("DEPRECATION")
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

            val releaseFuture = releaseExecutor.submit { subject.release() }
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
    ) : BaseMediaCodecAsynchronous(
        codecName = MediaFormat.MIMETYPE_AUDIO_AAC,
        sampleRate = 8_000,
        channelCount = 1
    ) {
        var errorCount: Int = 0
            private set
        var releaseHookCount: Int = 0
            private set
        var outputCount: Int = 0
            private set
        var endCount: Int = 0
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
            if (failInput) error("input failed")
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
        ) {
            outputCount++
        }

        override fun computePresentationTimeUs(): Long = 0

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            enteredError?.countDown()
            continueError?.let {
                assertTrue(it.await(2, TimeUnit.SECONDS), "Timed out waiting for error callback")
            }
            errorCount++
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            enteredFormat?.countDown()
            continueFormat?.let {
                assertTrue(it.await(2, TimeUnit.SECONDS), "Timed out waiting for format callback")
            }
        }

        override fun onCodecReleased() {
            releaseHookCount++
        }

        override fun onEndOfStream() {
            endCount++
        }
    }
}
