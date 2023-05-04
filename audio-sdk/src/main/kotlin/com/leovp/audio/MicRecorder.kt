@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import com.leovp.audio.base.AudioEncoderManager
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.bytes.toByteArrayLE
import com.leovp.log.LogContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * **Need following permission:**
 * ```xml
 * <uses-permission android:name="android.permission.RECORD_AUDIO" />
 * ```
 *
 * Author: Michael Leo
 * Date: 20-8-20 下午3:51
 */
@SuppressLint("MissingPermission")
class MicRecorder(
    encoderInfo: AudioEncoderInfo,
    val callback: RecordCallback,
    type: AudioType = AudioType.PCM,
    audioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
    recordMinBufferRatio: Int = 1
) {
    companion object {
        private const val TAG = "MicRec"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    val audioRecord: AudioRecord
    private var bufferSizeInBytes = 0

    private var encodeWrapper: AudioEncoderWrapper?

    init {
        bufferSizeInBytes = AudioRecord.getMinBufferSize(
            encoderInfo.sampleRate,
            encoderInfo.channelConfig,
            encoderInfo.audioFormat
        ) * recordMinBufferRatio
        LogContext.log.w(TAG, "recordAudio=$encoderInfo recordMinBufferRatio=$recordMinBufferRatio bufferSizeInBytes=$bufferSizeInBytes")

        encodeWrapper = AudioEncoderManager.getWrapper(
            type,
            encoderInfo,
            object : OutputCallback {
                override fun output(out: ByteArray) {
                    callback.onRecording(out)
                }
            }
        )
        LogContext.log.w(TAG, "encodeWrapper=$encodeWrapper")

        audioRecord = AudioRecord(
            // MediaRecorder.AudioSource.MIC
            // MediaRecorder.AudioSource.VOICE_COMMUNICATION
            // MediaRecorder.AudioSource.CAMCORDER
            // MediaRecorder.AudioSource.VOICE_COMMUNICATION
            audioSource,
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
                var pcmData = ShortArray(bufferSizeInBytes / 2)
                var st: Long
                var ed: Long
                var recordSize: Int
                while (true) {
                    ensureActive()
                    st = SystemClock.elapsedRealtime()
                    recordSize = audioRecord.read(pcmData, 0, pcmData.size)
                    ed = SystemClock.elapsedRealtime()
                    pcmData = pcmData.copyOfRange(0, recordSize)
                    if (BuildConfig.DEBUG) {
                        LogContext.log.d(TAG, "Record[${pcmData.size * 2}] cost ${ed - st} ms.")
                    }
                    // If you want to reduce latency when transfer real-time audio stream,
                    // please drop the first generated audio.
                    // It will cost almost 200ms due to preparing the first audio data.
                    // For the second and subsequent audio data, it will only cost 40ms-.
                    // if (cost > 100) {
                    //     LogContext.log.w(TAG, "Drop the generated audio data which costs over 100 ms.")
                    //     continue
                    // }
                    encodeWrapper?.encode(pcmData.toByteArrayLE()) ?: callback.onRecording(pcmData.toByteArrayLE())
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
                audioRecord.release()
                LogContext.log.w(TAG, "Recording released.")
            }
        }.onFailure { it.printStackTrace(); stopResult = false }
        encodeWrapper?.release()
        callback.onStop(stopResult)
    }

    @Suppress("unused")
    fun getRecordingState() = audioRecord.recordingState

    interface RecordCallback {
        /**
         * @param data The byte order of [data] is little endian.
         */
        fun onRecording(data: ByteArray)
        fun onStop(stopResult: Boolean)
    }
}
