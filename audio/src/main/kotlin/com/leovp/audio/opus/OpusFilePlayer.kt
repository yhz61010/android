package com.leovp.audio.opus

import android.content.Context
import android.media.AudioAttributes
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.audio.mediacodec.utils.AudioCodecUtil
import com.leovp.bytes.readLongLE
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Author: Michael Leo
 * Date: 2023/5/5 16:03
 */
class OpusFilePlayer(
    ctx: Context,
    private val audioDecoderInfo: AudioDecoderInfo,
    // AudioAttributes.USAGE_VOICE_COMMUNICATION  AudioAttributes.USAGE_MEDIA
    usage: Int = AudioAttributes.USAGE_MEDIA,
    // AudioAttributes.CONTENT_TYPE_SPEECH  AudioAttributes.CONTENT_TYPE_MUSIC
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
) {
    companion object {
        private const val TAG = "OpusFilePlayer"
        private const val OUTPUT_DRAIN_TIMEOUT_MS = 3_000L
        private const val QUEUE_LOG_INTERVAL_FRAMES = 50L
        const val START_CODE = "|leo|"
    }

    private val queue = ArrayBlockingQueue<ByteArray>(64)

    private val ioScope = CoroutineScope(Dispatchers.IO + CoroutineName("opus-file-player"))

    private val audioTrackPlayer: AudioTrackPlayer =
        AudioTrackPlayer(ctx, audioDecoderInfo, usage = usage, contentType = contentType)
    private var decoder: OpusDecoder? = null

    // private var cb: (() -> Unit)? = null

    private lateinit var rf: RandomAccessFile
    private val stopped = AtomicBoolean(false)

    fun playOpus(opusFile: File, endCallback: () -> Unit) {
        // cb = endCallback
        val playbackInput = try {
            rf = RandomAccessFile(opusFile, "r")
            LogContext.log.w(TAG, "File length=${rf.length()}")
            val framedFileReader = OpusFramedFileReader(rf, START_CODE.encodeToByteArray())
            val csdPayload = framedFileReader.readPayload(0)
            val firstAudioFramePosition = requireNotNull(csdPayload.nextStartCodePosition) {
                "OPUS file does not contain an audio frame"
            }
            val opusCsd = requireNotNull(
                AudioCodecUtil.parseOpusConfigFrame(csdPayload.data, ByteOrder.LITTLE_ENDIAN)
            ) { "Invalid OPUS codec configuration" }
            LogContext.log.w(TAG, "csd0=${opusCsd.csd0.toHexString()}")
            LogContext.log.w(
                TAG,
                "csd1=${opusCsd.csd1.readLongLE()} ${opusCsd.csd1.toHexString()}"
            )
            LogContext.log.w(
                TAG,
                "csd2=${opusCsd.csd2.readLongLE()} ${opusCsd.csd2.toHexString()}"
            )
            decoder = OpusDecoder(
                sampleRate = audioDecoderInfo.sampleRate,
                channelCount = audioDecoderInfo.channelCount,
                audioFormat = audioDecoderInfo.audioFormat,
                csd0 = opusCsd.csd0,
                csd1 = opusCsd.csd1,
                csd2 = opusCsd.csd2,
                callback = object : IDecodeCallback {
                    override fun onDecoded(pcmData: ByteArray) {
                        if (pcmData.isNotEmpty()) queue.put(pcmData)
                        // LogContext.log.i(TAG, "onDecoded -> queue[${queue.size}]
                        // pcm[${pcmData.size}]")
                    }
                }
            ).apply { start() }
            audioTrackPlayer.play()
            OpusPlaybackInput(framedFileReader, firstAudioFramePosition)
        } catch (e: Exception) {
            stop()
            throw e
        }

        val isDecodeDone = AtomicBoolean(false)
        val submittedFrameCount = AtomicLong(0)
        val playedFrameCount = AtomicLong(0)
        ioScope.launch(Dispatchers.IO) {
            var startCodeBeginPos = playbackInput.firstAudioFramePosition

            val maxFrameSizeMs: Long = 20 // ms
            val baseDelay = 10L
            val maxFrameSize: Long = audioDecoderInfo.sampleRate / 1000 * maxFrameSizeMs

            var frame: Long = 0
            var calcDelay = baseDelay
            var delayChanged = false
            try {
                while (true) {
                    ensureActive()
                    val payload = playbackInput.reader.readPayload(startCodeBeginPos)
                    require(payload.data.isNotEmpty()) { "Empty OPUS audio frame" }
                    check(decoder?.decode(payload.data) == true) {
                        "OPUS decoder input queue is full"
                    }
                    val submittedFrames = submittedFrameCount.incrementAndGet()
                    val delayMs = if (calcDelay > maxFrameSizeMs) maxFrameSizeMs else calcDelay
                    if (queue.size < 1) {
                        calcDelay = 10
                        delayChanged = false
                        frame = 0
                    } else if (queue.size in 1..10) {
                        // Queue in good condition.
                        delayChanged = false
                        frame = 0
                    } else {
                        if (!delayChanged) {
                            delayChanged = true
                            calcDelay += 1
                        }
                        if (++frame % (maxFrameSize / 20) == 0L) {
                            delayChanged = false
                        }
                    }
                    if (submittedFrames % QUEUE_LOG_INTERVAL_FRAMES == 0L) {
                        LogContext.log.d(
                            TAG,
                            "queue[${queue.size}] delay=$delayMs delayChanged=$delayChanged " +
                                "maxFrameSize=$maxFrameSize frame=$frame"
                        )
                    }
                    delay(delayMs)
                    startCodeBeginPos = payload.nextStartCodePosition ?: break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!stopped.get()) LogContext.log.e(TAG, "Decode OPUS file failed", e)
            } finally {
                closeInputFile()
                isDecodeDone.set(true)
            }
        }

        ioScope.launch(Dispatchers.IO) {
            while (true) {
                ensureActive()
                val pcmData = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                // LogContext.log.w(TAG, "play -> queue[${queue.size}] pcm=${pcmData.size}")
                audioTrackPlayer.write(pcmData)
                playedFrameCount.incrementAndGet()
            }
        }

        ioScope.launch {
            while (!isDecodeDone.get()) delay(100)
            val drained = withTimeoutOrNull(OUTPUT_DRAIN_TIMEOUT_MS) {
                while (
                    playedFrameCount.get() < submittedFrameCount.get() ||
                    queue.isNotEmpty()
                ) {
                    delay(50)
                }
                true
            } ?: false
            if (!drained && !stopped.get()) {
                LogContext.log.e(
                    TAG,
                    "Timed out draining OPUS output: submitted=${submittedFrameCount.get()} " +
                        "played=${playedFrameCount.get()} queue=${queue.size}"
                )
            } else if (!stopped.get()) {
                LogContext.log.i(
                    TAG,
                    "Playback completed: submitted=${submittedFrameCount.get()} " +
                        "played=${playedFrameCount.get()}"
                )
            }
            endCallback.invoke()
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        ioScope.cancel()
        closeInputFile()
        audioTrackPlayer.release()
        decoder?.release()
        queue.clear()
    }

    private fun closeInputFile() {
        if (!::rf.isInitialized) return
        try {
            rf.close()
        } catch (e: IOException) {
            if (!stopped.get()) LogContext.log.e(TAG, "Close OPUS input failed", e)
        }
    }
}

private data class OpusPlaybackInput(
    val reader: OpusFramedFileReader,
    val firstAudioFramePosition: Long,
)
