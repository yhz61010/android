package com.leovp.screencapture.screenrecord.base.strategies

import android.media.MediaCodec
import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class Screenshot2H26xStrategyTest {
    private val codec = mockk<MediaCodec>(relaxed = true)
    private val callback = slot<MediaCodec.Callback>()
    private val listener = mockk<ScreenDataListener>(relaxed = true)
    private lateinit var subject: Screenshot2H26xStrategy

    @BeforeTest
    fun setUp() {
        mockkStatic(MediaCodec::class, MediaFormat::class, EGL14::class)
        every { MediaFormat.createVideoFormat(any(), any(), any()) } returns mockk(relaxed = true)
        every { MediaCodec.createEncoderByType(any()) } returns codec
        every { codec.setCallback(capture(callback)) } returns Unit
        val display = mockk<EGLDisplay>()
        val config = mockk<EGLConfig>()
        every { EGL14.eglGetDisplay(any()) } returns display
        every { EGL14.eglInitialize(any(), any(), any(), any(), any()) } returns true
        every {
            EGL14.eglChooseConfig(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            arg<Array<EGLConfig?>>(3)[0] = config
            arg<IntArray>(6)[0] = 1
            true
        }
        every { EGL14.eglGetError() } returns EGL14.EGL_SUCCESS
        every { EGL14.eglCreateContext(any(), any(), any(), any(), any()) } returns
            mockk<EGLContext>()
        every { EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any()) } returns
            mockk<EGLSurface>()
        every { EGL14.eglMakeCurrent(any(), any(), any(), any()) } returns true
        subject = Screenshot2H26xStrategy.Builder(320, 240, 160, listener).build()
    }

    @AfterTest
    fun tearDown() {
        try {
            runBlocking { withTimeout(3_000) { subject.releaseAndJoin() } }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `interrupted initialization releases resources on the owner thread`() {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val owner = AtomicReference<Thread>()
        val releaseThread = AtomicReference<Thread>()
        every { codec.configure(any(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) } answers {
            owner.set(Thread.currentThread())
            entered.countDown()
            check(resume.await(3, TimeUnit.SECONDS))
        }
        every { codec.release() } answers { releaseThread.set(Thread.currentThread()) }
        val failure = AtomicReference<Throwable>()
        val interrupted = AtomicBoolean()
        val caller = Thread {
            try {
                subject.onInit()
            } catch (error: Throwable) {
                failure.set(error)
                interrupted.set(Thread.currentThread().isInterrupted)
            }
        }
        try {
            caller.start()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            caller.interrupt()
            caller.join(3_000)
            assertFalse(caller.isAlive)
            assertIs<InterruptedException>(failure.get())
            resume.countDown()
            assertTrue(executor().awaitTermination(3, TimeUnit.SECONDS))
            assertTrue(interrupted.get(), "The caller's interrupt status must be preserved")
            assertSame(owner.get(), releaseThread.get())
            assertNull(subject.h26xEncoder)
            verify(exactly = 1) { codec.release() }
        } finally {
            resume.countDown()
            caller.join(3_000)
        }
    }

    @Test
    fun `interrupted queued initialization does not create a codec`() {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        executor().execute {
            entered.countDown()
            check(resume.await(3, TimeUnit.SECONDS))
        }
        val failure = AtomicReference<Throwable>()
        val caller = Thread {
            Thread.currentThread().interrupt()
            try {
                subject.onInit()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            caller.start()
            caller.join(3_000)
            assertIs<InterruptedException>(failure.get())
            resume.countDown()
            assertTrue(executor().awaitTermination(3, TimeUnit.SECONDS))
            verify(exactly = 0) { MediaCodec.createEncoderByType(any()) }
        } finally {
            resume.countDown()
            caller.join(3_000)
        }
    }

    @Test
    fun `output failure without recording job releases and reports once`() {
        val failure = IllegalStateException("Output buffer failed")
        val reported = CountDownLatch(1)
        every { codec.getOutputBuffer(7) } throws failure
        every { listener.onError(failure) } answers { reported.countDown() }
        subject.onInit()
        subject.onStart()

        callback.captured.onOutputBufferAvailable(codec, 7, MediaCodec.BufferInfo())

        assertTrue(reported.await(3, TimeUnit.SECONDS), "Failure was not delivered")
        assertNull(subject.h26xEncoder)
        verify(exactly = 1) { codec.releaseOutputBuffer(7, false) }
        verify(exactly = 1) { codec.release() }
        callback.captured.onOutputBufferAvailable(codec, 8, MediaCodec.BufferInfo())
        verify(exactly = 0) { codec.getOutputBuffer(8) }
        verify(exactly = 1) { listener.onError(failure) }
    }

    private fun executor(): ExecutorService = subject.javaClass
        .getDeclaredField("recordingExecutor")
        .apply { isAccessible = true }
        .get(subject) as ExecutorService
}
