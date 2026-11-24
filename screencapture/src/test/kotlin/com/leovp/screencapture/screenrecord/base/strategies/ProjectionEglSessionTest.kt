package com.leovp.screencapture.screenrecord.base.strategies

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.view.Surface
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProjectionEglSessionTest {
    private val listener = slot<SurfaceTexture.OnFrameAvailableListener>()
    private val frameHandler = slot<Handler>()
    private val failure = AtomicReference<Throwable?>()
    private lateinit var subject: ProjectionEglSession

    @Before
    fun setUp() {
        Class.forName(EGL14::class.java.name, true, EGL14::class.java.classLoader)
        Class.forName(EGLExt::class.java.name, true, EGLExt::class.java.classLoader)
        Class.forName(GLES20::class.java.name, true, GLES20::class.java.classLoader)
        mockkStatic(EGL14::class, EGLExt::class, GLES20::class)
        val config = mockk<EGLConfig>()
        every { EGL14.eglGetDisplay(any()) } returns mockk<EGLDisplay>()
        every { EGL14.eglInitialize(any(), any(), any(), any(), any()) } returns true
        every {
            EGL14.eglChooseConfig(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            arg<Array<EGLConfig?>>(3)[0] = config
            arg<IntArray>(6)[0] = 1
            true
        }
        every { EGL14.eglCreateContext(any(), any(), any(), any(), any()) } returns
            mockk<EGLContext>()
        every { EGL14.eglCreatePbufferSurface(any(), any(), any(), any()) } returns
            mockk<EGLSurface>()
        every { EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any()) } returns
            mockk<EGLSurface>()
        every { EGL14.eglMakeCurrent(any(), any(), any(), any()) } returns true
        every { EGL14.eglDestroySurface(any(), any()) } returns true
        every { EGL14.eglSwapBuffers(any(), any()) } returns true
        every { EGLExt.eglPresentationTimeANDROID(any(), any(), any()) } returns true
        every { GLES20.glGetShaderiv(any(), any(), any(), any()) } answers {
            arg<IntArray>(2)[0] = 1
        }
        every { GLES20.glGetProgramiv(any(), any(), any(), any()) } answers {
            arg<IntArray>(2)[0] = 1
        }
        every { GLES20.glCheckFramebufferStatus(any()) } returns GLES20.GL_FRAMEBUFFER_COMPLETE
        mockkConstructor(SurfaceTexture::class)
        every {
            anyConstructed<SurfaceTexture>().setOnFrameAvailableListener(
                capture(listener), capture(frameHandler)
            )
        } returns Unit
        every { anyConstructed<SurfaceTexture>().timestamp } returns 123_456L
        every { anyConstructed<SurfaceTexture>().updateTexImage() } returns Unit
        every { anyConstructed<SurfaceTexture>().getTransformMatrix(any()) } returns Unit
        every { anyConstructed<SurfaceTexture>().release() } returns Unit
        subject = ProjectionEglSession { failure.set(it) }
    }

    @After
    fun tearDown() {
        if (::subject.isInitialized) runCatching { subject.release() }
        unmockkAll()
    }

    @Test
    fun `repeated cleanup throwable preserves the failure and completes retirement`() {
        subject.initialize(mockk<Surface>(), 2, 2)
        val error = IllegalStateException("repeated native cleanup failure")
        every { EGL14.eglDestroySurface(any(), any()) } throws error
        var retired = false
        val reported = assertFailsWith<IllegalStateException> {
            subject.release(
                retireBorrowed = { throw error },
                onRetired = {
                    assertSame(error, it)
                    retired = true
                    throw error
                }
            )
        }
        assertSame(error, reported)
        assertTrue(retired)
        verify(exactly = 1) { EGL14.eglTerminate(any()) }
    }

    @Test
    fun `screenshot reads an FBO while subsequent frames still reach the encoder`() {
        subject.initialize(mockk<Surface>(), 2, 2)
        subject.setRecording(true)
        val received = AtomicReference<Bitmap>()
        val delivered = CountDownLatch(1)
        subject.screenshot(1, 2) {
            received.set(it)
            delivered.countDown()
        }
        every { GLES20.glReadPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            // GL bottom row blue, top row red. Each row has one RGBA pixel.
            arg<ByteBuffer>(6).put(byteArrayOf(0, 0, -1, -1, -1, 0, 0, -1)).rewind()
        }

        emitFrame()
        assertTrue(delivered.await(2, TimeUnit.SECONDS), "Screenshot was not delivered")
        emitFrame()
        subject.setRecording(true) // FIFO barrier after the second native frame callback.

        val bitmap = received.get()
        assertEquals(Color.RED, bitmap.getPixel(0, 0))
        assertEquals(Color.BLUE, bitmap.getPixel(0, 1))
        bitmap.recycle()
        assertNull(failure.get())
        verify(exactly = 2) { EGL14.eglSwapBuffers(any(), any()) }
        verify(exactly = 2) { EGLExt.eglPresentationTimeANDROID(any(), any(), 123_456L) }
        verify(exactly = 1) { GLES20.glReadPixels(0, 0, 1, 2, any(), any(), any()) }
    }

    @Test
    fun `rotation replaces the encoder window while retaining projection input texture`() {
        val firstEncoder = mockk<Surface>()
        val secondEncoder = mockk<Surface>()
        subject.initialize(firstEncoder, 320, 240)
        subject.setRecording(true)
        emitFrame()
        subject.detachOutput()
        subject.resize(240, 320)
        subject.replaceOutput(secondEncoder, 240, 320)
        subject.setRecording(true)
        emitFrame()
        subject.setRecording(true)

        assertNull(failure.get())
        verify(exactly = 1) { anyConstructed<SurfaceTexture>().setDefaultBufferSize(320, 240) }
        verify(exactly = 1) { anyConstructed<SurfaceTexture>().setDefaultBufferSize(240, 320) }
        verify(exactly = 0) { anyConstructed<SurfaceTexture>().release() }
        verify(exactly = 2) { EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { firstEncoder.release() }
        verify(exactly = 0) { secondEncoder.release() }
        verify(exactly = 2) { EGL14.eglSwapBuffers(any(), any()) }
    }

    @Test
    fun `stop cancels pending screenshots and release ignores late frame callbacks`() {
        subject.initialize(mockk<Surface>(), 2, 2)
        subject.setRecording(true)
        var delivered = false
        subject.screenshot(1, 1) { delivered = true }
        subject.setRecording(false)
        emitFrame()
        subject.setRecording(false)
        subject.release()
        listener.captured.onFrameAvailable(mockk())
        subject.release()

        assertFalse(delivered)
        assertNull(failure.get())
        verify(exactly = 0) { EGL14.eglSwapBuffers(any(), any()) }
        verify(exactly = 1) { anyConstructed<SurfaceTexture>().release() }
        verify(exactly = 1) { EGL14.eglTerminate(any()) }
    }

    @Test
    fun `native frame failure is reported to its owner`() {
        subject.initialize(mockk<Surface>(), 2, 2)
        subject.setRecording(true)
        val nativeFailure = IllegalStateException("texture abandoned")
        every { anyConstructed<SurfaceTexture>().updateTexImage() } throws nativeFailure

        emitFrame()
        subject.setRecording(false)

        assertSame(nativeFailure, failure.get())
        verify(exactly = 0) { EGL14.eglSwapBuffers(any(), any()) }
    }

    @Test
    fun `a failed screenshot and throwing receiver do not stop recording`() {
        subject.initialize(mockk<Surface>(), 2, 2)
        subject.setRecording(true)
        every { GLES20.glCheckFramebufferStatus(any()) } returns 0
        subject.screenshot(1, 1) { error("Must not receive an incomplete framebuffer") }
        emitFrame()
        subject.setRecording(true)
        every { GLES20.glCheckFramebufferStatus(any()) } returns GLES20.GL_FRAMEBUFFER_COMPLETE
        val rejected = AtomicReference<Bitmap>()
        subject.screenshot(1, 1) {
            rejected.set(it)
            error("Receiver failed")
        }
        subject.setRecording(true)
        emitFrame()
        subject.setRecording(true)

        assertTrue(rejected.get().isRecycled)
        assertNull(failure.get())
        verify(exactly = 2) { EGL14.eglSwapBuffers(any(), any()) }
        verify(exactly = 2) { GLES20.glDeleteFramebuffers(1, any(), 0) }
    }

    @Test
    fun `screenshot overload and huge dimensions are rejected while recording continues`() {
        subject.initialize(mockk<Surface>(), 2, 2)
        subject.setRecording(true)
        repeat(4) { subject.screenshot(1, 1) { bitmap -> bitmap.recycle() } }
        assertFailsWith<IllegalStateException> { subject.screenshot(1, 1) {} }
        assertFailsWith<IllegalArgumentException> {
            subject.screenshot(Int.MAX_VALUE, Int.MAX_VALUE) {}
        }

        emitFrame()
        subject.setRecording(true)
        emitFrame()
        subject.setRecording(true)

        assertNull(failure.get())
        verify(exactly = 2) { EGL14.eglSwapBuffers(any(), any()) }
        verify(exactly = 4) { GLES20.glReadPixels(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `release timeout retains borrowed resources until GL retirement completes`() =
        checkBlockedRetirement(interrupt = false)

    @Test
    fun `interrupted release retains ownership and restores caller interrupt`() =
        checkBlockedRetirement(interrupt = true)

    private fun checkBlockedRetirement(interrupt: Boolean) {
        val enteredGl = CountDownLatch(1)
        val continueGl = CountDownLatch(1)
        val retired = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()
        val encoder = mockk<MediaCodec>(relaxed = true)
        val surface = mockk<Surface>(relaxed = true)
        subject.initialize(surface, 2, 2)
        subject.setRecording(true)
        every { anyConstructed<SurfaceTexture>().updateTexImage() } answers {
            enteredGl.countDown()
            check(continueGl.await(10, TimeUnit.SECONDS))
        }
        every { EGL14.eglTerminate(any()) } answers {
            order.add("egl")
            true
        }
        every { surface.release() } answers {
            order.add("surface")
        }
        every { encoder.release() } answers {
            order.add("codec")
        }
        val callerFailure = AtomicReference<Throwable>()
        val callerInterrupted = AtomicBoolean()
        val caller = Thread {
            try {
                subject.release(
                    retireBorrowed = {
                        surface.release()
                        encoder.release()
                        retired.countDown()
                    }
                )
            } catch (error: Throwable) {
                callerFailure.set(error)
                callerInterrupted.set(Thread.currentThread().isInterrupted)
                Thread.interrupted()
            }
        }
        try {
            emitFrame()
            assertTrue(enteredGl.await(2, TimeUnit.SECONDS))
            caller.start()
            if (interrupt) {
                val closed = ReflectionHelpers.getField<AtomicBoolean>(subject, "closed")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                while (!closed.get() && System.nanoTime() < deadline) Thread.yield()
                assertTrue(closed.get())
                caller.interrupt()
            }
            caller.join(3_000)
            assertFalse(caller.isAlive)
            if (interrupt) {
                assertTrue(callerFailure.get() is InterruptedException)
                assertTrue(callerInterrupted.get())
            } else {
                assertTrue(callerFailure.get() is TimeoutException)
            }
            verify(exactly = 0) { surface.release() }
            verify(exactly = 0) { encoder.release() }
            assertFailsWith<IllegalStateException> { subject.setRecording(true) }
            continueGl.countDown()
            assertTrue(retired.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("egl", "surface", "codec"), order.toList())
            subject.release()
            verify(exactly = 1) { surface.release() }
            verify(exactly = 1) { encoder.release() }
        } finally {
            continueGl.countDown()
            caller.join(3_000)
        }
    }

    @Test
    fun `late initialization retains borrowed encoder without publishing a projection display`() =
        checkLateLifecycle(initializing = true)

    @Test
    fun `late rotation cannot publish a replacement encoder or second projection display`() =
        checkLateLifecycle(initializing = false)

    private fun checkLateLifecycle(initializing: Boolean) {
        Class.forName(MediaCodec::class.java.name, true, MediaCodec::class.java.classLoader)
        mockkStatic(MediaCodec::class)
        val codec = mockk<MediaCodec>(relaxed = true)
        val surface = mockk<Surface>(relaxed = true)
        every { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) } returns codec
        every { codec.createInputSurface() } returns surface
        val projection = mockk<MediaProjection>(relaxed = true)
        val strategy = ScreenRecordMediaCodecStrategy.Builder(
            2, 2, 160, projection, mockk<ScreenDataListener>(relaxed = true)
        ).build()
        val enteredGl = CountDownLatch(1)
        val continueGl = CountDownLatch(1)
        val retired = CountDownLatch(1)
        val nativeCalls = AtomicInteger()
        if (initializing) {
            every { EGL14.eglCreateWindowSurface(any(), any(), any(), any(), any()) } answers {
                enteredGl.countDown()
                check(continueGl.await(10, TimeUnit.SECONDS))
                mockk<EGLSurface>()
            }
        } else {
            strategy.onInit()
            strategy.onStart()
            every { EGL14.eglDestroySurface(any(), any()) } answers {
                if (nativeCalls.getAndIncrement() == 0) {
                    enteredGl.countDown()
                    check(continueGl.await(10, TimeUnit.SECONDS))
                }
                true
            }
        }
        every { codec.release() } answers { retired.countDown() }
        val callerFailure = AtomicReference<Throwable>()
        val caller = Thread {
            try {
                if (initializing) strategy.onInit() else strategy.changeOrientation()
            } catch (error: Throwable) {
                callerFailure.set(error)
            }
        }
        try {
            caller.start()
            assertTrue(enteredGl.await(2, TimeUnit.SECONDS))
            caller.join(5_000)
            assertFalse(caller.isAlive)
            assertTrue(callerFailure.get() is TimeoutException)
            verify(exactly = 0) { surface.release() }
            verify(exactly = 0) { codec.release() }
            assertNull(ReflectionHelpers.getField<MediaCodec?>(strategy, "h26xEncoder"))
            assertFailsWith<IllegalStateException> { strategy.onStart() }
            continueGl.countDown()
            assertTrue(retired.await(3, TimeUnit.SECONDS))
            verify(exactly = 1) { surface.release() }
            verify(exactly = 1) { codec.release() }
            verify(exactly = 1) { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) }
            verify(exactly = if (initializing) 0 else 1) {
                projection.createVirtualDisplay(
                    any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
        } finally {
            continueGl.countDown()
            caller.join(5_000)
            runCatching { strategy.onRelease() }
        }
    }

    @Test
    fun `GL errors from a failed screenshot cannot poison the next encoded frame`() {
        subject.initialize(mockk<Surface>(), 2, 2)
        subject.setRecording(true)
        val glError = AtomicInteger(GLES20.GL_NO_ERROR)
        every { GLES20.glGetError() } answers { glError.getAndSet(GLES20.GL_NO_ERROR) }
        every { GLES20.glCheckFramebufferStatus(any()) } answers {
            glError.set(GLES20.GL_INVALID_VALUE)
            0
        }
        subject.screenshot(1, 1) { error("Incomplete framebuffer cannot produce a bitmap") }
        emitFrame()
        subject.setRecording(true)
        emitFrame()
        subject.setRecording(true)

        assertNull(failure.get(), "A screenshot GL error must not fail the projection encoder")
        verify(exactly = 2) { EGL14.eglSwapBuffers(any(), any()) }
    }

    private fun emitFrame() {
        check(frameHandler.captured.post { listener.captured.onFrameAvailable(mockk()) })
    }
}
