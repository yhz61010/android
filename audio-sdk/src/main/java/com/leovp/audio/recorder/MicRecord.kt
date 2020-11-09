package com.leovp.audio.recorder

import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import com.leovp.androidbase.exts.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.audio.base.AudioCodecInfo
import kotlinx.coroutines.*

/**
 * Author: Michael Leo
 * Date: 20-8-20 下午3:51
 */
class MicRecorder(encoderInfo: AudioCodecInfo, val callback: RecordCallback, private val recordMinBufferRatio: Int = 1) {
    companion object {
        private const val TAG = "PCM-Player"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private var audioRecord: AudioRecord

    private var bufferSizeInBytes = 0

    init {
        bufferSizeInBytes = AudioRecord.getMinBufferSize(encoderInfo.sampleRate, encoderInfo.channelConfig, encoderInfo.audioFormat) * recordMinBufferRatio
        LogContext.log.w(TAG, "recordAudio=${encoderInfo.toJsonString()} recordMinBufferRatio=$recordMinBufferRatio bufferSizeInBytes=$bufferSizeInBytes")

        audioRecord = AudioRecord(
            // MediaRecorder.AudioSource.MIC
            // MediaRecorder.AudioSource.VOICE_COMMUNICATION
            // MediaRecorder.AudioSource.CAMCORDER
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            encoderInfo.sampleRate,
            encoderInfo.channelConfig,
            encoderInfo.audioFormat,
            bufferSizeInBytes
        )
        // https://blog.csdn.net/lavender1626/article/details/80394253
        initAdvancedFeatures()
    }

    fun startRecord() {
        LogContext.log.w(TAG, "Do startRecord()")
        audioRecord.startRecording()
        ioScope.launch {
            runCatching {
                val pcmData = ByteArray(bufferSizeInBytes)
                var st: Long
                var ed: Long
                var cost: Long
                var recordSize: Int
                while (true) {
                    ensureActive()
                    st = SystemClock.elapsedRealtime()
                    recordSize = audioRecord.read(pcmData, 0, pcmData.size)
                    ed = SystemClock.elapsedRealtime()
                    cost = ed - st
                    if (BuildConfig.DEBUG) {
                        LogContext.log.d(TAG, "Record[$recordSize] cost $cost ms.")
                    }
                    // If you want to reduce latency when transfer real-time audio stream,
                    // please drop the first generated audio.
                    // It will cost almost 200ms due to preparing the first audio data.
                    // For the second and subsequent audio data, it will only cost 40ms-.
                    if (cost > 100) {
                        LogContext.log.w(TAG, "Drop the generate audio data which cost over 100 ms.")
                        continue
                    }
                    callback.onRecording(pcmData, st, ed)
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    private fun initAdvancedFeatures() {
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(audioRecord.audioSessionId)?.run {
                LogContext.log.w(TAG, "Enable AcousticEchoCanceler")
                enabled = true
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(audioRecord.audioSessionId)?.run {
                LogContext.log.w(TAG, "Enable AutomaticGainControl")
                enabled = true
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioRecord.audioSessionId)?.run {
                LogContext.log.w(TAG, "Enable NoiseSuppressor")
                enabled = true
            }
        }
    }

    fun stopRecord() {
        ioScope.cancel()
        LogContext.log.i(TAG, "Stop recording audio")
        var stopResult = true
        runCatching {
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                LogContext.log.i(TAG, "Stopping recording...")
                audioRecord.stop()
            }
        }.onFailure { it.printStackTrace(); stopResult = false }
        runCatching {
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                LogContext.log.w(TAG, "Releasing recording...")
                audioRecord.release()
            }
        }.onFailure { it.printStackTrace(); stopResult = false }
        callback.onStop(stopResult)
    }

    fun getRecordingState() = audioRecord.recordingState

    interface RecordCallback {
        fun onRecording(pcmData: ByteArray, st: Long, ed: Long)
        fun onStop(stopResult: Boolean)
    }
}