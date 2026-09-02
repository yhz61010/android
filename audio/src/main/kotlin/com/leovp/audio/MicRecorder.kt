@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.audio

import com.leovp.audio.base.runCatchingPreservingCancellation

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /**
     * Lets repeated suspend stop calls wait for an error-triggered teardown already in progress.
     */
    private val releaseCompleted = CompletableDeferred<Unit>()

    init {
        require(recordMinBufferRatio > 0) { "recordMinBufferRatio must be positive" }
        val platformMinBufferSize = AudioRecord.getMinBufferSize(
            encoderInfo.sampleRate,
            encoderInfo.channelConfig,
            encoderInfo.audioFormat
        )
        require(platformMinBufferSize > 0) {
            "Invalid AudioRecord parameters: $encoderInfo (code=$platformMinBufferSize)"
        }
        val computedBufferSize = platformMinBufferSize.toLong() * recordMinBufferRatio
        require(computedBufferSize <= Int.MAX_VALUE) { "AudioRecord buffer size overflow" }
        bufferSizeInBytes = computedBufferSize.toInt()
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
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            failRecordStart("AudioRecord is not initialized")
            return
        }
        try {
            audioRecord.startRecording()
        } catch (e: SecurityException) {
            LogContext.log.e(TAG, "RECORD_AUDIO permission denied", e)
            failRecordStart("AudioRecord start failed")
            return
        } catch (e: IllegalStateException) {
            LogContext.log.e(TAG, "AudioRecord start failed", e)
            failRecordStart("AudioRecord start failed")
            return
        }
        if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            failRecordStart("AudioRecord did not enter the recording state")
            return
        }
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
                            // AudioRecord.stop() wakes a blocked read with a negative status on
                            // some devices. It is an expected shutdown signal after `stopped` is
                            // set, not a recording failure.
                            if (stopped.get()) break
                            LogContext.log.e(TAG, "AudioRecord.read error=$recordSize")
                            handleRecordingFailure()
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogContext.log.e(TAG, "Recording loop failed", e)
                handleRecordingFailure()
            }
        }
    }

    private suspend fun handleRecordingFailure() {
        stopped.set(true)
        stopAudioRecord()
        finishRecorderReleaseAndJoin(stopSucceeded = false)
    }

    private fun failRecordStart(message: String) {
        LogContext.log.e(TAG, message)
        stopped.set(true)
        ioScope.cancel()
        stopAudioRecord()
        finishRecorderRelease(stopSucceeded = false)
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
     * the record job and release resources. This API must be called by an external owner.
     */
    suspend fun stopRecordAndJoin() {
        // Guard BEFORE any side effect: a self-call (from the record job's own context) must fail
        // fast without half-stopping the recorder or consuming the one-shot `stopped` flag
        // (remediation R-8). NOTE: this rejects only a *direct* self-call; a call wrapped in
        // runBlocking on the record thread has a different Job and still cannot be made safe here.
        val job = recordJob
        require(job !== currentCoroutineContext()[Job]) {
            "stopRecordAndJoin() must be called by an external owner"
        }
        if (!stopped.compareAndSet(false, true)) {
            releaseCompleted.await()
            return
        }
        LogContext.log.i(TAG, "Stop recording audio")
        val ok = stopAudioRecord()

        job?.cancelAndJoin()
        recordJob = null
        ioScope.cancel()
        finishRecorderReleaseAndJoin(ok)
    }

    /**
     * Legacy non-suspend entry point. It keeps the historical synchronous release semantics but
     * cannot wait for the worker to finish; use [stopRecordAndJoin] when completion matters.
     */
    @Deprecated(
        "Non-suspend stop cannot guarantee the record job has exited. " +
            "Use stopRecordAndJoin() for deterministic shutdown.",
        ReplaceWith("stopRecordAndJoin()")
    )
    fun stopRecord() {
        if (!stopped.compareAndSet(false, true)) return
        val ok = stopAudioRecord()
        recordJob?.cancel()
        recordJob = null
        ioScope.cancel()
        finishRecorderRelease(ok)
    }

    /**
     * Idempotent resource release: releases AudioRecord and encoder, and delivers onStop exactly
     * once. Safe to call from either the record job's error path or the teardown flow.
     */
    private fun stopAudioRecord(): Boolean {
        var ok = true
        runCatchingPreservingCancellation {
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord.stop()
            }
        }.onFailure {
            ok = false
            LogContext.log.e(TAG, "stop error", it)
        }
        return ok
    }

    private fun finishRecorderRelease(stopSucceeded: Boolean) {
        if (!released.compareAndSet(false, true)) return
        var ok = stopSucceeded
        try {
            ok = releaseAudioRecord(ok)
            runCatchingPreservingCancellation { encodeWrapper?.release() }.onFailure {
                ok = false
                LogContext.log.e(TAG, "encoder release error", it)
            }
        } finally {
            completeRecorderRelease(ok)
        }
    }

    private suspend fun finishRecorderReleaseAndJoin(stopSucceeded: Boolean) {
        if (!released.compareAndSet(false, true)) {
            releaseCompleted.await()
            return
        }
        withContext(NonCancellable) {
            var ok = stopSucceeded
            try {
                ok = releaseAudioRecord(ok)
                try {
                    encodeWrapper?.releaseAndJoin()
                } catch (e: CancellationException) {
                    ok = false
                    throw e
                } catch (e: Exception) {
                    ok = false
                    LogContext.log.e(TAG, "encoder release error", e)
                }
            } finally {
                completeRecorderRelease(ok)
            }
        }
    }

    private fun releaseAudioRecord(currentResult: Boolean): Boolean {
        var ok = currentResult
        runCatchingPreservingCancellation {
            audioRecord.release()
            LogContext.log.w(TAG, "Recording released.")
        }.onFailure {
            ok = false
            LogContext.log.e(TAG, "release error", it)
        }
        return ok
    }

    private fun completeRecorderRelease(result: Boolean) {
        try {
            notifyStopOnce(result)
        } finally {
            releaseCompleted.complete(Unit)
        }
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
