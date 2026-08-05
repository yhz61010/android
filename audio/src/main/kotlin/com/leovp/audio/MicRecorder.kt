@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.leovp.audio.base.AudioEncoderManager
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.bytes.toByteArrayLE
import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
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
    recordMinBufferRatio: Int = 1,
) {
    companion object {
        private const val TAG = "MicRec"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    /** Independent scope for teardown so cancelling [ioScope] cannot kill the release flow. */
    private val teardownScope =
        CoroutineScope(Dispatchers.IO + CoroutineName("mic-recorder-teardown"))

    val audioRecord: AudioRecord
    private var bufferSizeInBytes = 0

    private var encodeWrapper: AudioEncoderWrapper?

    private var recordJob: Job? = null

    /** Guards [stopRecordAndJoin] re-entry. */
    private val stopped = AtomicBoolean(false)

    /** Guards [finishRecorderRelease] so AudioRecord/encoder release runs exactly once. */
    private val released = AtomicBoolean(false)

    /** Guards [callback].onStop so it is delivered exactly once. */
    private val onStopNotified = AtomicBoolean(false)

    init {
        bufferSizeInBytes = AudioRecord.getMinBufferSize(
            encoderInfo.sampleRate,
            encoderInfo.channelConfig,
            encoderInfo.audioFormat
        ) * recordMinBufferRatio
        LogContext.log.w(
            TAG,
            "recordAudio=$encoderInfo recordMinBufferRatio=$recordMinBufferRatio " +
                "bufferSizeInBytes=$bufferSizeInBytes"
        )

        encodeWrapper = AudioEncoderManager.getWrapper(
            type,
            encoderInfo,
            object : OutputCallback {
                override fun output(out: ByteArray, isConfig: Boolean, isKeyFrame: Boolean) {
                    callback.onRecording(out, isConfig, isKeyFrame)
                }
            }
        )
        LogContext.log.w(TAG, "encodeWrapper=$encodeWrapper")

        // MediaRecorder.AudioSource.MIC
        // MediaRecorder.AudioSource.VOICE_COMMUNICATION
        // MediaRecorder.AudioSource.CAMCORDER
        // MediaRecorder.AudioSource.VOICE_COMMUNICATION
        audioRecord = AudioRecord(
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
        recordJob = ioScope.launch {
            try {
                // Keep a fixed reusable capacity; never shrink/reassign this buffer (AUD-6).
                val pcmBuffer = ShortArray(bufferSizeInBytes / 2)
                while (true) {
                    ensureActive()
                    val recordSize = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                    when {
                        recordSize > 0 -> {
                            // Slice only for this frame; the backing buffer stays full-size.
                            val frame = pcmBuffer.copyOfRange(0, recordSize).toByteArrayLE()
                            encodeWrapper?.encode(frame)
                                ?: callback.onRecording(frame, isConfig = false, isKeyFrame = false)
                        }

                        recordSize == 0 -> continue

                        else -> {
                            LogContext.log.e(TAG, "AudioRecord.read error=$recordSize")
                            finishRecorderRelease(stopSucceeded = false)
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogContext.log.e(TAG, "Recording loop failed", e)
                finishRecorderRelease(stopSucceeded = false)
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

    /**
     * Deterministic teardown: stop the AudioRecord first (so the native read returns), then join
     * the record job and release resources. Guards against self-join when called from the record
     * job itself.
     */
    suspend fun stopRecordAndJoin() {
        if (!stopped.compareAndSet(false, true)) return
        LogContext.log.i(TAG, "Stop recording audio")
        var ok = true
        // Stop first so the blocking native read() returns and the loop can exit.
        runCatching {
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) audioRecord.stop()
        }.onFailure { ok = false; LogContext.log.e(TAG, "stop error", it) }

        val job = recordJob
        if (job === currentCoroutineContext()[Job]) {
            // Called from within the record job; never join on ourselves.
            job.cancel()
            teardownScope.launch { job.join(); finishRecorderRelease(ok) }
            return
        }
        job?.cancelAndJoin()
        recordJob = null
        ioScope.cancel()
        finishRecorderRelease(ok)
    }

    /**
     * Legacy non-suspend entry point. Bridges to [stopRecordAndJoin] on an independent scope so it
     * never blocks (or runBlocking-s) the caller thread (e.g. the UI thread).
     */
    @Deprecated(
        "Non-suspend stop cannot guarantee the record job has exited. " +
            "Use stopRecordAndJoin() for deterministic shutdown.",
        ReplaceWith("stopRecordAndJoin()")
    )
    fun stopRecord() {
        teardownScope.launch { stopRecordAndJoin() }
    }

    /**
     * Idempotent resource release: releases AudioRecord and encoder, and delivers onStop exactly
     * once. Safe to call from either the record job's error path or the teardown flow.
     */
    private fun finishRecorderRelease(stopSucceeded: Boolean) {
        if (!released.compareAndSet(false, true)) return
        var ok = stopSucceeded
        runCatching {
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                LogContext.log.w(TAG, "Recording released.")
            }
        }.onFailure { ok = false; LogContext.log.e(TAG, "release error", it) }
        encodeWrapper?.release()
        notifyStopOnce(ok)
    }

    private fun notifyStopOnce(result: Boolean) {
        if (onStopNotified.compareAndSet(false, true)) callback.onStop(result)
    }

    @Suppress("unused")
    fun getRecordingState() = audioRecord.recordingState

    interface RecordCallback {
        /**
         * @param data The byte order of [data] is little endian.
         */
        fun onRecording(data: ByteArray, isConfig: Boolean, isKeyFrame: Boolean)
        fun onStop(stopResult: Boolean)
    }
}
