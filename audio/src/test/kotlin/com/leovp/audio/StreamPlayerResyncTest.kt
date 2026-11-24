package com.leovp.audio

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import com.leovp.audio.aac.AacDecoder
import com.leovp.audio.aac.AacStreamPlayer
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.audio.mediacodec.BaseMediaCodec
import com.leovp.audio.opus.OpusDecoder
import com.leovp.audio.opus.OpusStreamPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class StreamPlayerResyncTest {
    private val scheduler = TestCoroutineScheduler()
    private val scopes = mutableListOf<CoroutineScope>()
    private val decoders = mutableListOf<BaseMediaCodec>()
    private val codecs = mutableListOf<MediaCodec>()
    private val info = AudioDecoderInfo(48_000, AudioFormat.CHANNEL_OUT_MONO)
    private val callback = object : IDecodeCallback {
        override fun onDecoded(pcmData: ByteArray) = Unit
    }

    @Before
    fun setUp() {
        Class.forName(MediaCodec::class.java.name, true, MediaCodec::class.java.classLoader)
        Class.forName(MediaFormat::class.java.name, true, MediaFormat::class.java.classLoader)
        mockkStatic(MediaCodec::class, MediaFormat::class)
        every { MediaCodec.createDecoderByType(any()) } answers {
            mockk<MediaCodec>(relaxed = true).also { codecs += it }
        }
        every { MediaFormat.createAudioFormat(any(), any(), any()) } answers {
            mockk<MediaFormat>(relaxed = true)
        }
    }

    @After
    fun tearDown() {
        try {
            cancelWorkers()
            runBlocking { decoders.forEach { it.releaseAndJoin() } }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `AAC clearing pending input keeps the running decoder usable`() {
        val decoder = aacDecoder()
        repeat(10) { assertTrue(decoder.decode(byteArrayOf(1, 2))) }
        decoder.clearPendingInput()
        assertEquals(0, decoder.queueSize)
        assertTrue(decoder.decode(byteArrayOf(3, 4)))
        assertEquals(1, decoder.queueSize)
        verify(exactly = 0) { codecs.single().flush() }
    }

    @Test
    fun `OPUS clearing pending input preserves acceptance and later EOS`() {
        val decoder = opusDecoder()
        repeat(64) { assertTrue(decoder.decode(byteArrayOf(1, 2))) }
        decoder.clearPendingInput()
        assertEquals(0, decoder.queueSize)
        assertTrue(decoder.isAcceptingInput)
        assertTrue(decoder.decode(byteArrayOf(3, 4)))
        assertTrue(decoder.signalEndOfStream())
        assertEquals(2, decoder.queueSize)
        assertFalse(decoder.isAcceptingInput)
        verify(exactly = 0) { codecs.single().flush() }
    }

    @Test
    fun `AAC full queue resync discards pending frames and flushes the same codec`() =
        checkResync(opus = false, fullQueue = true)

    @Test
    fun `AAC latency resync also discards a partial backlog`() =
        checkResync(opus = false, fullQueue = false)

    @Test
    fun `OPUS full queue resync discards backlog without replacing the codec`() =
        checkResync(opus = true, fullQueue = true)

    @Test
    fun `OPUS latency resync also discards a partial backlog without codec reset`() =
        checkResync(opus = true, fullQueue = false)

    private fun checkResync(opus: Boolean, fullQueue: Boolean) {
        val context = RuntimeEnvironment.getApplication()
        val decoder = if (opus) opusDecoder() else aacDecoder()
        val player: Any = if (opus) {
            OpusStreamPlayer(context, info) { throw AssertionError(it) }
        } else {
            AacStreamPlayer(context, info) { throw AssertionError(it) }
        }
        setField(player, "audioDecoder", decoder)
        setField(player, "csd0", byteArrayOf(1, 2))
        val clock = SystemClock.elapsedRealtimeNanos() / 1_000
        setField(player, "playStartTimeInUs", clock - if (fullQueue) 0L else 2_000_000L)
        val track = getField(player, "audioTrackPlayer") as AudioTrackPlayer
        try {
            track.play()
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
            val backlog = if (fullQueue) {
                if (opus) 64 else 10
            } else {
                2
            }
            repeat(backlog) { assertTrue(enqueue(decoder)) }
            if (opus) pcmQueue(player).put(byteArrayOf(5, 6))
            play(player)
            assertEquals("Resync must discard encoded backlog", 0, queueSize(decoder))
            if (opus) {
                assertEquals(0, pcmQueue(player).size)
                assertTrue((decoder as OpusDecoder).isAcceptingInput)
            }
            assertEquals("Resync must not recreate a codec", 1, codecs.size)
            verify(exactly = if (opus) 0 else 1) { codecs.single().flush() }
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
            play(player)
            assertEquals("The next frame must still be accepted", 1, queueSize(decoder))
        } finally {
            cancelWorkers()
            runBlocking {
                when (player) {
                    is OpusStreamPlayer -> player.stopPlayingAndJoin()
                    is AacStreamPlayer -> player.stopPlayingAndJoin()
                }
            }
        }
    }

    private fun aacDecoder() = startControlled(
        AacDecoder(
            48_000, 1, AudioFormat.ENCODING_PCM_16BIT, byteArrayOf(0x11, 0x88.toByte()),
            callback, errorCallback = { throw AssertionError(it) }
        )
    )

    private fun opusDecoder() = startControlled(
        OpusDecoder(
            48_000, 1, csd0 = ByteArray(19), csd1 = ByteArray(8), csd2 = ByteArray(8),
            callback = callback, errorCallback = { throw AssertionError(it) }
        )
    )

    private fun <T : BaseMediaCodec> startControlled(decoder: T): T {
        // Hold only the external worker schedule; all state transitions and queue APIs are real.
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        scopes += scope
        BaseMediaCodec::class.java.getDeclaredField("ioScope").apply {
            isAccessible = true
            set(decoder, scope)
        }
        decoders += decoder
        decoder.start()
        return decoder
    }

    private fun cancelWorkers() {
        scopes.forEach { it.cancel() }
        scheduler.runCurrent()
    }

    private fun enqueue(decoder: BaseMediaCodec): Boolean = when (decoder) {
        is OpusDecoder -> decoder.decode(byteArrayOf(1, 2))
        is AacDecoder -> decoder.decode(byteArrayOf(1, 2))
        else -> error("Unexpected decoder")
    }

    private fun queueSize(decoder: BaseMediaCodec): Int = when (decoder) {
        is OpusDecoder -> decoder.queueSize
        is AacDecoder -> decoder.queueSize
        else -> error("Unexpected decoder")
    }

    private fun play(player: Any) {
        when (player) {
            is OpusStreamPlayer -> player.startPlayingStream(ByteArray(20)) { }
            is AacStreamPlayer -> player.startPlayingStream(ByteArray(20)) { }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun pcmQueue(player: Any) = getField(player, "decodedPcmQueue") as
        ArrayBlockingQueue<ByteArray>

    private fun getField(subject: Any, name: String): Any? = subject.javaClass
        .getDeclaredField(name).apply { isAccessible = true }.get(subject)

    private fun setField(subject: Any, name: String, value: Any) {
        subject.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(subject, value)
        }
    }
}
