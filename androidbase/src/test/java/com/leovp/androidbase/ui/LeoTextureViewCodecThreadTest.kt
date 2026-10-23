package com.leovp.androidbase.ui

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Size
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

abstract class LeoTextureViewCodecThreadTestBase {
    protected val codec = mockk<MediaCodec>(relaxed = true)
    protected val events = CopyOnWriteArrayList<String>()
    protected val callbackThreads = CopyOnWriteArrayList<String>()
    protected lateinit var view: LeoTextureView

    @Before
    fun setUp() {
        Class.forName(MediaCodec::class.java.name, true, MediaCodec::class.java.classLoader)
        Class.forName(MediaFormat::class.java.name, true, MediaFormat::class.java.classLoader)
        mockkStatic(MediaCodec::class, MediaFormat::class)
        every { MediaFormat.createVideoFormat(any(), any(), any()) } returns mockk(relaxed = true)
        every { MediaCodec.createDecoderByType(any()) } answers {
            callbackThreads += "create:${Thread.currentThread().name}"
            codec
        }
        every { codec.setCallback(any()) } answers {
            events += "callback"
            callbackThreads += "callback:${Thread.currentThread().name}"
        }
        every { codec.setCallback(any(), any()) } answers { events += "callback" }
        every { codec.configure(any(), any(), null, 0) } answers { events += "configure" }
        view = LeoTextureView(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        if (::view.isInitialized) view.releaseDecoder()
        unmockkAll()
    }

    protected fun initializeDecoder() {
        view.initDecoder(null, byteArrayOf(1), byteArrayOf(2), Size(320, 240))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LeoTextureViewApi28CodecThreadTest : LeoTextureViewCodecThreadTestBase() {
    @Test
    fun `handler callback is installed before configure`() {
        initializeDecoder()

        assertEquals(listOf("callback", "configure"), events)
        verify(exactly = 1) { codec.setCallback(any(), any()) }
    }

    @Test
    fun `callback setup failure releases the created codec`() {
        every { codec.setCallback(any(), any()) } throws IllegalStateException("callback failed")

        runCatching { initializeDecoder() }

        verify(exactly = 1) { codec.release() }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LeoTextureViewApi22CodecThreadTest : LeoTextureViewCodecThreadTestBase() {
    @Test
    fun `codec creation and default callback share the dedicated thread before configure`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        initializeDecoder()

        assertEquals(listOf("callback", "configure"), events)
        val createThread = callbackThreads.single {
            it.startsWith("create:")
        }.substringAfter(':')
        val callbackThread = callbackThreads.single {
            it.startsWith("callback:")
        }.substringAfter(':')
        assertEquals(createThread, callbackThread)
        assertTrue(createThread.contains("LTV-decoder-callback"))
        verify(exactly = 1) { codec.setCallback(any()) }
    }

    @Test
    fun `interrupted codec setup preserves the caller interrupt status`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        val creationStarted = CountDownLatch(1)
        val allowCreation = CountDownLatch(1)
        every { MediaCodec.createDecoderByType(any()) } answers {
            creationStarted.countDown()
            allowCreation.await()
            codec
        }
        val failure = AtomicReference<Throwable?>()
        val interrupted = AtomicBoolean(false)
        val caller = Thread {
            failure.set(runCatching { initializeDecoder() }.exceptionOrNull())
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
        every { MediaCodec.createDecoderByType(any()) } throws
            InterruptedException("worker interrupted")

        val failure = runCatching { initializeDecoder() }.exceptionOrNull()
        val callerInterrupted = Thread.interrupted()

        assertTrue(failure is InterruptedException)
        assertFalse(callerInterrupted)
    }
}
