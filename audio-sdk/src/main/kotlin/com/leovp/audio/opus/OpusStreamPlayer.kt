package com.leovp.audio.opus

import android.content.Context
import android.media.AudioTrack
import android.os.SystemClock
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2023/4/14 17:11
 */
class OpusStreamPlayer(ctx: Context, private val audioDecoderInfo: AudioDecoderInfo) {
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

    private var frameCount = 0
    private var dropFrameTimes = AtomicLong(0)
    private var playStartTimeInUs: Long = 0

    private val audioTrackPlayer = AudioTrackPlayer(ctx, audioDecoderInfo)
    private var audioDecoder: OpusDecoder? = null

    private var csd0: ByteArray? = null
    private var csd1: ByteArray? = null
    private var csd2: ByteArray? = null

    private fun initAudioDecoder(csd0: ByteArray, csd1: ByteArray, csd2: ByteArray) {
        LogContext.log.i(TAG, "initAudioDecoder: $audioDecoderInfo")
        LogContext.log.i(TAG, "Channel count: ${audioDecoderInfo.channelCount}")
        LogContext.log.i(TAG, "CSD0[${csd0.size}]=${csd0.toHexString()}")
        LogContext.log.i(TAG, "CSD1[${csd1.size}]=${csd1.toHexString()}")
        LogContext.log.i(TAG, "CSD2[${csd2.size}]=${csd2.toHexString()}")
        audioDecoder = OpusDecoder(
            audioDecoderInfo.sampleRate,
            audioDecoderInfo.channelCount,
            audioDecoderInfo.audioFormat,
            csd0,
            csd1,
            csd2,
            object : IDecodeCallback {
                override fun onDecoded(pcmData: ByteArray) {
                    LogContext.log.i(TAG, "onDecoded PCM[${pcmData.size}]")
                    if (pcmData.isNotEmpty()) {
                        if (AudioTrack.STATE_UNINITIALIZED == audioTrackPlayer.state) return
                        if (AudioTrack.PLAYSTATE_PLAYING == audioTrackPlayer.playState) {
                            LogContext.log.i(TAG, "Play PCM[${pcmData.size}]")
                            // Play decoded audio data in PCM
                            audioTrackPlayer.write(pcmData)
                        }
                    }
                }
            }
        ).apply { start() }
    }

    fun startPlayingStream(audioData: ByteArray, dropFrameCallback: () -> Unit) {
        // We should use a better way to check csd0 (Identification Header)
        if (csd0 == null && audioData.size == 83) {
            LogContext.log.w(TAG, "Found audio config data[${audioData.size}].")
            runCatching {
                synchronized(this) {
                    frameCount = 0
                    csd0 = audioData.copyOfRange(16, 16 + 19)
                    csd1 = audioData.copyOfRange(16 + 19 + 16, 16 + 19 + 16 + 8)
                    csd2 = audioData.copyOfRange(16 + 19 + 16 + 8 + 16, audioData.size)
                    initAudioDecoder(csd0!!, csd1!!, csd2!!)
                    audioTrackPlayer.play()
                    playStartTimeInUs = SystemClock.elapsedRealtimeNanos() / 1000
                    ioScope.launch {
                        delay(REASSIGN_LATENCY_TIME_THRESHOLD_IN_MS)
                        audioLatencyThresholdInMs = AUDIO_ALLOW_LATENCY_LIMIT_IN_MS
                        LogContext.log.w(TAG, "Change latency limit to $AUDIO_ALLOW_LATENCY_LIMIT_IN_MS")
                    }
                    LogContext.log.w(TAG, "Play audio at: $playStartTimeInUs")
                }
            }.onFailure { LogContext.log.e(TAG, "startPlayingStream error. msg=${it.message}", it) }
            return
        }
        if (csd0 == null) {
            LogContext.log.e(TAG, "OPUS header not found")
            return
        }
        val elapsedUs = SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs
        val latencyInMs = elapsedUs / 1000 - getAudioTimeUs() / 1000
        LogContext.log.d(
            TAG,
            "st=$playStartTimeInUs\t cal=${(SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000}\t " +
                "play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
        )
        val auDecQueueSize = audioDecoder?.queueSize ?: 0
        if (auDecQueueSize >= AUDIO_DATA_QUEUE_CAPACITY || abs(latencyInMs) > audioLatencyThresholdInMs) {
            dropFrameTimes.incrementAndGet()
            LogContext.log.w(
                TAG,
                "Drop[${dropFrameTimes.get()}]|full[${audioDecoder?.queueSize ?: 0}] " +
                    "latency[$latencyInMs] play=${getAudioTimeUs() / 1000}"
            )
            frameCount = 0
            runCatching { audioDecoder?.flush() }.getOrNull()
            runCatching { audioTrackPlayer.pause() }.getOrNull()
            runCatching { audioTrackPlayer.play() }.getOrNull()
            if (dropFrameTimes.get() >= RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES) {
                // If drop frame times exceeds RESYNC_AUDIO_AFTER_DROP_FRAME_TIMES-1 times, we need to do sync again.
                dropFrameTimes.set(0)
                dropFrameCallback.invoke()
            }
            playStartTimeInUs = SystemClock.elapsedRealtimeNanos() / 1000
        } else {
            audioDecoder!!.decode(audioData)
        }
        if (frameCount++ % 50 == 0) {
            LogContext.log.i(TAG, "AU[${audioData.size}][$latencyInMs]")
        }
    }

    fun stopPlaying() {
        LogContext.log.w(TAG, "Stop playing audio")
        runCatching {
            ioScope.cancel()
            frameCount = 0
            dropFrameTimes.set(0)
            audioTrackPlayer.release()
        }.onFailure {
            LogContext.log.e(TAG, "audioTrack stop or release error. msg=${it.message}")
        }

        LogContext.log.w(TAG, "Releasing AudioDecoder...")
        runCatching {
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
    fun getPlayState() = audioTrackPlayer.playState

    private fun getAudioTimeUs(): Long = runCatching {
        val numFramesPlayed: Int = audioTrackPlayer.playbackHeadPosition
        numFramesPlayed * 1_000_000L / audioDecoderInfo.sampleRate
    }.getOrDefault(0L)
}
