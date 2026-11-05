package com.leovp.audio.aac

import android.media.AudioFormat
import android.media.MediaFormat
import com.leovp.audio.base.FileAudioOutput
import com.leovp.audio.base.bean.AudioDecoderInfo
import io.mockk.mockk
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AacFilePlayerTest {
    @Test
    fun `non-positive AudioTrack writes are terminal failures`() {
        listOf(0, -6).forEach { result ->
            val error = assertFailsWith<IllegalStateException> {
                requireSuccessfulAacAudioWrite(result)
            }

            assertEquals("AudioTrack accepted no AAC PCM bytes: $result", error.message)
        }
    }

    @Test
    fun `second play leaves the active AAC session untouched`() = runTest {
        val fixture = Fixture()
        val player = fixture.createPlayer()

        player.playAac(File("first.aac"), endCallback = {})
        val error = assertFailsWith<IllegalStateException> {
            player.playAac(File("second.aac"), endCallback = {})
        }

        assertEquals("AacFilePlayer can only be started once", error.message)
        assertEquals(1, fixture.sourceFactory.openCount)
        assertEquals(1, fixture.decoder.startCount)
        assertEquals(0, fixture.decoder.releaseCount)
        assertFalse(fixture.output.released)
        withRealTimeout { player.stop() }
    }

    @Test
    fun `natural end releases every AAC resource before callback`() = runTest {
        val fixture = Fixture()
        val player = fixture.createPlayer()
        val completed = CompletableDeferred<Unit>()

        player.playAac(File("audio.aac"), endCallback = {
            fixture.events += "end-callback"
            completed.complete(Unit)
        })
        fixture.decoder.emitEnd()
        withRealTimeout { completed.await() }

        assertEquals(
            listOf(
                "output-play",
                "output-stop",
                "decoder-release",
                "source-release",
                "output-release",
                "end-callback"
            ),
            fixture.events
        )
        assertEquals(1, fixture.decoder.releaseCount)
        assertTrue(fixture.output.released)

        withRealTimeout { player.stop() }
        assertEquals(1, fixture.decoder.releaseCount)
        assertEquals(1, fixture.output.releaseCount)
    }

    @Test
    fun `Error during AAC startup releases every initialized resource`() = runTest {
        val fixture = Fixture()
        val startupError = AssertionError("AudioTrack start failed")
        fixture.output.playFailure = startupError
        val player = fixture.createPlayer()

        val thrown = assertFailsWith<AssertionError> {
            player.playAac(File("audio.aac"), endCallback = {})
        }

        assertEquals(startupError.message, thrown.message)
        assertEquals(1, fixture.decoder.releaseCount)
        assertEquals(1, fixture.sourceFactory.releaseCount)
        assertEquals(1, fixture.output.releaseCount)
    }

    @Test
    fun `AAC stop failure does not skip decoder or external resource release`() = runTest {
        val fixture = Fixture()
        val stopFailure = IllegalStateException("AudioTrack stop failed")
        fixture.output.stopFailure = stopFailure
        val player = fixture.createPlayer()
        player.playAac(File("audio.aac"), endCallback = {})

        val thrown = assertFailsWith<IllegalStateException> { player.stop() }

        assertEquals(stopFailure.message, thrown.message)
        assertEquals(1, fixture.decoder.releaseCount)
        assertEquals(1, fixture.sourceFactory.releaseCount)
        assertEquals(1, fixture.output.releaseCount)
    }

    @Test
    fun `AAC source release failure does not skip output release`() = runTest {
        val fixture = Fixture()
        val sourceFailure = IllegalStateException("source release failed")
        fixture.sourceFactory.releaseFailure = sourceFailure
        val player = fixture.createPlayer()
        player.playAac(File("audio.aac"), endCallback = {})

        val thrown = assertFailsWith<IllegalStateException> { player.stop() }

        assertEquals(sourceFailure.message, thrown.message)
        assertEquals(1, fixture.decoder.releaseCount)
        assertEquals(1, fixture.sourceFactory.releaseCount)
        assertEquals(1, fixture.output.releaseCount)
    }

    private class Fixture {
        val events = CopyOnWriteArrayList<String>()
        val output = FakeAudioOutput(events)
        val sourceFactory = FakeSourceFactory(events)
        val decoder = FakeDecoder(events)

        fun createPlayer(): AacFilePlayer = AacFilePlayer(
            audioDecodeInfo = AudioDecoderInfo(
                sampleRate = 48_000,
                channelConfig = AudioFormat.CHANNEL_OUT_MONO,
                audioFormat = AudioFormat.ENCODING_PCM_16BIT
            ),
            audioOutput = output,
            sourceFactory = sourceFactory,
            decoderFactory = AacDecoderSessionFactory { _, _, _, _, onEnd, onFailure ->
                decoder.onEnd = onEnd
                decoder.onFailure = onFailure
                decoder
            }
        )
    }

    private class FakeSourceFactory(private val events: MutableList<String>) :
        AacSampleSourceFactory {
        var openCount = 0
        var releaseCount = 0
        var releaseFailure: Throwable? = null

        override fun open(file: File): AacSampleSource {
            openCount++
            return object : AacSampleSource {
                override val mime: String = MediaFormat.MIMETYPE_AUDIO_AAC
                override val mediaFormat: MediaFormat = mockk(relaxed = true)

                override fun readSample(buffer: ByteBuffer): AacSample = AacSample(-1, -1)

                override fun release() {
                    releaseCount++
                    events += "source-release"
                    releaseFailure?.let { throw it }
                }
            }
        }
    }

    private class FakeDecoder(private val events: MutableList<String>) : AacDecoderSession {
        lateinit var onEnd: () -> Unit
        lateinit var onFailure: (Throwable) -> Unit
        var startCount = 0
        var releaseCount = 0

        override fun start() {
            startCount++
        }

        override suspend fun releaseAndJoin() {
            releaseCount++
            events += "decoder-release"
        }

        fun emitEnd() = onEnd()
    }

    private class FakeAudioOutput(private val events: MutableList<String>) : FileAudioOutput {
        override val playbackHeadPosition: Int = 0
        var released = false
        var releaseCount = 0
        var playFailure: Throwable? = null
        var stopFailure: Throwable? = null

        override fun play() {
            events += "output-play"
            playFailure?.let { throw it }
        }

        override fun write(pcmBytes: ByteArray): Int = pcmBytes.size

        override fun stop() {
            events += "output-stop"
            stopFailure?.let { throw it }
        }

        override fun release() {
            released = true
            releaseCount++
            events += "output-release"
        }
    }

    private suspend fun <T> withRealTimeout(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(2_000) { block() }
        }
}
