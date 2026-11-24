package com.leovp.camera2live.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import com.leovp.androidbase.utils.media.CodecUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

abstract class CameraAvcEncoderCallbackThreadTestBase {
    protected val codec = mockk<MediaCodec>(relaxed = true)
    protected val events = CopyOnWriteArrayList<String>()
    protected val callbackThreads = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        Class.forName(MediaCodec::class.java.name, true, MediaCodec::class.java.classLoader)
        Class.forName(MediaFormat::class.java.name, true, MediaFormat::class.java.classLoader)
        mockkStatic(MediaCodec::class, MediaFormat::class)
        mockkObject(CodecUtil)
        every { CodecUtil.getSupportedProfileLevelsForEncoder(any()) } returns emptyArray()
        every { MediaFormat.createVideoFormat(any(), any(), any()) } returns mockk(relaxed = true)
        every { MediaCodec.createEncoderByType(any()) } answers {
            callbackThreads += "create:${Thread.currentThread().name}"
            codec
        }
        every { codec.setCallback(any()) } answers {
            events += "callback"
            callbackThreads += "callback:${Thread.currentThread().name}"
        }
        every { codec.setCallback(any(), any()) } answers { events += "callback" }
        every { codec.configure(any(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) } answers {
            events += "configure"
        }
    }

    @After
    fun tearDown() = unmockkAll()

    protected fun createEncoder(): CameraAvcEncoder = CameraAvcEncoder(320, 240, 256_000, 20)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraAvcEncoderApi28CallbackThreadTest : CameraAvcEncoderCallbackThreadTestBase() {
    @Test
    fun `handler callback is installed before configure`() {
        val encoder = createEncoder()
        try {
            assertEquals(listOf("callback", "configure"), events)
            verify(exactly = 1) { codec.setCallback(any(), any()) }
        } finally {
            encoder.release()
        }
    }

    @Test
    fun `callback setup failure releases the created codec`() {
        every { codec.setCallback(any(), any()) } throws IllegalStateException("callback failed")

        runCatching { createEncoder() }

        verify(exactly = 1) { codec.release() }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraAvcEncoderApi22CallbackThreadTest : CameraAvcEncoderCallbackThreadTestBase() {
    @Test
    fun `caller timeout after the worker final check still releases the unclaimed codec`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        val handoffEntered = CountDownLatch(1)
        val allowHandoff = CountDownLatch(1)
        val released = CountDownLatch(1)
        val releaseCount = AtomicInteger()
        val workerThread = AtomicReference<Thread?>()
        val releaseThread = AtomicReference<Thread?>()
        every { codec.setCallback(any()) } answers { workerThread.set(Thread.currentThread()) }
        every { codec.release() } answers {
            releaseThread.set(Thread.currentThread())
            releaseCount.incrementAndGet()
            released.countDown()
        }
        mockkConstructor(SynchronousQueue::class)
        every {
            anyConstructed<SynchronousQueue<Result<MediaCodec>>>().offer(any(), any(), any())
        } answers {
            handoffEntered.countDown()
            assertTrue(allowHandoff.await(6, TimeUnit.SECONDS))
            // The real zero-capacity queue must reject delivery after its receiver has gone.
            callOriginal()
        }
        val failure = AtomicReference<Throwable?>()
        val initialized = AtomicReference<CameraAvcEncoder?>()
        val caller = Thread {
            failure.set(
                runCatching { createEncoder().also { initialized.set(it) } }.exceptionOrNull()
            )
        }
        caller.start()
        try {
            assertTrue(handoffEntered.await(3, TimeUnit.SECONDS))
            caller.join(4_000)
            assertFalse(caller.isAlive)
            assertTrue(failure.get() is TimeoutException)
            assertEquals(0, releaseCount.get())
        } finally {
            allowHandoff.countDown()
            caller.join(4_000)
            try {
                assertTrue(released.await(4, TimeUnit.SECONDS))
            } finally {
                initialized.get()?.release()
                unmockkConstructor(SynchronousQueue::class)
            }
        }
        assertEquals(1, releaseCount.get())
        assertEquals(workerThread.get(), releaseThread.get())
    }

    @Test
    fun `interrupting setup never releases a codec still inside setCallback`() {
        verifyAbandonedCallbackRelease(interruptCaller = true)
    }

    @Test
    fun `timed out setup never releases a codec still inside setCallback`() {
        verifyAbandonedCallbackRelease(interruptCaller = false)
    }

    private fun verifyAbandonedCallbackRelease(interruptCaller: Boolean) {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        val callbackEntered = CountDownLatch(1)
        val allowCallbackReturn = CountDownLatch(1)
        val released = CountDownLatch(1)
        val releaseCount = AtomicInteger()
        val workerThread = AtomicReference<Thread?>()
        val releaseThread = AtomicReference<Thread?>()
        every { codec.setCallback(any()) } answers {
            workerThread.set(Thread.currentThread())
            callbackEntered.countDown()
            var waiting = true
            while (waiting) {
                try {
                    allowCallbackReturn.await()
                    waiting = false
                } catch (_: InterruptedException) {
                    // Model a native call that does not stop when Java interrupts its worker.
                }
            }
        }
        every { codec.release() } answers {
            releaseThread.set(Thread.currentThread())
            releaseCount.incrementAndGet()
            released.countDown()
        }
        val failure = AtomicReference<Throwable?>()
        val caller = Thread {
            failure.set(runCatching { createEncoder() }.exceptionOrNull())
            Thread.interrupted()
        }
        caller.start()
        try {
            assertTrue(callbackEntered.await(3, TimeUnit.SECONDS))
            if (interruptCaller) caller.interrupt()
            caller.join(4_000)
            assertFalse("the caller must be able to abandon initialization", caller.isAlive)
            assertEquals("the worker still owns the in-flight codec", 0, releaseCount.get())
        } finally {
            allowCallbackReturn.countDown()
            caller.join(4_000)
        }
        assertTrue(failure.get() != null)
        assertTrue(released.await(4, TimeUnit.SECONDS))
        assertEquals(1, releaseCount.get())
        assertEquals(workerThread.get(), releaseThread.get())
    }

    @Test
    fun `codec creation and default callback share the dedicated thread before configure`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        val encoder = createEncoder()
        try {
            assertEquals(listOf("callback", "configure"), events)
            val createThread = callbackThreads.single {
                it.startsWith("create:")
            }.substringAfter(':')
            val callbackThread = callbackThreads.single {
                it.startsWith("callback:")
            }.substringAfter(':')
            assertEquals(createThread, callbackThread)
            assertTrue(createThread.contains("CameraEncoder-callback"))
            verify(exactly = 1) { codec.setCallback(any()) }
        } finally {
            encoder.release()
        }
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
            failure.set(runCatching { createEncoder() }.exceptionOrNull())
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
        // Boundary guard, not a red-before-green regression test: the stub throws on the encoder
        // callback thread, so the caller sees ExecutionException and takes the pre-existing
        // catch (Throwable) branch, which already unwrapped the cause. It therefore passes
        // against the code before the InterruptedException branch existed. Its job is to keep
        // that branch from being widened to worker-side failures, which would set the caller's
        // interrupt flag for an interrupt that never targeted the caller.
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        every { MediaCodec.createEncoderByType(any()) } throws
            InterruptedException("worker interrupted")

        val failure = runCatching { createEncoder() }.exceptionOrNull()
        val callerInterrupted = Thread.interrupted()

        assertTrue(failure is InterruptedException)
        assertFalse(callerInterrupted)
    }
}
