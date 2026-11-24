package com.leovp.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.AudioEncoderManager
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.encoderWrapper.OpusEncoderWrapper
import com.leovp.audio.mediacodec.BaseMediaCodec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MicRecorderEncoderFailureTest {
    private val codec = mockk<MediaCodec>(relaxed = true)
    private val codecCallback = slot<MediaCodec.Callback>()
    private val reading = CountDownLatch(1)
    private val unblockRead = CountDownLatch(1)
    private val stopped = CountDownLatch(1)
    private val notifications = AtomicInteger()
    private var stopResult: Boolean? = null
    private var recorder: MicRecorder? = null

    @Before
    fun setUp() {
        listOf(AudioRecord::class.java, MediaCodec::class.java, MediaFormat::class.java).forEach {
            Class.forName(it.name, true, it.classLoader)
        }
        mockkStatic(AudioRecord::class, MediaCodec::class, MediaFormat::class)
        mockkConstructor(AudioRecord::class)
        every { AudioRecord.getMinBufferSize(any(), any(), any()) } returns 1024
        every { MediaFormat.createAudioFormat(any(), any(), any()) } returns mockk(relaxed = true)
        every { MediaCodec.createEncoderByType(any()) } returns codec
        every { codec.setCallback(capture(codecCallback)) } returns Unit
        every { anyConstructed<AudioRecord>().state } returns AudioRecord.STATE_INITIALIZED
        every { anyConstructed<AudioRecord>().recordingState } returns
            AudioRecord.RECORDSTATE_RECORDING
        every { anyConstructed<AudioRecord>().startRecording() } returns Unit
        every { anyConstructed<AudioRecord>().read(any<ShortArray>(), any(), any()) } answers {
            reading.countDown()
            check(unblockRead.await(3, TimeUnit.SECONDS))
            AudioRecord.ERROR_INVALID_OPERATION
        }
        every { anyConstructed<AudioRecord>().stop() } answers { unblockRead.countDown() }
        every { anyConstructed<AudioRecord>().release() } returns Unit
    }

    @After
    fun tearDown() {
        unblockRead.countDown()
        try {
            runBlocking { withTimeout(3_000) { recorder?.stopRecordAndJoin() } }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `AAC output write failure stops capture and releases the encoder`() {
        verifyOutputFailure(AudioType.AAC)
    }

    @Test
    fun `OPUS output write failure stops capture and releases the encoder`() {
        verifyOutputFailure(AudioType.OPUS)
    }

    @Test
    fun `encoder failure before capture starts still releases the recorder`() {
        recorder = createRecorder(AudioType.OPUS)

        deliverOutputFailure(AudioType.OPUS)

        assertTrue("Encoder failure never reached the recorder", stopped.await(2, TimeUnit.SECONDS))
        assertFalse(requireNotNull(stopResult))
        assertEquals(1, notifications.get())
        verify(exactly = 0) { anyConstructed<AudioRecord>().startRecording() }
        verify(exactly = 1) { anyConstructed<AudioRecord>().release() }
        verify(exactly = 1) { codec.release() }
    }

    @Test
    fun `PCM output cancellation still stops and releases capture`() {
        every { anyConstructed<AudioRecord>().read(any<ShortArray>(), any(), any()) } returns 4
        recorder = createRecorder(AudioType.PCM, CancellationException("Output cancelled"))

        recorder?.startRecord()

        assertTrue("Cancelled output left capture running", stopped.await(2, TimeUnit.SECONDS))
        assertFalse(requireNotNull(stopResult))
        assertEquals(1, notifications.get())
        verify(exactly = 1) { anyConstructed<AudioRecord>().stop() }
        verify(exactly = 1) { anyConstructed<AudioRecord>().release() }
    }

    @Test
    fun `explicit stop cancellation is not reported as a recording failure`() {
        every { anyConstructed<AudioRecord>().read(any<ShortArray>(), any(), any()) } answers {
            reading.countDown()
            check(unblockRead.await(3, TimeUnit.SECONDS))
            throw CancellationException("Capture stopped")
        }
        val subject = createRecorder(AudioType.PCM).also { recorder = it }
        subject.startRecord()
        assertTrue(reading.await(2, TimeUnit.SECONDS))

        runBlocking { withTimeout(2_000) { subject.stopRecordAndJoin() } }

        assertTrue(stopped.await(2, TimeUnit.SECONDS))
        assertTrue(requireNotNull(stopResult))
        assertEquals(1, notifications.get())
        verify(exactly = 1) { anyConstructed<AudioRecord>().stop() }
        verify(exactly = 1) { anyConstructed<AudioRecord>().release() }
    }

    @Test
    fun `encoder failure racing an explicit stop reports failure and releases once`() {
        val subject = createRecorder(AudioType.OPUS).also { recorder = it }
        subject.startRecord()
        assertTrue(reading.await(2, TimeUnit.SECONDS))
        val stopping = CountDownLatch(1)
        val continueStop = CountDownLatch(1)
        every { anyConstructed<AudioRecord>().stop() } answers {
            stopping.countDown()
            check(continueStop.await(3, TimeUnit.SECONDS))
            unblockRead.countDown()
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val stop = executor.submit { runBlocking { subject.stopRecordAndJoin() } }
            assertTrue(stopping.await(2, TimeUnit.SECONDS))

            deliverOutputFailure(AudioType.OPUS)
            continueStop.countDown()
            stop.get(2, TimeUnit.SECONDS)

            assertTrue(stopped.await(2, TimeUnit.SECONDS))
            assertFalse(requireNotNull(stopResult))
            assertEquals(1, notifications.get())
            verify(exactly = 1) { anyConstructed<AudioRecord>().stop() }
            verify(exactly = 1) { anyConstructed<AudioRecord>().release() }
            verify(exactly = 1) { codec.release() }
        } finally {
            continueStop.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `stop observes codec failure even while owner notification is delayed`() {
        val failureSelected = CountDownLatch(1)
        val continueNotification = CountDownLatch(1)
        mockkObject(AudioEncoderManager)
        every { AudioEncoderManager.getWrapper(any(), any(), any(), any()) } answers {
            val owner = arg<(Throwable) -> Unit>(3)
            OpusEncoderWrapper(secondArg(), thirdArg()) { error ->
                failureSelected.countDown()
                check(continueNotification.await(3, TimeUnit.SECONDS))
                owner(error)
            }
        }
        val subject = createRecorder(AudioType.OPUS).also { recorder = it }
        subject.startRecord()
        assertTrue(reading.await(2, TimeUnit.SECONDS))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val failure = executor.submit { deliverOutputFailure(AudioType.OPUS) }
            assertTrue(failureSelected.await(2, TimeUnit.SECONDS))

            runBlocking { withTimeout(2_000) { subject.stopRecordAndJoin() } }

            assertTrue(stopped.await(2, TimeUnit.SECONDS))
            assertFalse(
                "A selected codec failure must not report success",
                requireNotNull(stopResult)
            )
            assertEquals(1, notifications.get())
            verify(exactly = 1) { codec.release() }
            continueNotification.countDown()
            failure.get(2, TimeUnit.SECONDS)
        } finally {
            continueNotification.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `output failure stays failed when stop starts before the output buffer returns`() {
        val returningBuffer = CountDownLatch(1)
        val continueReturn = CountDownLatch(1)
        every { codec.releaseOutputBuffer(7, false) } answers {
            returningBuffer.countDown()
            check(continueReturn.await(5, TimeUnit.SECONDS))
        }
        val subject = createRecorder(AudioType.OPUS).also { recorder = it }
        subject.startRecord()
        assertTrue(reading.await(2, TimeUnit.SECONDS))
        val wrapper = MicRecorder::class.java.getDeclaredField("encodeWrapper")
            .apply { isAccessible = true }.get(subject)
        val encoder = OpusEncoderWrapper::class.java.getDeclaredField("encoder")
            .apply { isAccessible = true }.get(wrapper)
        val state = BaseMediaCodec::class.java.getDeclaredField("lifecycleState")
            .apply { isAccessible = true }.get(encoder) as AtomicReference<*>
        val executor = Executors.newFixedThreadPool(2)
        try {
            val callback = executor.submit { deliverOutputFailure(AudioType.OPUS) }
            assertTrue(returningBuffer.await(2, TimeUnit.SECONDS))
            val stop = executor.submit { runBlocking { subject.stopRecordAndJoin() } }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (state.get().toString() != "RELEASING" && System.nanoTime() < deadline) {
                Thread.sleep(1)
            }
            assertEquals("RELEASING", state.get().toString())
            continueReturn.countDown()
            callback.get(2, TimeUnit.SECONDS)
            stop.get(2, TimeUnit.SECONDS)

            assertTrue(stopped.await(2, TimeUnit.SECONDS))
            assertFalse(
                "An output failure must not become a successful stop",
                requireNotNull(stopResult)
            )
            assertEquals(1, notifications.get())
            verify(exactly = 1) { codec.release() }
            verify(exactly = 1) { anyConstructed<AudioRecord>().release() }
        } finally {
            continueReturn.countDown()
            executor.shutdownNow()
        }
    }

    private fun verifyOutputFailure(type: AudioType) {
        val subject = createRecorder(type).also { recorder = it }
        subject.startRecord()
        assertTrue(reading.await(2, TimeUnit.SECONDS))
        deliverOutputFailure(type)

        assertTrue("Output failure left capture running", stopped.await(2, TimeUnit.SECONDS))
        assertFalse(requireNotNull(stopResult))
        runBlocking { withTimeout(2_000) { subject.stopRecordAndJoin() } }
        deliverOutputFailure(type)
        assertEquals(1, notifications.get())
        verify(exactly = 1) { anyConstructed<AudioRecord>().stop() }
        verify(exactly = 1) { anyConstructed<AudioRecord>().release() }
        verify(exactly = 1) { codec.release() }
    }

    private fun deliverOutputFailure(type: AudioType) {
        val output = byteArrayOf(0x11, 0x88.toByte())
        every { codec.getOutputBuffer(7) } returns ByteBuffer.wrap(output)
        val info = MediaCodec.BufferInfo().apply {
            set(0, output.size, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
        }
        // OPUS config parsing needs a real 83-byte header; a normal payload needs no CSD.
        if (type == AudioType.OPUS) info.flags = 0

        codecCallback.captured.onOutputBufferAvailable(codec, 7, info)
    }

    private fun createRecorder(
        type: AudioType,
        outputFailure: Exception = IOException("Output storage is full")
    ) = MicRecorder(
        AudioEncoderInfo(48_000, 128_000, AudioFormat.CHANNEL_IN_MONO),
        object : MicRecorder.RecordCallback {
            override fun onRecording(
                data: ByteArray,
                isConfig: Boolean,
                isKeyFrame: Boolean
            ): Unit = throw outputFailure

            override fun onStop(stopResult: Boolean) {
                this@MicRecorderEncoderFailureTest.stopResult = stopResult
                notifications.incrementAndGet()
                stopped.countDown()
            }
        },
        type
    )
}
