package com.leovp.demo.basiccomponents.examples.cipher

import android.media.MediaPlayer
import android.os.Looper
import android.view.View
import com.leovp.androidbase.utils.cipher.AESUtil
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import java.io.File
import java.io.FileDescriptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.SecretKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class AudioCipherActivityTest {
    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun `destroy during prepare releases local player without starting it`() {
        val preparing = CountDownLatch(1)
        val continuePrepare = CountDownLatch(1)
        val released = CountDownLatch(1)
        val starts = AtomicInteger()
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().setDataSource(any<FileDescriptor>()) } returns Unit
        every { anyConstructed<MediaPlayer>().prepare() } answers {
            preparing.countDown()
            check(continuePrepare.await(5, TimeUnit.SECONDS))
        }
        every { anyConstructed<MediaPlayer>().start() } answers {
            starts.incrementAndGet()
            Unit
        }
        every { anyConstructed<MediaPlayer>().release() } answers { released.countDown() }
        val controller = Robolectric.buildActivity(AudioCipherActivity::class.java).setup()
        val activity = controller.get()
        val key = AudioCipherActivity::class.java.getDeclaredField("secretKey")
            .apply { isAccessible = true }.get(activity) as SecretKey
        File(activity.getExternalFilesDir(null), "encrypted_audio.mp3")
            .writeBytes(AESUtil.encrypt(byteArrayOf(1, 2, 3), key))
        activity.onDecryptAudioClick(View(activity))
        awaitMain(preparing)

        controller.pause().stop().destroy()
        continuePrepare.countDown()

        awaitMain(released)
        assertEquals("A destroyed Activity must not start playback", 0, starts.get())
    }

    @Test
    fun `prepared player starts and is destroyed on main while prepare stays on IO`() {
        val started = CountDownLatch(1)
        val released = AtomicInteger()
        var prepareThread: Thread? = null
        var startThread: Thread? = null
        var releaseThread: Thread? = null
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().setDataSource(any<FileDescriptor>()) } returns Unit
        every { anyConstructed<MediaPlayer>().prepare() } answers {
            prepareThread = Thread.currentThread()
        }
        every { anyConstructed<MediaPlayer>().start() } answers {
            startThread = Thread.currentThread()
            started.countDown()
        }
        every { anyConstructed<MediaPlayer>().release() } answers {
            releaseThread = Thread.currentThread()
            released.incrementAndGet()
            Unit
        }
        val controller = Robolectric.buildActivity(AudioCipherActivity::class.java).setup()
        val activity = controller.get()
        val key = AudioCipherActivity::class.java.getDeclaredField("secretKey")
            .apply { isAccessible = true }.get(activity) as SecretKey
        File(activity.getExternalFilesDir(null), "encrypted_audio.mp3")
            .writeBytes(AESUtil.encrypt(byteArrayOf(1, 2, 3), key))

        activity.onDecryptAudioClick(View(activity))
        awaitMain(started)
        assertNotNull(activePlayer(activity))
        assertTrue(prepareThread !== Looper.getMainLooper().thread)
        assertSame(Looper.getMainLooper().thread, startThread)
        controller.pause().stop().destroy()
        assertSame(startThread, releaseThread)
        assertEquals(1, released.get())
    }

    @Test
    fun `data source failure closes stream and releases unpublished player`() =
        checkFailure("source")

    @Test
    fun `prepare failure closes stream and releases unpublished player`() = checkFailure("prepare")

    @Test
    fun `start failure closes stream and releases unpublished player`() = checkFailure("start")

    private fun checkFailure(stage: String) {
        val released = CountDownLatch(1)
        var descriptor: FileDescriptor? = null
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().setDataSource(any<FileDescriptor>()) } answers {
            descriptor = firstArg()
            if (stage == "source") error("Data source failed")
        }
        every { anyConstructed<MediaPlayer>().prepare() } answers {
            if (stage == "prepare") error("Prepare failed")
        }
        every { anyConstructed<MediaPlayer>().start() } answers { error("Start failed") }
        every { anyConstructed<MediaPlayer>().release() } answers { released.countDown() }
        val controller = Robolectric.buildActivity(AudioCipherActivity::class.java).setup()
        val activity = controller.get()
        val key = AudioCipherActivity::class.java.getDeclaredField("secretKey")
            .apply { isAccessible = true }.get(activity) as SecretKey
        File(activity.getExternalFilesDir(null), "encrypted_audio.mp3")
            .writeBytes(AESUtil.encrypt(byteArrayOf(1, 2, 3), key))

        activity.onDecryptAudioClick(View(activity))
        awaitMain(released)

        assertFalse(
            "Input descriptor must close on every failure",
            requireNotNull(descriptor).valid()
        )
        assertNull(activePlayer(activity))
        val temporaryFiles = activity.cacheDir.listFiles().orEmpty()
            .filter { it.name.startsWith("decrypted_temp_music_file") }
        assertTrue(temporaryFiles.isEmpty())
        controller.pause().stop().destroy()
    }

    private fun activePlayer(activity: AudioCipherActivity): Any? {
        val field = AudioCipherActivity::class.java.getDeclaredField("player")
        field.isAccessible = true
        return field.get(activity)
    }

    private fun awaitMain(latch: CountDownLatch) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (latch.count != 0L && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals("Coroutine did not reach the expected resource boundary", 0L, latch.count)
    }
}
