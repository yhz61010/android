package com.leovp.screencapture.screenrecord.base.strategies

import android.hardware.display.VirtualDisplay
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenRecordMediaCodecStrategyTest {
    private val codec = mockk<MediaCodec>(relaxed = true)
    private val surface = mockk<Surface>(relaxed = true)
    private val projection = mockk<MediaProjection>(relaxed = true)
    private val virtualDisplay = mockk<VirtualDisplay>(relaxed = true)
    private val listener = mockk<ScreenDataListener>(relaxed = true)

    /**
     * Every strategy [createStrategy] hands out, so [tearDown] can release them. Concurrent
     * because the interrupt case builds one on a thread of its own.
     */
    private val strategies = CopyOnWriteArrayList<ScreenRecordMediaCodecStrategy>()

    @Before
    fun setUp() {
        Class.forName(MediaCodec::class.java.name, true, MediaCodec::class.java.classLoader)
        Class.forName(MediaFormat::class.java.name, true, MediaFormat::class.java.classLoader)
        mockkStatic(MediaCodec::class, MediaFormat::class)
        mockkConstructor(ProjectionEglSession::class)
        every { anyConstructed<ProjectionEglSession>().initialize(any(), any(), any()) } returns
            mockk<Surface>(relaxed = true)
        every { anyConstructed<ProjectionEglSession>().release(any(), any()) } answers {
            var failure: Throwable? = null
            try {
                firstArg<() -> Unit>().invoke()
            } catch (error: Throwable) {
                failure = error
            }
            secondArg<(Throwable?) -> Unit>().invoke(failure)
            failure?.let { throw it }
        }
        every { anyConstructed<ProjectionEglSession>().detachOutput() } returns Unit
        every { anyConstructed<ProjectionEglSession>().resize(any(), any()) } returns Unit
        every { anyConstructed<ProjectionEglSession>().replaceOutput(any(), any(), any()) } returns
            Unit
        every { anyConstructed<ProjectionEglSession>().setRecording(any()) } returns Unit
        every { anyConstructed<ProjectionEglSession>().screenshot(any(), any(), any()) } returns
            Unit
        every { MediaFormat.createVideoFormat(any(), any(), any()) } returns mockk(relaxed = true)
        every { MediaCodec.createEncoderByType(any()) } returns codec
        every { codec.createInputSurface() } returns surface
    }

    @After
    fun tearDown() {
        // The case that needs this is `changeOrientation ...`: it ends with the strategy holding
        // a complete session -- encoder, input surface, VirtualDisplay and two HandlerThreads --
        // and has no release point of its own. Failed-init and interrupted-caller cases already
        // roll themselves back through releaseCodecCallbackThread(), which nulls the field, so
        // for them this hook is an idempotent no-op. Released before unmockkAll(), which the
        // release path still needs; each one is wrapped because a stub may be left throwing.
        strategies.forEach { runCatching { it.onRelease() } }
        strategies.clear()
        unmockkAll()
    }

    @Test
    fun `virtual display failure rolls back every acquired resource`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
        every {
            projection.createVirtualDisplay(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        } throws IllegalStateException("display failed")
        val strategy = createStrategy()

        val failure = runCatching { strategy.onInit() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        verify(exactly = 1) { projection.registerCallback(any(), any()) }
        verify(exactly = 1) { projection.unregisterCallback(any()) }
        verify(exactly = 1) { surface.release() }
        verify(exactly = 1) { codec.release() }
    }

    @Test
    fun `failed init keeps a projection callback owned by an earlier operation`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
        every {
            projection.createVirtualDisplay(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        } throws IllegalStateException("display failed")
        val strategy = createStrategy()
        ReflectionHelpers.setField(strategy, "projectionCallbackRegistered", true)

        runCatching { strategy.onInit() }

        verify(exactly = 0) { projection.unregisterCallback(any()) }
    }

    @Test
    fun `interrupted codec setup preserves the caller interrupt status`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        val creationStarted = CountDownLatch(1)
        val allowCreation = CountDownLatch(1)
        every { MediaCodec.createEncoderByType(any()) } answers {
            creationStarted.countDown()
            allowCreation.await()
            codec
        }
        val failure = AtomicReference<Throwable?>()
        val interrupted = AtomicBoolean(false)
        val caller = Thread {
            failure.set(runCatching { createStrategy().onInit() }.exceptionOrNull())
            interrupted.set(Thread.currentThread().isInterrupted)
            Thread.interrupted()
        }

        caller.start()
        try {
            assertTrue(creationStarted.await(2, TimeUnit.SECONDS))
            caller.interrupt()
            caller.join(2_000)
            // Asserted inside the try on purpose. The finally only needs to unwedge the worker;
            // interrupting the caller a second time there would set the very flag this case
            // asserts, for a reason unrelated to the production code, and a lost interrupt
            // restoration would then report green.
            assertFalse(caller.isAlive)
        } finally {
            allowCreation.countDown()
            caller.join(2_000)
        }

        assertTrue(failure.get() is InterruptedException)
        assertTrue(interrupted.get())
    }

    @Test
    fun `a worker-side InterruptedException is unwrapped without touching the caller flag`() {
        // Boundary guard, not a red-before-green regression test: the stub throws on the
        // scr-rec-codec thread, so the caller sees ExecutionException and takes the pre-existing
        // catch (Throwable) branch, which already unwrapped the cause. It therefore passes
        // against the code before the InterruptedException branch existed. Its job is to keep
        // that branch from being widened to worker-side failures, which would set the caller's
        // interrupt flag for an interrupt that never targeted the caller.
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        every { MediaCodec.createEncoderByType(any()) } throws
            InterruptedException("worker interrupted")

        val failure = runCatching { createStrategy().onInit() }.exceptionOrNull()
        val callerInterrupted = Thread.interrupted()

        assertTrue(failure is InterruptedException)
        assertFalse(callerInterrupted)
    }

    @Test
    fun `release continues after encoder stop failure`() {
        every {
            projection.createVirtualDisplay(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns virtualDisplay
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val stopFailure = IllegalStateException("stop failed")
        every { codec.stop() } throws stopFailure

        try {
            val failure = runCatching { strategy.onRelease() }.exceptionOrNull()

            assertSame(stopFailure, failure)
            verify(exactly = 1) { projection.stop() }
            verify(exactly = 1) { virtualDisplay.release() }
            verify(exactly = 1) { surface.release() }
            verify(exactly = 1) { codec.release() }
        } finally {
            every { codec.stop() } returns Unit
            runCatching { strategy.onRelease() }
        }
    }

    @Test
    fun `release reports projection callback unregister failure after cleanup`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
        every {
            projection.createVirtualDisplay(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns virtualDisplay
        val strategy = createStrategy()
        strategy.onInit()
        val unregisterFailure = IllegalStateException("unregister failed")
        every { projection.unregisterCallback(any()) } throws unregisterFailure

        val failure = runCatching { strategy.onRelease() }.exceptionOrNull()

        assertSame(unregisterFailure, failure)
        verify(exactly = 1) { virtualDisplay.release() }
        verify(exactly = 1) { surface.release() }
        verify(exactly = 1) { codec.release() }
    }

    @Test
    fun `failed projection callback unregister remains eligible for retry`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
        val strategy = createStrategy()
        strategy.onInit()
        every { projection.unregisterCallback(any()) } throws
            IllegalStateException("unregister failed")

        runCatching { strategy.onRelease() }
        every { projection.unregisterCallback(any()) } returns Unit
        strategy.onRelease()

        verify(exactly = 2) { projection.unregisterCallback(any()) }
    }

    @Test
    fun `release aggregates a second cleanup failure as suppressed`() {
        every {
            projection.createVirtualDisplay(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns virtualDisplay
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val stopFailure = IllegalStateException("stop failed")
        val displayFailure = IllegalStateException("display release failed")
        every { codec.stop() } throws stopFailure
        every { virtualDisplay.release() } throws displayFailure

        val failure = runCatching { strategy.onRelease() }.exceptionOrNull()

        // onStop() rethrows its own first failure into onRelease()'s collector, so the earlier
        // failure wins and the later one is attached instead of lost.
        assertSame(stopFailure, failure)
        assertSame(displayFailure, failure?.suppressed?.single())
        verify(exactly = 1) { surface.release() }
        verify(exactly = 1) { codec.release() }
    }

    @Test
    fun `changeOrientation resizes the existing virtual display`() {
        every {
            projection.createVirtualDisplay(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns virtualDisplay
        val strategy = createStrategy()
        strategy.onInit()

        strategy.changeOrientation()

        // The per-rotation half of the input-surface leak: changeOrientation() rebuilds the
        // encoder, so it has to release the surface the retired encoder handed out. The count is
        // one because the session it builds afterwards is not released here -- this stub hands
        // back the same mock on re-init, so a count of two would mean a double release.
        verify(exactly = 1) { surface.release() }
        verify(exactly = 0) { virtualDisplay.release() }
        verify(exactly = 1) { virtualDisplay.resize(240, 320, 160) }
        verify(exactly = 1) {
            projection.createVirtualDisplay(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `screenshot keeps the current recording and projection display`() {
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()

        strategy.takeScreenshot(160, 120) {}

        verify(exactly = 0) { codec.stop() }
        verify(exactly = 0) { projection.stop() }
        verify(exactly = 1) {
            projection.createVirtualDisplay(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `a stopped projection session cannot be initialized again`() {
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        strategy.onStop()

        assertTrue(runCatching { strategy.onInit() }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { strategy.onStart() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `late screenshot bitmap is recycled after stop`() {
        val screenshot = slot<(Bitmap) -> Unit>()
        every {
            anyConstructed<ProjectionEglSession>().screenshot(any(), any(), capture(screenshot))
        } returns Unit
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        var delivered = false
        strategy.takeScreenshot(null, null) { delivered = true }
        strategy.onStop()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        screenshot.captured(bitmap)

        assertTrue(bitmap.isRecycled)
        assertFalse(delivered)
    }

    @Test
    fun `screenshot result is delivered on the recording delivery thread`() {
        val screenshot = slot<(Bitmap) -> Unit>()
        every {
            anyConstructed<ProjectionEglSession>().screenshot(160, 120, capture(screenshot))
        } returns Unit
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val delivered = CountDownLatch(1)
        val threadName = AtomicReference<String>()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        strategy.takeScreenshot(160, 120) {
            assertSame(bitmap, it)
            threadName.set(Thread.currentThread().name)
            delivered.countDown()
        }

        screenshot.captured(bitmap)

        assertTrue(delivered.await(2, TimeUnit.SECONDS))
        assertEquals("scr-rec-send", threadName.get())
        assertFalse(bitmap.isRecycled)
        bitmap.recycle()
    }

    @Test
    fun `rotation retires every old encoder resource even when stop throws`() {
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val stopFailure = IllegalStateException("rotation stop failed")
        every { codec.stop() } throws stopFailure

        val failure = runCatching { strategy.changeOrientation() }.exceptionOrNull()

        assertSame(stopFailure, failure)
        verify(exactly = 1) { codec.release() }
        verify(exactly = 1) { surface.release() }
        verify(exactly = 1) { projection.stop() }
        verify(exactly = 1) { anyConstructed<ProjectionEglSession>().release(any(), any()) }
    }

    @Test
    fun `old encoder callback after rotation cannot send frames into the replacement session`() {
        val callbacks = mutableListOf<MediaCodec.Callback>()
        every { codec.setCallback(capture(callbacks), any()) } returns Unit
        val nextCodec = mockk<MediaCodec>(relaxed = true)
        every { MediaCodec.createEncoderByType(any()) } returnsMany listOf(codec, nextCodec)
        every { nextCodec.createInputSurface() } returns mockk(relaxed = true)
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        strategy.changeOrientation()

        callbacks.single().onOutputBufferAvailable(codec, 7, MediaCodec.BufferInfo())
        callbacks.single().onOutputFormatChanged(codec, mockk())

        assertSame(nextCodec, ReflectionHelpers.getField<MediaCodec?>(strategy, "h26xEncoder"))
        verify(exactly = 0) { codec.getOutputBuffer(any()) }
        verify(exactly = 0) { listener.onDataUpdate(any(), any(), any()) }
    }

    @Test
    fun `throwing screenshot receiver recycles bitmap without stopping recorder`() {
        val screenshot = slot<(Bitmap) -> Unit>()
        every {
            anyConstructed<ProjectionEglSession>().screenshot(any(), any(), capture(screenshot))
        } returns Unit
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val entered = CountDownLatch(1)
        strategy.takeScreenshot(null, null) {
            entered.countDown()
            error("Receiver failed")
        }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        screenshot.captured(bitmap)
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        val finished = CountDownLatch(1)
        val delivery = ReflectionHelpers.getField<Handler>(strategy, "videoDataSendHandler")
        assertTrue(delivery.post { finished.countDown() })
        assertTrue(finished.await(2, TimeUnit.SECONDS))

        assertTrue(bitmap.isRecycled)
        verify(exactly = 0) { codec.stop() }
        verify(exactly = 0) { codec.release() }
        verify(exactly = 0) { projection.stop() }
    }

    @Test
    fun `rotation waits for an active native callback before releasing its codec`() {
        val callback = slot<MediaCodec.Callback>()
        every { codec.setCallback(capture(callback), any()) } returns Unit
        val nextCodec = mockk<MediaCodec>(relaxed = true)
        every { nextCodec.createInputSurface() } returns mockk(relaxed = true)
        every { MediaCodec.createEncoderByType(any()) } returnsMany listOf(codec, nextCodec)
        val enteredOutput = CountDownLatch(1)
        val continueOutput = CountDownLatch(1)
        every { codec.getOutputBuffer(7) } answers {
            enteredOutput.countDown()
            check(continueOutput.await(3, TimeUnit.SECONDS))
            ByteBuffer.wrap(byteArrayOf(1, 2))
        }
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val info = MediaCodec.BufferInfo().apply {
            size = 2
            flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG
        }
        val nativeCallback = Thread { callback.captured.onOutputBufferAvailable(codec, 7, info) }
        val rotationFailure = AtomicReference<Throwable>()
        val rotation = Thread {
            runCatching { strategy.changeOrientation() }.exceptionOrNull()
                ?.let(rotationFailure::set)
        }
        try {
            nativeCallback.start()
            assertTrue(enteredOutput.await(2, TimeUnit.SECONDS))
            rotation.start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (ReflectionHelpers.getField<Any?>(strategy, "encoderSession") != null &&
                System.nanoTime() < deadline
            ) {
                Thread.yield()
            }
            assertSame(null, ReflectionHelpers.getField<Any?>(strategy, "encoderSession"))
            verify(exactly = 0) { codec.stop() }
            verify(exactly = 0) { codec.release() }
            continueOutput.countDown()
            nativeCallback.join(2_000)
            rotation.join(2_000)
            assertFalse(rotation.isAlive)
            assertSame(null, rotationFailure.get())
            assertSame(nextCodec, ReflectionHelpers.getField<MediaCodec?>(strategy, "h26xEncoder"))
            assertSame(null, strategy.vpsSpsPpsBuf)
            verify(exactly = 1) { codec.releaseOutputBuffer(7, false) }
            verify(exactly = 1) { codec.release() }
            verify(exactly = 0) { listener.onDataUpdate(any(), any(), any()) }
        } finally {
            continueOutput.countDown()
            nativeCallback.join(2_000)
            rotation.join(2_000)
        }
    }

    @Test
    fun `queued frames from retired encoder cannot enter the new recording delivery`() {
        val callback = slot<MediaCodec.Callback>()
        every { codec.setCallback(capture(callback), any()) } returns Unit
        every { codec.getOutputBuffer(7) } returns ByteBuffer.wrap(byteArrayOf(1, 2))
        val nextCodec = mockk<MediaCodec>(relaxed = true)
        every { nextCodec.createInputSurface() } returns mockk(relaxed = true)
        every { MediaCodec.createEncoderByType(any()) } returnsMany listOf(codec, nextCodec)
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val delivery = ReflectionHelpers.getField<Handler>(strategy, "videoDataSendHandler")
        val enteredDelivery = CountDownLatch(1)
        val continueDelivery = CountDownLatch(1)
        delivery.post {
            enteredDelivery.countDown()
            check(continueDelivery.await(3, TimeUnit.SECONDS))
        }
        try {
            assertTrue(enteredDelivery.await(2, TimeUnit.SECONDS))
            val info = MediaCodec.BufferInfo().apply {
                size = 2
                flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG
            }
            callback.captured.onOutputBufferAvailable(codec, 7, info)
            strategy.changeOrientation()
            continueDelivery.countDown()
            val drained = CountDownLatch(1)
            delivery.post { drained.countDown() }
            assertTrue(drained.await(2, TimeUnit.SECONDS))

            assertSame(null, strategy.vpsSpsPpsBuf)
            verify(exactly = 0) { listener.onDataUpdate(any(), any(), any()) }
        } finally {
            continueDelivery.countDown()
        }
    }

    @Test
    fun `API22 interrupted callback setup retains codec on its worker until native call exits`() =
        checkBlockedCallbackSetup(interrupt = true)

    @Test
    fun `API22 callback setup timeout cannot release codec while native call still runs`() =
        checkBlockedCallbackSetup(interrupt = false)

    private fun checkBlockedCallbackSetup(interrupt: Boolean) {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        val enteredSetup = CountDownLatch(1)
        val continueSetup = CountDownLatch(1)
        val nativeReleased = CountDownLatch(1)
        val setupThread = AtomicReference<Thread>()
        val releaseThread = AtomicReference<Thread>()
        every { codec.setCallback(any()) } answers {
            setupThread.set(Thread.currentThread())
            enteredSetup.countDown()
            // JNI may ignore Thread.interrupt(); cancellation does not prove native exit.
            while (true) {
                try {
                    check(continueSetup.await(10, TimeUnit.SECONDS))
                    break
                } catch (_: InterruptedException) {
                    // Model a native operation that cannot be preempted by Future.cancel(true).
                }
            }
        }
        every { codec.release() } answers {
            releaseThread.set(Thread.currentThread())
            nativeReleased.countDown()
        }
        val strategy = createStrategy()
        val failure = AtomicReference<Throwable>()
        val interrupted = AtomicBoolean()
        val caller = Thread {
            runCatching { strategy.onInit() }.exceptionOrNull()?.let(failure::set)
            interrupted.set(Thread.currentThread().isInterrupted)
            Thread.interrupted()
        }
        try {
            caller.start()
            assertTrue(enteredSetup.await(2, TimeUnit.SECONDS))
            if (interrupt) caller.interrupt()
            caller.join(4_000)
            assertFalse(caller.isAlive)
            if (interrupt) {
                assertTrue(failure.get() is InterruptedException)
                assertTrue(interrupted.get())
            } else {
                assertTrue(failure.get() is TimeoutException)
            }
            verify(exactly = 0) { codec.release() }
            continueSetup.countDown()
            assertTrue(nativeReleased.await(3, TimeUnit.SECONDS))
            assertSame(setupThread.get(), releaseThread.get())
            verify(exactly = 1) { codec.release() }
            verify(exactly = 0) { codec.createInputSurface() }
        } finally {
            continueSetup.countDown()
            caller.join(4_000)
        }
    }

    @Test
    fun `system projection stop releases an API22 recorder`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        val projectionCallback = slot<MediaProjection.Callback>()
        every { projection.registerCallback(capture(projectionCallback), any()) } returns Unit
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()

        assertTrue(projectionCallback.isCaptured)
        projectionCallback.captured.onStop()
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { codec.release() }
        verify(exactly = 1) { surface.release() }
        verify(exactly = 1) { projection.unregisterCallback(any()) }
        verify(exactly = 0) { listener.onError(any()) }
        assertTrue(runCatching { strategy.onStart() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `API22 caller leaving after final abandoned check cannot strand its unclaimed codec`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        mockkConstructor(SynchronousQueue::class)
        val reachedOffer = CountDownLatch(1)
        val continueOffer = CountDownLatch(1)
        val released = CountDownLatch(1)
        every {
            anyConstructed<SynchronousQueue<Result<MediaCodec>>>().offer(
                match { it.getOrNull() === codec }, any(), any()
            )
        } answers {
            reachedOffer.countDown()
            check(continueOffer.await(10, TimeUnit.SECONDS))
            callOriginal()
        }
        every { codec.release() } answers { released.countDown() }
        val strategy = createStrategy()
        val failure = AtomicReference<Throwable>()
        val caller = Thread {
            runCatching { strategy.onInit() }.exceptionOrNull()?.let(failure::set)
        }
        try {
            caller.start()
            assertTrue(reachedOffer.await(2, TimeUnit.SECONDS))
            caller.join(4_000)
            assertFalse(caller.isAlive)
            assertTrue(failure.get() is TimeoutException)
            verify(exactly = 0) { codec.release() }
            continueOffer.countDown()
            assertTrue(released.await(3, TimeUnit.SECONDS))
            verify(exactly = 1) { codec.release() }
            verify(exactly = 0) { codec.createInputSurface() }
        } finally {
            continueOffer.countDown()
            caller.join(4_000)
        }
    }

    @Test
    fun `asynchronous codec error releases resources before notifying owner once`() {
        val callback = slot<MediaCodec.Callback>()
        every { codec.setCallback(capture(callback), any()) } returns Unit
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val failure = nativeCodecFailure()
        var errors = 0
        every { listener.onError(failure) } answers {
            verify(exactly = 1) { codec.release() }
            verify(exactly = 1) { surface.release() }
            assertSame(null, ReflectionHelpers.getField<MediaCodec?>(strategy, "h26xEncoder"))
            strategy.onRelease() // Owner re-entry must not deadlock.
            errors++
        }

        callback.captured.onError(codec, failure)
        callback.captured.onError(codec, failure)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, errors)
        val screenshotFailure = runCatching {
            strategy.takeScreenshot(null, null) {}
        }.exceptionOrNull()
        assertTrue(screenshotFailure is IllegalStateException)
        verify(exactly = 1) { codec.release() }
    }

    @Test
    fun `asynchronous output processing failure selects terminal owner notification`() {
        val callback = slot<MediaCodec.Callback>()
        every { codec.setCallback(capture(callback), any()) } returns Unit
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val failure = IllegalStateException("Broken native output")
        every { codec.getOutputBuffer(7) } throws failure
        var errors = 0
        every { listener.onError(failure) } answers {
            verify(exactly = 1) { codec.release() }
            strategy.onRelease()
            errors++
        }

        callback.captured.onOutputBufferAvailable(codec, 7, MediaCodec.BufferInfo())
        callback.captured.onOutputBufferAvailable(codec, 8, MediaCodec.BufferInfo())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, errors)
        verify(exactly = 1) { codec.releaseOutputBuffer(7, false) }
        verify(exactly = 0) { codec.getOutputBuffer(8) }
        verify(exactly = 0) { listener.onDataUpdate(any(), any(), any()) }
    }

    @Test
    fun `asynchronous failure notification waits for deferred EGL retirement completion`() {
        val callback = slot<MediaCodec.Callback>()
        every { codec.setCallback(capture(callback), any()) } returns Unit
        val strategy = createStrategy()
        strategy.onInit()
        strategy.onStart()
        val retire = slot<() -> Unit>()
        val retired = slot<(Throwable?) -> Unit>()
        every {
            anyConstructed<ProjectionEglSession>().release(capture(retire), capture(retired))
        } throws TimeoutException("GL retirement is still pending")
        val failure = nativeCodecFailure()

        callback.captured.onError(codec, failure)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { listener.onError(any()) }
        verify(exactly = 0) { codec.release() }
        retire.captured()
        retired.captured(null)
        shadowOf(Looper.getMainLooper()).idle()
        verify(exactly = 1) { codec.release() }
        verify(exactly = 1) { listener.onError(failure) }
    }

    private fun nativeCodecFailure(): MediaCodec.CodecException =
        MediaCodec.CodecException::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
        ).apply { isAccessible = true }.newInstance(1100, 0, "Encoder failed")

    private fun createStrategy() = ScreenRecordMediaCodecStrategy.Builder(
        width = 320,
        height = 240,
        dpi = 160,
        mediaProjection = projection,
        screenDataListener = listener
    ).build().also { strategies += it }
}
