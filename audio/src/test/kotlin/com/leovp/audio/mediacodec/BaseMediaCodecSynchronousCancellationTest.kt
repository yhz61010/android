package com.leovp.audio.mediacodec

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.aac.AacDecoder
import com.leovp.audio.base.iters.IDecodeCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class BaseMediaCodecSynchronousCancellationTest {
    @Test
    fun `active input hook cancellation selects terminal failure`() = checkActiveHook(Hook.INPUT)

    @Test
    fun `active output hook cancellation selects terminal failure`() = checkActiveHook(Hook.OUTPUT)

    @Test
    fun `active format hook cancellation selects terminal failure`() = checkActiveHook(Hook.FORMAT)

    @Test
    fun `AacDecoder output cancellation notifies owner and rejects input`() = runBlocking {
        val mediaCodec = createCodec(Hook.OUTPUT)
        val failure = CancellationException("Consumer cancelled its own output operation")
        val failures = AtomicInteger()
        var reportedFailure: Throwable? = null
        mockkStatic(MediaCodec::class, MediaFormat::class)
        try {
            every { MediaCodec.createDecoderByType(any()) } returns mediaCodec
            every { MediaFormat.createAudioFormat(any(), any(), any()) } returns
                mockk(relaxed = true)
            val subject = AacDecoder(
                8_000,
                1,
                AudioFormat.ENCODING_PCM_16BIT,
                byteArrayOf(0x15, 0x88.toByte()),
                object : IDecodeCallback {
                    override fun onDecoded(pcmData: ByteArray): Unit = throw failure
                },
                errorCallback = {
                    reportedFailure = it
                    failures.incrementAndGet()
                }
            )
            try {
                subject.start()
                val worker = BaseMediaCodec::class.java.getDeclaredField("codecJob")
                    .apply { isAccessible = true }.get(subject) as Job
                withTimeout(2_000) { worker.join() }

                assertEquals(1, failures.get(), "The stopped worker must notify its owner")
                assertSame(failure, reportedFailure)
                assertSame(failure, subject.terminalFailure)
                assertFalse(subject.decode(byteArrayOf(1, 2)), "A failed decoder must reject input")
            } finally {
                subject.releaseAndJoin()
            }
        } finally {
            unmockkStatic(MediaCodec::class, MediaFormat::class)
        }
        verify(exactly = 1) { mediaCodec.releaseOutputBuffer(1, false) }
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Test
    fun `external worker cancellation does not report a codec failure`() = runBlocking {
        val mediaCodec = createCodec(Hook.INPUT)
        val subject = CancellationCodec(mediaCodec, Hook.INPUT, blockInput = true)
        try {
            subject.start()
            assertTrue(subject.enteredInput.await(2, TimeUnit.SECONDS))
            val worker = subject.worker()
            worker.cancel()
            subject.continueInput.countDown()
            withTimeout(2_000) { worker.join() }

            assertTrue(worker.isCancelled)
            assertEquals(0, subject.failureCount.get())
            assertNull(subject.terminalFailure)
        } finally {
            subject.continueInput.countDown()
            subject.releaseAndJoin()
        }
        verify(exactly = 1) { mediaCodec.queueInputBuffer(0, 0, 0, 0, 0) }
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Test
    fun `hook cancellation during release does not report failure`() = runBlocking {
        val mediaCodec = createCodec(Hook.INPUT)
        val subject = CancellationCodec(mediaCodec, Hook.INPUT, blockInput = true)
        val releaseExecutor = Executors.newSingleThreadExecutor()
        try {
            subject.start()
            assertTrue(subject.enteredInput.await(2, TimeUnit.SECONDS))
            val worker = subject.worker()
            val release = releaseExecutor.submit { runBlocking { subject.releaseAndJoin() } }
            withTimeout(2_000) {
                while (!worker.isCancelled) delay(1)
            }
            subject.continueInput.countDown()
            release.get(2, TimeUnit.SECONDS)

            assertTrue(worker.isCancelled)
            assertEquals(0, subject.failureCount.get())
            assertNull(subject.terminalFailure)
        } finally {
            subject.continueInput.countDown()
            subject.releaseAndJoin()
            releaseExecutor.shutdownNow()
        }
        verify(exactly = 1) { mediaCodec.release() }
    }

    private fun checkActiveHook(hook: Hook) = runBlocking {
        val mediaCodec = createCodec(hook)
        val subject = CancellationCodec(mediaCodec, hook)
        try {
            subject.start()
            withTimeout(2_000) { subject.worker().join() }

            assertEquals(
                1,
                subject.failureCount.get(),
                "Active $hook cancellation must notify owner"
            )
            assertSame(subject.failure, subject.terminalFailure)
            assertFalse(subject.stillRunning(), "A stopped worker must not leave a running session")
            assertEquals(0, subject.endCount.get(), "Failure must not report clean EOS")
        } finally {
            subject.releaseAndJoin()
        }
        if (hook == Hook.INPUT) {
            verify(exactly = 1) { mediaCodec.queueInputBuffer(0, 0, 0, 0, 0) }
        }
        if (hook == Hook.OUTPUT) {
            verify(exactly = 1) { mediaCodec.releaseOutputBuffer(1, false) }
        }
        verify(exactly = 1) { mediaCodec.release() }
    }

    @Test
    fun `output failure survives release before buffer return`() = runBlocking {
        val mediaCodec = createCodec(Hook.OUTPUT)
        val failure = IOException("Output write failed")
        val subject = CancellationCodec(mediaCodec, Hook.OUTPUT, failure = failure)
        val returningBuffer = CountDownLatch(1)
        val continueReturn = CountDownLatch(1)
        every { mediaCodec.releaseOutputBuffer(1, false) } answers {
            returningBuffer.countDown()
            check(continueReturn.await(5, TimeUnit.SECONDS))
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            subject.start()
            assertTrue(returningBuffer.await(2, TimeUnit.SECONDS))
            val release = executor.submit { runBlocking { subject.releaseAndJoin() } }
            withTimeout(2_000) {
                while (!subject.worker().isCancelled) delay(1)
            }
            continueReturn.countDown()
            release.get(2, TimeUnit.SECONDS)

            assertSame(
                failure, subject.terminalFailure,
                "Release must retain an in-flight processing failure"
            )
            verify(exactly = 1) { mediaCodec.release() }
        } finally {
            continueReturn.countDown()
            subject.releaseAndJoin()
            executor.shutdownNow()
        }
    }

    private fun createCodec(hook: Hook): MediaCodec =
        mockk<MediaCodec>(relaxed = true).also { mediaCodec ->
            every { mediaCodec.dequeueInputBuffer(0) } returns 0
            every { mediaCodec.getInputBuffer(0) } returns ByteBuffer.allocate(16)
            every { mediaCodec.dequeueOutputBuffer(any(), any()) } answers {
                if (hook == Hook.FORMAT) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                } else {
                    firstArg<MediaCodec.BufferInfo>().apply {
                        offset = 0
                        size = 2
                        presentationTimeUs = 0
                        flags = 0
                    }
                    1
                }
            }
            every { mediaCodec.getOutputBuffer(1) } returns ByteBuffer.wrap(byteArrayOf(1, 2))
            every { mediaCodec.outputFormat } returns mockk(relaxed = true)
        }

    private enum class Hook { INPUT, OUTPUT, FORMAT }

    private class CancellationCodec(
        private val mediaCodec: MediaCodec,
        private val hook: Hook,
        private val blockInput: Boolean = false,
        val failure: Exception =
            CancellationException("Active $hook callback cancelled its own work"),
    ) : BaseMediaCodecSynchronous(MediaFormat.MIMETYPE_AUDIO_AAC, 8_000, 1) {
        val failureCount = AtomicInteger()
        val endCount = AtomicInteger()
        val enteredInput = CountDownLatch(1)
        val continueInput = CountDownLatch(1)

        fun worker(): Job = requireNotNull(codecJob)
        fun stillRunning(): Boolean = isRunning

        override fun createMediaFormat() = Unit
        override fun createCodec() {
            codec = mediaCodec
        }

        override fun setFormatOptions(format: MediaFormat) = Unit
        override fun onInputData(inBuf: ByteBuffer): Int {
            if (blockInput) {
                enteredInput.countDown()
                check(continueInput.await(2, TimeUnit.SECONDS)) {
                    "Timed out waiting to continue input"
                }
            }
            if (hook == Hook.INPUT) throw failure
            return 0
        }

        override fun onOutputData(
            outBuf: ByteBuffer,
            info: MediaCodec.BufferInfo,
            isConfig: Boolean,
            isKeyFrame: Boolean,
        ) {
            if (hook == Hook.OUTPUT) throw failure
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (hook == Hook.FORMAT) throw failure
        }

        override fun computePresentationTimeUs(): Long = 0
        override fun notifyCodecFailure(error: Throwable) {
            failureCount.incrementAndGet()
        }

        override fun onEndOfStream() {
            endCount.incrementAndGet()
        }
    }
}
