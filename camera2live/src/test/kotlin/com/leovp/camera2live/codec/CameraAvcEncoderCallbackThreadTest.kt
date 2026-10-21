package com.leovp.camera2live.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import com.leovp.androidbase.utils.media.CodecUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
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
}
