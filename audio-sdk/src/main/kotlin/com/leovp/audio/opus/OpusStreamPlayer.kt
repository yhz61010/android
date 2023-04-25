package com.leovp.audio.opus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.bytes.toHexStringLE
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2023/4/14 17:11
 */
class OpusStreamPlayer(private val ctx: Context, private val audioDecoderInfo: AudioDecoderInfo) {
    companion object {
        private const val TAG = "OpusStreamPlayer"
        private const val AUDIO_DATA_QUEUE_CAPACITY = 64
        private const val RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES = 3
        private const val AUDIO_INIT_LATENCY_IN_MS = 1200
        private const val REASSIGN_LATENCY_TIME_THRESHOLD_IN_MS: Long = 5000
        private const val AUDIO_ALLOW_LATENCY_LIMIT_IN_MS = 500
    }

    private var audioLatencyThresholdInMs = AUDIO_INIT_LATENCY_IN_MS

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private var audioManager: AudioManager? = null

    // private var outputFormat: MediaFormat? = null
    private var frameCount = AtomicLong(0)
    private var dropFrameTimes = AtomicLong(0)
    private var playStartTimeInUs: Long = 0
    private val rcvAudioDataQueue = ArrayBlockingQueue<ByteArray>(AUDIO_DATA_QUEUE_CAPACITY)

    private var audioTrack: AudioTrack? = null
    private var audioDecoder: MediaCodec? = null

    private var csd0: ByteArray? = null
    private var csd1: ByteArray? = null
    private var csd2: ByteArray? = null

    private fun initAudioTrack(ctx: Context) {
        runCatching {
            audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val bufferSize =
                AudioTrack.getMinBufferSize(audioDecoderInfo.sampleRate, audioDecoderInfo.channelConfig, audioDecoderInfo.audioFormat)
            val sessionId = audioManager!!.generateAudioSessionId()
            val audioAttributesBuilder = AudioAttributes.Builder().apply {
                // Speaker
                // AudioAttributes.USAGE_MEDIA  AudioAttributes.USAGE_VOICE_COMMUNICATION
                setUsage(AudioAttributes.USAGE_MEDIA)
                // AudioAttributes.CONTENT_TYPE_MUSIC   AudioAttributes.CONTENT_TYPE_SPEECH
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                setLegacyStreamType(AudioManager.STREAM_MUSIC)

                // Earphone
                // AudioAttributes.USAGE_MEDIA         AudioAttributes.USAGE_VOICE_COMMUNICATION
                //                setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                // AudioAttributes.CONTENT_TYPE_MUSIC   AudioAttributes.CONTENT_TYPE_SPEECH
                //                setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                //                setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
            }
            val audioFormat = AudioFormat.Builder().setSampleRate(audioDecoderInfo.sampleRate)
                .setEncoding(audioDecoderInfo.audioFormat)
                .setChannelMask(audioDecoderInfo.channelConfig)
                .build()
            audioTrack = AudioTrack(
                audioAttributesBuilder.build(), audioFormat, bufferSize,
                AudioTrack.MODE_STREAM, sessionId
            )

            if (AudioTrack.STATE_INITIALIZED == audioTrack!!.state) {
                LogContext.log.i(TAG, "Start playing audio...")
                audioTrack!!.play()
            } else {
                LogContext.log.w(TAG, "AudioTrack state is not STATE_INITIALIZED")
            }
        }.onFailure { LogContext.log.e(TAG, "initAudioTrack error msg=${it.message}") }
    }

    @Suppress("unused")
    fun useSpeaker(ctx: Context, on: Boolean) {
        LogContext.log.w(TAG, "useSpeaker=$on")
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (on) {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
        } else {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
        }
    }

    private fun initAudioDecoder(csd0: ByteArray, csd1: ByteArray, csd2: ByteArray) {
        runCatching {
            this.csd0 = csd0
            this.csd1 = csd1
            this.csd2 = csd2
            LogContext.log.i(TAG, "initAudioDecoder: $audioDecoderInfo")
            LogContext.log.i(TAG, "CSD0[${csd0.size}]=${csd0.toHexStringLE()}")
            LogContext.log.i(TAG, "CSD1[${csd1.size}]=${csd1.toHexStringLE()}")
            LogContext.log.i(TAG, "CSD2[${csd2.size}]=${csd2.toHexStringLE()}")
            val csd0BB = ByteBuffer.wrap(csd0)
            val csd1BB = ByteBuffer.wrap(csd1)
            val csd2BB = ByteBuffer.wrap(csd2)
            val mediaFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                audioDecoderInfo.sampleRate,
                audioDecoderInfo.channelCount
            ).apply {
                // Set Codec-specific Data
                setByteBuffer("csd-0", csd0BB)
                setByteBuffer("csd-1", csd1BB)
                setByteBuffer("csd-2", csd2BB)
            }
            audioDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                configure(mediaFormat, null, null, 0)
                // outputFormat = this.outputFormat // option B
                // setCallback(mediaCodecCallback)
                start()
            }
            ioScope.launch {
                runCatching {
                    LogContext.log.w(TAG, "Start playing audio thread...")
                    while (true) {
                        ensureActive()
                        val audioData = rcvAudioDataQueue.poll()
                        if (audioData != null && audioData.isNotEmpty()) {
                            LogContext.log.i(TAG, "Play Opus[${audioData.size}]")
                            decodeAndPlay(audioData)
                        }
                        delay(10)
                    }
                }.onFailure { it.printStackTrace() }
            }
        }.onFailure { LogContext.log.e(TAG, "initAudioDecoder error msg=${it.message}") }
    }

    /**
     * If I use asynchronous MediaCodec, most of time in my phone(HuaWei Honor V20),
     * it will not play sound due to MediaCodec state error.
     */
    private fun decodeAndPlay(audioData: ByteArray) {
        try {
            val decoder: MediaCodec? = audioDecoder
            requireNotNull(decoder) { "Opus decoder must not be null." }

            val bufferInfo = MediaCodec.BufferInfo()

            // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
            val inputIndex: Int = decoder.dequeueInputBuffer(0)
            if (inputIndex > -1) {
                decoder.getInputBuffer(inputIndex)?.run {
                    // Clear exist data.
                    clear()
                    // Put pcm audio data to encoder.
                    put(audioData)
                }
                val pts = computePresentationTimeUs(frameCount.incrementAndGet())
                decoder.queueInputBuffer(inputIndex, 0, audioData.size, pts, 0)
            }

            // Start decoding and get output index
            var outputIndex: Int = decoder.dequeueOutputBuffer(bufferInfo, 0)
            // Get decoded data in bytes
            while (outputIndex >= 0) {
                val chunkPCM = ByteArray(bufferInfo.size)
                decoder.getOutputBuffer(outputIndex)?.run { get(chunkPCM) }
                // Must clear decoded data before next loop. Otherwise, you will get the same data while looping.
                if (chunkPCM.isNotEmpty()) {
                    if (audioTrack == null || AudioTrack.STATE_UNINITIALIZED == audioTrack?.state) return
                    if (AudioTrack.PLAYSTATE_PLAYING == audioTrack?.playState) {
                        LogContext.log.i(TAG, "Play PCM[${chunkPCM.size}]")
                        // Play decoded audio data in PCM
                        audioTrack?.write(chunkPCM, 0, chunkPCM.size)
                    }
                }
                decoder.releaseOutputBuffer(outputIndex, false)
                // Get data again.
                outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
            LogContext.log.e(TAG, "You can ignore this message safely. decodeAndPlay error", e)
        }
    }

    // private val mediaCodecCallback = object : MediaCodec.Callback() {
    //     override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
    //         try {
    //             val inputBuffer = codec.getInputBuffer(inputBufferId)
    //             // fill inputBuffer with valid data
    //             inputBuffer?.clear()
    //             val data = rcvAudioDataQueue.poll()?.also {
    //                 inputBuffer?.put(it)
    //             }
    //             val dataSize = data?.size ?: 0
    //             val pts = computePresentationTimeUs(frameCount.incrementAndGet())
    //             //                if (BuildConfig.DEBUG) {
    //             //                    LogContext.log.d(TAG, "Data len=$dataSize\t pts=$pts")
    //             //                }
    //             codec.queueInputBuffer(inputBufferId, 0, dataSize, pts, 0)
    //         } catch (e: Exception) {
    //             LogContext.log.e(TAG, "Audio Player onInputBufferAvailable error. msg=${e.message}")
    //         }
    //     }
    //
    //     override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
    //         try {
    //             val outputBuffer = codec.getOutputBuffer(outputBufferId)
    //             // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
    //             // bufferFormat is equivalent to member variable outputFormat
    //             // outputBuffer is ready to be processed or rendered.
    //             outputBuffer?.let {
    //                 val decodedData = ByteArray(info.size)
    //                 it.get(decodedData)
    //                 //                LogContext.log.w(TAG, "PCM[${decodedData.size}]")
    //                 when (info.flags) {
    //                     MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
    //                         LogContext.log.w(TAG, "Found CSD0 frame: ${JsonUtil.toJsonString(decodedData)}")
    //                     }
    //
    //                     MediaCodec.BUFFER_FLAG_END_OF_STREAM -> Unit
    //                     MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> Unit
    //                     else -> Unit
    //                 }
    //                 if (decodedData.isNotEmpty()) {
    //                     // Play decoded audio data in PCM
    //                     audioTrack?.write(decodedData, 0, decodedData.size)
    //                 }
    //             }
    //             codec.releaseOutputBuffer(outputBufferId, false)
    //         } catch (e: Exception) {
    //             LogContext.log.e(TAG, "Audio Player onOutputBufferAvailable error. msg=${e.message}")
    //         }
    //     }
    //
    //     override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
    //         LogContext.log.w(TAG, "onOutputFormatChanged format=$format")
    //         // Subsequent data will conform to new format.
    //         // Can ignore if using getOutputFormat(outputBufferId)
    //         outputFormat = format // option B
    //     }
    //
    //     override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
    //         e.printStackTrace()
    //         LogContext.log.e(TAG, "onError e=${e.message}", e)
    //     }
    // }

    fun startPlayingStream(audioData: ByteArray, dropFrameCallback: () -> Unit) {
        // We should use a better way to check csd0 (Identification Header)
        if (csd0 == null && audioData.size == 83) {
            LogContext.log.w(TAG, "Found audio config data[${audioData.size}].")
            runCatching {
                synchronized(this) {
                    audioDecoder?.run {
                        LogContext.log.w(TAG, "Found exist OPUS Audio Decoder. Release it first")
                        stop()
                        release()
                    }
                    audioTrack?.run {
                        LogContext.log.w(TAG, "Found exist AudioTrack. Release it first")
                        stop()
                        release()
                    }
                    frameCount.set(0)
                    csd0 = audioData.copyOfRange(16, 16 + 19)
                    csd1 = audioData.copyOfRange(16 + 19 + 16, 16 + 19 + 16 + 8)
                    csd2 = audioData.copyOfRange(16 + 19 + 16 + 8 + 16, audioData.size)
                    initAudioDecoder(csd0!!, csd1!!, csd2!!)
                    initAudioTrack(ctx)
                    playStartTimeInUs = SystemClock.elapsedRealtimeNanos() / 1000
                    ioScope.launch {
                        delay(REASSIGN_LATENCY_TIME_THRESHOLD_IN_MS)
                        audioLatencyThresholdInMs = AUDIO_ALLOW_LATENCY_LIMIT_IN_MS
                        LogContext.log.w(TAG, "Change latency limit to $AUDIO_ALLOW_LATENCY_LIMIT_IN_MS")
                    }
                    LogContext.log.w(TAG, "Play audio at: $playStartTimeInUs")
                }
            }.onFailure { LogContext.log.e(TAG, "startPlayingStream error. msg=${it.message}") }
            return
        }
        if (csd0 == null) {
            LogContext.log.e(TAG, "OPUS header not found")
            return
        }
        val latencyInMs = (SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000 - getAudioTimeUs() / 1000
        LogContext.log.d(
            TAG,
            "st=$playStartTimeInUs\t cal=${(SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000}\t " +
                "play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
        )
        if (rcvAudioDataQueue.size >= AUDIO_DATA_QUEUE_CAPACITY || kotlin.math.abs(latencyInMs) > audioLatencyThresholdInMs) {
            dropFrameTimes.incrementAndGet()
            LogContext.log.w(
                TAG,
                "Drop[${dropFrameTimes.get()}]|full[${rcvAudioDataQueue.size}] " +
                    "latency[$latencyInMs] play=${getAudioTimeUs() / 1000}"
            )
            rcvAudioDataQueue.clear()
            frameCount.set(0)
            runCatching { audioDecoder?.flush() }.getOrNull()
            runCatching { audioTrack?.pause() }.getOrNull()
            runCatching { audioTrack?.flush() }.getOrNull()
            runCatching { audioTrack?.play() }.getOrNull()
            if (dropFrameTimes.get() >= RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES) {
                // If drop frame times exceeds RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES-1 times, we need to do sync again.
                dropFrameTimes.set(0)
                dropFrameCallback.invoke()
            }
            playStartTimeInUs = SystemClock.elapsedRealtimeNanos() / 1000
        }
        if (frameCount.get() % 50 == 0L) {
            LogContext.log.i(TAG, "AU[${audioData.size}][$latencyInMs]")
        }
        rcvAudioDataQueue.offer(audioData)
    }

    fun stopPlaying() {
        LogContext.log.w(TAG, "Stop playing audio")
        runCatching {
            ioScope.cancel()
            rcvAudioDataQueue.clear()
            frameCount.set(0)
            dropFrameTimes.set(0)
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        }.onFailure {
            LogContext.log.e(TAG, "audioTrack stop or release error. msg=${it.message}")
        }.also {
            audioTrack = null
        }

        LogContext.log.w(TAG, "Releasing AudioDecoder...")
        runCatching {
            // These are the magic lines for Samsung phone. DO NOT try to remove or refactor me.
            audioDecoder?.setCallback(null)
            audioDecoder?.release()
        }.onFailure {
            it.printStackTrace()
            LogContext.log.e(TAG, "audioDecoder() release1 error. msg=${it.message}")
        }.also {
            audioDecoder = null
            csd0 = null
            csd1 = null
            csd2 = null
        }

        LogContext.log.w(TAG, "stopPlaying() done")
    }

    @Suppress("unused")
    fun getPlayState() = audioTrack?.playState ?: AudioTrack.PLAYSTATE_STOPPED

    private fun computePresentationTimeUs(frameIndex: Long) =
        frameIndex * 1_000_000 / audioDecoderInfo.sampleRate

    private fun getAudioTimeUs(): Long = runCatching {
        val numFramesPlayed: Int = audioTrack?.playbackHeadPosition ?: 0
        numFramesPlayed * 1_000_000L / audioDecoderInfo.sampleRate
    }.getOrDefault(0L)
}
