package com.leovp.audio.aac

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.SystemClock
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.bytes.toHexStringLE
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 20-8-20 下午5:18
 */
class AacStreamPlayer(private val ctx: Context, private val audioDecoderInfo: AudioDecoderInfo) {
    companion object {
        private const val TAG = "AacStreamPlayer"
        private const val PROFILE_AAC_LC = MediaCodecInfo.CodecProfileLevel.AACObjectLC
        private const val AUDIO_DATA_QUEUE_CAPACITY = 10
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

    // ByteBuffer key
    // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
    // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
    // Convert them to binary value: 0b10, 0b100, 0b1
    // According to AAC required, convert theirs values to binary bits:
    // 00010 0100 0001 000
    // The corresponding hex value：
    // 0001 0010 0000 1000
    // So the csd_0 value is 0x12,0x08
    // https://developer.android.com/reference/android/media/MediaCodec
    // AAC CSD: Decoder-specific information from ESDS
    //
    // ByteBuffer key
    // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
    // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
    // Convert them to binary value: 0b10, 0b100, 0b1
    // According to AAC required, convert theirs values to binary bits:
    // 00010 0100 0001 000
    // The corresponding hex value：
    // 0001 0010 0000 1000
    // So the csd_0 value is 0x12,0x08
    // https://developer.android.com/reference/android/media/MediaCodec
    // AAC CSD: Decoder-specific information from ESDS
    private fun initAudioDecoder(csd0: ByteArray) {
        runCatching {
            LogContext.log.i(TAG, "initAudioDecoder: $audioDecoderInfo")
            val csd0BB = ByteBuffer.wrap(csd0)
            audioDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val mediaFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                audioDecoderInfo.sampleRate, audioDecoderInfo.channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, PROFILE_AAC_LC)
                setInteger(MediaFormat.KEY_IS_ADTS, 1)
                // Set ADTS decoder information.
                setByteBuffer("csd-0", csd0BB)
            }
            audioDecoder!!.configure(mediaFormat, null, null, 0)
            //            outputFormat = audioDecoder?.outputFormat // option B
            //            audioDecoder?.setCallback(mediaCodecCallback)
            audioDecoder?.start()
            ioScope.launch {
                runCatching {
                    while (true) {
                        ensureActive()
                        val audioData = rcvAudioDataQueue.poll()
                        //                        if (frameCount.get() % 30 == 0L) {
                        LogContext.log.i(TAG, "Rcv AAC[${audioData?.size}]")
                        //                        }
                        if (audioData != null && audioData.isNotEmpty()) {
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
            val bufferInfo = MediaCodec.BufferInfo()

            // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
            val inputIndex: Int = audioDecoder?.dequeueInputBuffer(0) ?: -1
            if (inputIndex > -1) {
                audioDecoder?.getInputBuffer(inputIndex)?.run {
                    // Clear exist data.
                    clear()
                    // Put pcm audio data to encoder.
                    put(audioData)
                }
                val pts = computePresentationTimeUs(frameCount.incrementAndGet())
                audioDecoder?.queueInputBuffer(inputIndex, 0, audioData.size, pts, 0)
            }

            // Start decoding and get output index
            var outputIndex: Int = audioDecoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            val chunkPCM = ByteArray(bufferInfo.size)
            // Get decoded data in bytes
            while (outputIndex >= 0) {
                audioDecoder?.getOutputBuffer(outputIndex)?.run { get(chunkPCM) }
                // Must clear decoded data before next loop. Otherwise, you will get the same data while looping.
                if (chunkPCM.isNotEmpty()) {
                    if (audioTrack == null || AudioTrack.STATE_UNINITIALIZED == audioTrack?.state) return
                    if (AudioTrack.PLAYSTATE_PLAYING == audioTrack?.playState) {
                        LogContext.log.i(TAG, "Play PCM[${chunkPCM.size}]")
                        // Play decoded audio data in PCM
                        audioTrack?.write(chunkPCM, 0, chunkPCM.size)
                    }
                }
                audioDecoder?.releaseOutputBuffer(outputIndex, false)
                // Get data again.
                outputIndex = audioDecoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
            }
        } catch (e: Exception) {
            LogContext.log.e(TAG, "You can ignore this message safely. decodeAndPlay error")
        }
    }

    fun startPlayingStream(audioData: ByteArray, dropFrameCallback: () -> Unit) {
        // We should use a better way to check CSD0
        if (audioData.size < 10) {
            runCatching {
                synchronized(this) {
                    audioDecoder?.run {
                        LogContext.log.w(TAG, "Found exist AAC Audio Decoder. Release it first")
                        stop()
                        release()
                    }
                    audioTrack?.run {
                        LogContext.log.w(TAG, "Found exist AudioTrack. Release it first")
                        stop()
                        release()
                    }
                    frameCount.set(0)
                    csd0 = byteArrayOf(audioData[audioData.size - 2], audioData[audioData.size - 1])
                    LogContext.log.w(TAG, "Audio csd0=HEX[${csd0?.toHexStringLE()}]")
                    initAudioDecoder(csd0!!)
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
            LogContext.log.e(TAG, "csd0 is null. Can not play!")
            return
        }
        val latencyInMs = (SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000 - getAudioTimeUs() / 1000
        // LogContext.log.d(
        //     TAG,
        //     "st=$playStartTimeInUs\t cal=${(SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000}\t " +
        //         "play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
        // )
        if (rcvAudioDataQueue.size >= AUDIO_DATA_QUEUE_CAPACITY || abs(latencyInMs) > audioLatencyThresholdInMs) {
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
