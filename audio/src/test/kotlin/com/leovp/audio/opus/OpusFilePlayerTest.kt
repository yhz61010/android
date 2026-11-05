package com.leovp.audio.opus

import android.media.AudioFormat
import com.leovp.audio.base.FileAudioOutput
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class OpusFilePlayerTest {
    @Test
    fun `non-empty payload is submitted`() {
        assertTrue(shouldSubmitOpusPayload(OpusFilePayload(byteArrayOf(1), null)))
    }

    @Test
    fun `empty final payload is treated as natural end`() {
        assertFalse(shouldSubmitOpusPayload(OpusFilePayload(byteArrayOf(), null)))
    }

    @Test
    fun `empty payload before another start code is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            shouldSubmitOpusPayload(OpusFilePayload(byteArrayOf(), 42L))
        }
    }

    @Test
    fun `trailing start code ends OPUS playback and releases resources once`() = runTest {
        val fixture = Fixture()
        val file = createOpusFile(byteArrayOf(1, 2, 3), trailingStartCode = true)
        val player = fixture.createPlayer()
        val completed = CompletableDeferred<Unit>()

        try {
            player.playOpus(file, endCallback = {
                fixture.events += "end-callback"
                completed.complete(Unit)
            })
            withRealTimeout { completed.await() }

            assertEquals(
                listOf(byteArrayOf(1, 2, 3).toList()),
                fixture.decoder.decoded.map {
                    it.toList()
                }
            )
            assertEquals(1, fixture.decoder.eosCount)
            assertEquals(
                listOf(
                    "decoder-start",
                    "output-play",
                    "output-stop",
                    "decoder-release",
                    "output-release",
                    "end-callback"
                ),
                fixture.events
            )

            player.stop()
            assertEquals(1, fixture.decoder.releaseCount)
            assertEquals(1, fixture.output.releaseCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `stop wakes a blocked OPUS write before decoder and output release`() = runTest {
        val fixture = Fixture()
        fixture.output.blockWrites = true
        fixture.decoder.emitPcmOnDecode = byteArrayOf(9, 8)
        val file = createOpusFile(byteArrayOf(1), trailingStartCode = false)
        val player = fixture.createPlayer()

        try {
            player.playOpus(file, endCallback = {})
            assertTrue(fixture.output.writeEntered.await(2, TimeUnit.SECONDS))

            withRealTimeout { player.stop() }

            assertEquals(
                listOf(
                    "decoder-start",
                    "output-play",
                    "write-start",
                    "output-stop",
                    "write-end",
                    "decoder-release",
                    "output-release"
                ),
                fixture.events
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `decoded queue overflow reports failure after releasing OPUS resources`() = runTest {
        val fixture = Fixture()
        fixture.output.blockWrites = true
        val file = createOpusFile(byteArrayOf(1), trailingStartCode = false)
        val player = fixture.createPlayer()
        val failure = CompletableDeferred<Throwable>()

        try {
            player.playOpus(file, endCallback = {}, errorCallback = {
                fixture.events += "error-callback"
                failure.complete(it)
            })
            repeat(66) { fixture.decoder.emitPcm(byteArrayOf(1, 2)) }

            val error = withRealTimeout { failure.await() }
            assertEquals("Decoded PCM queue is full", error.message)
            assertTrue(
                fixture.events.indexOf("decoder-release") <
                    fixture.events.indexOf("error-callback")
            )
            assertTrue(
                fixture.events.indexOf("output-release") <
                    fixture.events.indexOf("error-callback")
            )
            assertEquals(1, fixture.decoder.releaseCount)
            assertEquals(1, fixture.output.releaseCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `Error during OPUS startup releases initialized resources`() = runTest {
        val fixture = Fixture()
        val startupError = AssertionError("decoder start failed")
        fixture.decoder.startFailure = startupError
        val file = createOpusFile(byteArrayOf(1), trailingStartCode = false)
        val player = fixture.createPlayer()

        try {
            val thrown = assertFailsWith<AssertionError> {
                player.playOpus(file, endCallback = {})
            }

            assertEquals(startupError.message, thrown.message)
            assertEquals(1, fixture.decoder.releaseCount)
            assertEquals(1, fixture.output.releaseCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `OPUS stop failure does not skip decoder or output release`() = runTest {
        val fixture = Fixture()
        val stopFailure = IllegalStateException("AudioTrack stop failed")
        fixture.output.stopFailure = stopFailure
        val file = createOpusFile(byteArrayOf(1), trailingStartCode = false)
        val player = fixture.createPlayer()

        try {
            player.playOpus(file, endCallback = {})
            val thrown = assertFailsWith<IllegalStateException> { player.stop() }

            assertEquals(stopFailure.message, thrown.message)
            assertEquals(1, fixture.decoder.releaseCount)
            assertEquals(1, fixture.output.releaseCount)
        } finally {
            file.delete()
        }
    }

    private class Fixture {
        val events = CopyOnWriteArrayList<String>()
        val output = FakeAudioOutput(events)
        val decoder = FakeDecoder(events)

        fun createPlayer(): OpusFilePlayer = OpusFilePlayer(
            audioDecoderInfo = AudioDecoderInfo(
                sampleRate = 48_000,
                channelConfig = AudioFormat.CHANNEL_OUT_MONO,
                audioFormat = AudioFormat.ENCODING_PCM_16BIT
            ),
            audioOutput = output,
            decoderFactory = OpusDecoderSessionFactory { _, _, onPcm, onEnd, onFailure ->
                decoder.onPcm = onPcm
                decoder.onEnd = onEnd
                decoder.onFailure = onFailure
                decoder
            }
        )
    }

    private class FakeDecoder(private val events: MutableList<String>) : OpusDecoderSession {
        override val isAcceptingInput: Boolean = true
        lateinit var onPcm: (ByteArray) -> Unit
        lateinit var onEnd: () -> Unit
        lateinit var onFailure: (Throwable) -> Unit
        val decoded = CopyOnWriteArrayList<ByteArray>()
        var emitPcmOnDecode: ByteArray? = null
        var startFailure: Throwable? = null
        var eosCount = 0
        var releaseCount = 0

        override fun start() {
            events += "decoder-start"
            startFailure?.let { throw it }
        }

        override fun decode(rawData: ByteArray): Boolean {
            decoded += rawData.copyOf()
            emitPcmOnDecode?.let(::emitPcm)
            return true
        }

        override fun signalEndOfStream(): Boolean {
            eosCount++
            onEnd()
            return true
        }

        override suspend fun releaseAndJoin() {
            releaseCount++
            events += "decoder-release"
        }

        fun emitPcm(data: ByteArray) = onPcm(data)
    }

    private class FakeAudioOutput(private val events: MutableList<String>) : FileAudioOutput {
        override val playbackHeadPosition: Int = Int.MAX_VALUE
        val writeEntered = CountDownLatch(1)
        private val allowWrite = CountDownLatch(1)
        var blockWrites = false
        var releaseCount = 0
        var stopFailure: Throwable? = null

        override fun play() {
            events += "output-play"
        }

        override fun write(pcmBytes: ByteArray): Int {
            events += "write-start"
            writeEntered.countDown()
            if (blockWrites) check(allowWrite.await(2, TimeUnit.SECONDS))
            events += "write-end"
            return pcmBytes.size
        }

        override fun stop() {
            events += "output-stop"
            allowWrite.countDown()
            stopFailure?.let { throw it }
        }

        override fun release() {
            releaseCount++
            events += "output-release"
        }
    }

    private fun createOpusFile(audioFrame: ByteArray, trailingStartCode: Boolean): File {
        val startCode = OpusFilePlayer.START_CODE.encodeToByteArray()
        val config = ByteBuffer.allocate(16 + 8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(IAudioMediaCodec.OPUS_AOPUSHDR)
            .putLong(8)
            .putLong(0)
            .array()
        val bytes = ByteArrayOutputStream().apply {
            write(startCode)
            write(config)
            write(startCode)
            write(audioFrame)
            if (trailingStartCode) write(startCode)
        }.toByteArray()
        return Files.createTempFile("opus-player", ".opus").toFile().apply {
            writeBytes(bytes)
        }
    }

    private suspend fun <T> withRealTimeout(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(2_000) { block() }
        }
}
