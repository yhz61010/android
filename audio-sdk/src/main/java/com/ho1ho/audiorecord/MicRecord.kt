package com.ho1ho.audiorecord

import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.toJsonString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.audiorecord.bean.AudioCodecInfo
import kotlinx.coroutines.*

/**
 * Author: Michael Leo
 * Date: 20-8-20 下午3:51
 */
class MicRecorder(encoderInfo: AudioCodecInfo) {
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private var audioRecord: AudioRecord

    private var bufferSizeInBytes = 0

    init {
        LLog.w(ITAG, "recordAudio=${encoderInfo.toJsonString()}")
        bufferSizeInBytes = AudioRecord.getMinBufferSize(encoderInfo.sampleRate, encoderInfo.channelConfig, encoderInfo.audioFormat)

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

    fun startRecord(f: (audioData: ByteArray) -> Unit) {
        LLog.w(ITAG, "Do startRecord() audioRecord=$audioRecord")
        audioRecord.startRecording()
        ioScope.launch {
            runCatching {
                val pcmData = ByteArray(bufferSizeInBytes)
                while (true) {
                    ensureActive()
                    audioRecord.read(pcmData, 0, pcmData.size)
                    f.invoke(pcmData)
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    private fun initAdvancedFeatures() {
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(audioRecord.audioSessionId)?.run {
                LLog.w(ITAG, "Enable AcousticEchoCanceler")
                enabled = true
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(audioRecord.audioSessionId)?.run {
                LLog.w(ITAG, "Enable AutomaticGainControl")
                enabled = true
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioRecord.audioSessionId)?.run {
                LLog.w(ITAG, "Enable NoiseSuppressor")
                enabled = true
            }
        }
    }

    fun stopRecord(): Boolean {
        LLog.w(ITAG, "Stop recording audio")
        ioScope.cancel()
        runCatching {
            LLog.w(ITAG, "Stopping recording...")
            audioRecord.stop()
        }.onFailure { it.printStackTrace(); return false }
        runCatching {
            LLog.w(ITAG, "Releasing recording...")
            audioRecord.release()
        }.onFailure { it.printStackTrace(); return false }
        return true
    }

    fun getRecordingState() = audioRecord.recordingState
}