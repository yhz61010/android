package com.leovp.demo.basiccomponents.examples.audio

import android.Manifest
import android.content.Context
import android.os.Looper
import android.widget.ToggleButton
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import com.leovp.android.exts.createFile
import com.leovp.audio.AudioPlayer
import com.leovp.audio.MicRecorder
import com.leovp.audio.aac.AacFilePlayer
import com.leovp.audio.base.AudioType
import com.leovp.audio.opus.OpusFilePlayer
import com.leovp.demo.R
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class AudioActivitySessionTest {
    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun `queued recording failure cannot reset a replacement of the same format`() =
        checkRecordingFailure(replace = true)

    @Test
    fun `current recording failure still resets its button`() =
        checkRecordingFailure(replace = false)

    @Test
    fun `recording stop closes output when buffered write fails`() =
        checkOutputClosedAfterFailure(failWrite = true)

    @Test
    fun `recording stop closes output when underlying flush fails`() =
        checkOutputClosedAfterFailure(failWrite = false)

    private fun checkOutputClosedAfterFailure(failWrite: Boolean) {
        var failureReached = false
        var closeCount = 0
        val underlying = object : OutputStream() {
            override fun write(value: Int) {
                if (failWrite) {
                    failureReached = true
                    throw IOException("Buffered write failed")
                }
            }

            override fun flush() {
                failureReached = true
                throw IOException("Output flush failed")
            }

            override fun close() {
                closeCount++
            }
        }
        val output = BufferedOutputStream(underlying).apply { write(1) }
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildActivity(AudioActivity::class.java).setup()
        val activity = controller.get()
        val callbackMethod = AudioActivity::class.java.declaredMethods.single {
            it.name == "recordCallbackFor"
        }
        callbackMethod.isAccessible = true
        val callback = callbackMethod.invoke(
            activity,
            0L,
            AudioType.PCM,
            output,
            AtomicReference<MicRecorder?>()
        ) as MicRecorder.RecordCallback
        try {
            callback.onStop(true)
            assertTrue(
                "The real buffered stream must reach the failing IO operation",
                failureReached
            )
            assertEquals("IO failure must not skip closing the underlying stream", 1, closeCount)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun checkRecordingFailure(replace: Boolean) {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildActivity(AudioActivity::class.java).setup()
        val activity = controller.get()
        val button = activity.findViewById<ToggleButton>(R.id.btnRecordAac)
        button.setOnCheckedChangeListener(null)
        button.isChecked = true
        val intents = field(activity, "recordingIntents") as RecordingIntentCoordinator
        val token = intents.requestStart(AudioType.AAC)
        val callbackMethod = AudioActivity::class.java.declaredMethods.single {
            it.name == "recordCallbackFor"
        }
        callbackMethod.isAccessible = true
        val callback = callbackMethod.invoke(
            activity,
            token,
            AudioType.AAC,
            null,
            AtomicReference<MicRecorder?>()
        ) as MicRecorder.RecordCallback
        val failure = Thread { callback.onStop(false) }
        failure.start()
        failure.join(3_000)
        val replacement = if (replace) intents.requestStart(AudioType.AAC) else null

        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Only the current failure may reset its button", replace, button.isChecked)
        if (replacement != null) assertTrue(intents.isCurrent(replacement, AudioType.AAC))
        controller.pause().stop().destroy()
    }

    @Test
    fun `AAC queued error cannot reset or detach replacement playback`() =
        checkPlayback(AudioType.AAC, true)

    @Test
    fun `AAC queued completion cannot reset replacement playback`() =
        checkPlayback(AudioType.AAC, false)

    @Test
    fun `OPUS queued error cannot reset or detach replacement playback`() =
        checkPlayback(AudioType.OPUS, true)

    @Test
    fun `OPUS queued completion cannot reset replacement playback`() =
        checkPlayback(AudioType.OPUS, false)

    @Test
    fun `queued permission denial cannot finish a replacement recording intent`() {
        val requests = LinkedBlockingQueue<OnPermissionCallback>()
        mockkStatic(XXPermissions::class)
        val permissions = mockk<XXPermissions>()
        every { XXPermissions.with(any<Context>()) } returns permissions
        every { permissions.permission(any<IPermission>()) } returns permissions
        every { permissions.request(any()) } answers { requests.put(firstArg()) }
        val controller = Robolectric.buildActivity(AudioActivity::class.java).setup()
        requests.remove() // Initial permission request, before any recording intent.
        val activity = controller.get()
        val button = activity.findViewById<ToggleButton>(R.id.btnRecordPcm)
        button.isChecked = true
        val oldRequest = requests.remove()
        val denial = Thread {
            oldRequest.onResult(emptyList(), listOf(PermissionLists.getRecordAudioPermission()))
        }
        denial.start()
        denial.join(3_000)
        button.isChecked = false
        button.isChecked = true
        requests.remove()

        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("Old denial must not finish the replacement Activity", activity.isFinishing)
        assertTrue(button.isChecked)
        controller.pause().stop().destroy()
    }

    @Test
    fun `startup failure queued after cleanup cannot reset a replacement recording intent`() {
        val requests = LinkedBlockingQueue<OnPermissionCallback>()
        mockkStatic(XXPermissions::class)
        val permissions = mockk<XXPermissions>()
        every { XXPermissions.with(any<Context>()) } returns permissions
        every { permissions.permission(any<IPermission>()) } returns permissions
        every { permissions.request(any()) } answers { requests.put(firstArg()) }
        mockkConstructor(MicRecorder::class)
        val cleaning = CountDownLatch(1)
        val finishCleanup = CountDownLatch(1)
        every { anyConstructed<MicRecorder>().startRecord() } throws
            IllegalStateException("Start failed")
        coEvery { anyConstructed<MicRecorder>().stopRecordAndJoin() } answers {
            cleaning.countDown()
            check(finishCleanup.await(5, TimeUnit.SECONDS))
        }
        val controller = Robolectric.buildActivity(AudioActivity::class.java).setup()
        requests.remove()
        val activity = controller.get()
        val button = activity.findViewById<ToggleButton>(R.id.btnRecordPcm)
        button.isChecked = true
        requests.remove().onResult(listOf(PermissionLists.getRecordAudioPermission()), emptyList())
        assertTrue(cleaning.await(5, TimeUnit.SECONDS))
        button.isChecked = false
        button.isChecked = true
        requests.remove()
        finishCleanup.countDown()
        runBlocking { (field(activity, "ioScopeJob") as Job).children.toList().joinAll() }

        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("Old startup catch must not reset the replacement button", button.isChecked)
        controller.pause().stop().destroy()
    }

    @Test
    fun `PCM queued finally cannot stop replacement playback`() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        mockkConstructor(AudioPlayer::class)
        val playing = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val writes = AtomicInteger()
        every { anyConstructed<AudioPlayer>().play(any()) } answers {
            if (writes.incrementAndGet() == 2) {
                playing.countDown()
                finish.await(5, TimeUnit.SECONDS)
            }
            Unit
        }
        every { anyConstructed<AudioPlayer>().stop() } returns Unit
        coEvery { anyConstructed<AudioPlayer>().releaseAndJoin() } returns Unit
        val controller = Robolectric.buildActivity(AudioActivity::class.java).setup()
        val activity = controller.get()
        activity.createFile("audio_mono.pcm").writeBytes(byteArrayOf(1, 2))
        val button = activity.findViewById<ToggleButton>(R.id.btnPlayPCM)
        button.isChecked = true
        (field(activity, "playPcmThread") as Thread).join(3_000)
        button.isChecked = false
        button.isChecked = true
        assertTrue(playing.await(5, TimeUnit.SECONDS))
        val replacement = field(activity, "audioPlayer")
        try {
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Old PCM finally must not reset the replacement button", button.isChecked)
            assertSame(replacement, field(activity, "audioPlayer"))
        } finally {
            finish.countDown()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `AAC queued startup exception cannot stop replacement playback`() =
        checkPlayback(AudioType.AAC, true, startupFailure = true)

    @Test
    fun `OPUS queued startup exception cannot stop replacement playback`() =
        checkPlayback(AudioType.OPUS, true, startupFailure = true)

    @Test
    fun `current AAC error resets playback`() = checkPlayback(AudioType.AAC, true, replace = false)

    @Test
    fun `current AAC completion resets playback`() =
        checkPlayback(AudioType.AAC, false, replace = false)

    @Test
    fun `current OPUS error resets playback`() =
        checkPlayback(AudioType.OPUS, true, replace = false)

    @Test
    fun `current OPUS completion resets playback`() =
        checkPlayback(AudioType.OPUS, false, replace = false)

    private fun checkPlayback(
        type: AudioType,
        error: Boolean,
        startupFailure: Boolean = false,
        replace: Boolean = true
    ) {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        val callbacks = LinkedBlockingQueue<Pair<() -> Unit, (Throwable) -> Unit>>()
        val attempts = AtomicInteger()
        mockkConstructor(AacFilePlayer::class, OpusFilePlayer::class)
        coEvery { anyConstructed<AacFilePlayer>().playAac(any(), any(), any()) } answers {
            callbacks.put(secondArg<() -> Unit>() to thirdArg<(Throwable) -> Unit>())
            if (startupFailure && attempts.incrementAndGet() == 1) error("Old startup failed")
        }
        coEvery { anyConstructed<OpusFilePlayer>().playOpus(any(), any(), any()) } answers {
            callbacks.put(secondArg<() -> Unit>() to thirdArg<(Throwable) -> Unit>())
            if (startupFailure && attempts.incrementAndGet() == 1) error("Old startup failed")
        }
        coEvery { anyConstructed<AacFilePlayer>().stop() } returns Unit
        coEvery { anyConstructed<OpusFilePlayer>().stop() } returns Unit
        val controller = Robolectric.buildActivity(AudioActivity::class.java).setup()
        val activity = controller.get()
        val buttonId = if (type == AudioType.AAC) R.id.btnPlayAac else R.id.btnPlayOpus
        val playerField = if (type == AudioType.AAC) "aacFilePlayer" else "opusFilePlayer"
        val button = activity.findViewById<ToggleButton>(buttonId)
        button.isChecked = true
        val oldCallbacks = requireNotNull(callbacks.poll(5, TimeUnit.SECONDS))
        if (startupFailure) {
            runBlocking { (field(activity, "ioScopeJob") as Job).children.toList().joinAll() }
        } else {
            val callbackThread = Thread {
                if (error) {
                    oldCallbacks.second(IllegalStateException("old playback failed"))
                } else {
                    oldCallbacks.first()
                }
            }
            callbackThread.start()
            callbackThread.join(3_000)
        }
        val replacement = if (replace) {
            button.isChecked = false
            button.isChecked = true
            requireNotNull(callbacks.poll(5, TimeUnit.SECONDS))
            field(activity, playerField)
        } else {
            null
        }

        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Only the current callback may reset playback", replace, button.isChecked)
        if (replace) {
            assertSame(replacement, field(activity, playerField))
        } else {
            assertNull(field(activity, playerField))
        }
        controller.pause().stop().destroy()
    }

    private fun field(activity: AudioActivity, name: String): Any? =
        AudioActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
}
