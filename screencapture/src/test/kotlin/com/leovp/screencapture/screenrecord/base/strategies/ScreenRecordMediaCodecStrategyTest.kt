package com.leovp.screencapture.screenrecord.base.strategies

import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.view.Surface
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenRecordMediaCodecStrategyTest {
    private val codec = mockk<MediaCodec>(relaxed = true)
    private val surface = mockk<Surface>(relaxed = true)
    private val projection = mockk<MediaProjection>(relaxed = true)
    private val virtualDisplay = mockk<VirtualDisplay>(relaxed = true)
    private val listener = mockk<ScreenDataListener>(relaxed = true)

    @Before
    fun setUp() {
        Class.forName(MediaCodec::class.java.name, true, MediaCodec::class.java.classLoader)
        Class.forName(MediaFormat::class.java.name, true, MediaFormat::class.java.classLoader)
        mockkStatic(MediaCodec::class, MediaFormat::class)
        every { MediaFormat.createVideoFormat(any(), any(), any()) } returns mockk(relaxed = true)
        every { MediaCodec.createEncoderByType(any()) } returns codec
        every { codec.createInputSurface() } returns surface
    }

    @After
    fun tearDown() = unmockkAll()

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
        } finally {
            allowCreation.countDown()
            caller.interrupt()
            caller.join(2_000)
        }

        assertTrue(!caller.isAlive)
        assertTrue(failure.get() is InterruptedException)
        assertTrue(interrupted.get())
    }

    @Test
    fun `worker interruption does not interrupt the caller`() {
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

    private fun createStrategy() = ScreenRecordMediaCodecStrategy.Builder(
        width = 320,
        height = 240,
        dpi = 160,
        mediaProjection = projection,
        screenDataListener = listener
    ).build()
}
