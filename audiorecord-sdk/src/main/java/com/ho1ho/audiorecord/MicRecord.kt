package com.ho1ho.audiorecord

import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.media.PcmToWavUtil
import com.ho1ho.audiorecord.aac.AudioEncoder
import com.ho1ho.audiorecord.bean.PcmBean
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Author: Michael Leo
 * Date: 20-6-18 下午1:58
 */
class MicRecord(
    private val sampleRate: Int,
    private val bitrate: Int,
    private val channelCount: Int,
    private val channelMask: Int,
    private val audioFormat: Int
) {
    private val mThreadPool = Executors.newFixedThreadPool(4)

    @Volatile
    var isRecording = false
        private set
    private var mAacEncoder: AudioEncoder = AudioEncoder(sampleRate, bitrate, channelCount)
    private lateinit var audioRecord: AudioRecord
        private set
    private var recordBufferSize: Int = 0

    private var recCallback: Callback? = null
    private val mPcmQueue: ConcurrentLinkedQueue<PcmBean> = ConcurrentLinkedQueue()

    // ==================================
    // Debug only
    // ==================================
    private var pcmOs: BufferedOutputStream? = null
    private var pcmFile: File? = null
    private var wavFile: File? = null
    // ==================================

    init {
        createAudioRecord(sampleRate, channelMask, audioFormat, AudioRecord.getMinBufferSize(sampleRate, channelMask, audioFormat))
    }

    private fun createAudioRecord(
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        recordBufferSize: Int
    ) {
        val bufferSizeInBytes = max(BUFFER_SIZE_FACTOR * recordBufferSize, 0)
        this.recordBufferSize = bufferSizeInBytes
        CLog.i(
            TAG, """createAudioRecord
                    sampleRate=$sampleRate
                    channelConfig=$channelConfig
                    audioFormat=$audioFormat
                    recordBufferSize=$recordBufferSize
                    realBufferSize=$bufferSizeInBytes
                    """.trim()
        )
        audioRecord = AudioRecord(
            // MediaRecorder.AudioSource.MIC
            // MediaRecorder.AudioSource.VOICE_COMMUNICATION
            // MediaRecorder.AudioSource.CAMCORDER
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSizeInBytes
        ) // https://blog.csdn.net/lavender1626/article/details/80394253
    }

    private fun initAdvancedFeatures() {
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(audioRecord.audioSessionId)?.let {
                CLog.w(TAG, "Enable AcousticEchoCanceler")
                it.enabled = true
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(audioRecord.audioSessionId)?.let {
                CLog.w(TAG, "Enable AutomaticGainControl")
                it.enabled = true
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioRecord.audioSessionId)?.let {
                CLog.w(TAG, "Enable NoiseSuppressor")
                it.enabled = true
            }
        }
    }

    fun stop() {
        try {
            CLog.i(TAG, "stop()")
            isRecording = false
            try {
                audioRecord.stop()
            } catch (e: Exception) {
                CLog.e(TAG, e)
            }
            if (BuildConfig.DEBUG) {
                pcmOs!!.flush()
                pcmOs!!.close()
                PcmToWavUtil.pcmToWav(pcmFile!!, wavFile!!, channelCount, sampleRate, if (audioFormat == 2) 16 else 8)
            }
            mAacEncoder.stop()
            mPcmQueue.clear()
        } catch (e: Exception) {
            CLog.e(TAG, "stop error", e)
        }
    }

    fun release() {
        try {
            CLog.i(TAG, "release()")
            stop()
            audioRecord.release()
            mAacEncoder.release()
            mThreadPool?.shutdownNow()
        } catch (e: Exception) {
            CLog.e(TAG, "release error", e)
        }
    }

    fun doRecord(callback: Callback?) {
        recCallback = callback
        audioRecord.startRecording()
        isRecording = true
        mThreadPool!!.execute {
            try {
                val pcmData = ShortArray(recordBufferSize / 2)
                if (BuildConfig.DEBUG) {
                    val outputFolder: String = "/sdcard" + File.separator + "leo-audio"
                    val folder = File(outputFolder)
                    if (!folder.exists()) {
                        val mkdirStatus = folder.mkdirs()
                        CLog.i(TAG, "mkdir [$outputFolder] $mkdirStatus")
                    }
                    pcmFile = File(outputFolder, "original.pcm")
                    wavFile = File(outputFolder, "original.wav")
                    val pcmFilename = pcmFile!!.absolutePath
                    pcmOs = BufferedOutputStream(FileOutputStream(pcmFilename), 32 * 1024)
                }
                var readPcmSize: Int
                var rst: Long
                var red: Long
                encodePcmToAacThread()
                while (isRecording) {
                    rst = SystemClock.elapsedRealtime()
                    readPcmSize = audioRecord.read(pcmData, 0, pcmData.size)
                    red = SystemClock.elapsedRealtime()
                    CLog.d(TAG, "AudioRecord.cost=${red - rst} ReadSize=${readPcmSize * 2} PCM-16bit[${pcmData.size}]")
                    mPcmQueue.offer(PcmBean(pcmData, readPcmSize))

//                    val oriPcmDataInBytes = ByteArray(readPcmSize * 2)
//                    for (i in pcmData.indices) {
//                        oriPcmDataInBytes[i * 2] = (pcmData[i].toInt() and 0xFF).toByte()
//                        oriPcmDataInBytes[i * 2 + 1] = (pcmData[i].toInt() ushr 8 and 0xFF).toByte()
//                    }
//                    pcmOs!!.write(oriPcmDataInBytes)
                }
            } catch (e: Exception) {
                CLog.e(TAG, e)
            }
        }
    }

    private fun encodePcmToAacThread() {
        mThreadPool!!.execute {
            try {
                var est: Long
                while (isRecording) {
                    est = SystemClock.elapsedRealtime()
                    if (mPcmQueue.isEmpty()) {
                        continue
                    }
                    val pcmBean = mPcmQueue.poll()
                    if (pcmBean == null) {
                        CLog.e(TAG, "pcmBean is queue is null.")
                        continue
                    }
                    val oriPcmData = pcmBean.pcm
                    val readSize = pcmBean.readSize
                    val oriPcmDataInBytes = ByteArray(readSize * 2)
                    if (BuildConfig.DEBUG) {
                        // Output original pcm for debug
                        for (i in oriPcmData.indices) {
                            oriPcmDataInBytes[i * 2] = (oriPcmData[i].toInt() and 0xFF).toByte()
                            oriPcmDataInBytes[i * 2 + 1] = (oriPcmData[i].toInt() ushr 8 and 0xFF).toByte()
                        }
                        pcmOs!!.write(oriPcmDataInBytes)
                    }
                    recCallback!!.onCallback(oriPcmDataInBytes)
                    CLog.d(TAG, "Encode while-End cost=${SystemClock.elapsedRealtime() - est}")
                } // end while
                if (BuildConfig.DEBUG) {
                    pcmOs!!.flush()
                    pcmOs!!.close()
                    PcmToWavUtil.pcmToWav(pcmFile!!, wavFile!!, channelCount, sampleRate, if (audioFormat == 2) 16 else 8)
                }
            } catch (e: Exception) {
                CLog.e(TAG, "Encode AAC error.", e)
            }
        }
    }

    val recordingState: Int
        get() = audioRecord.recordingState

    interface Callback {
        fun onCallback(data: ByteArray?)
    }

    val encoder: AudioEncoder?
        get() = mAacEncoder

    companion object {
        private const val TAG = "REC"
        const val ENCODE_MODE_NONE = 0
        const val ENCODE_MODE_AAC = 1

        // We ask for a native buffer size of BUFFER_SIZE_FACTOR * (minimum required
        // buffer size). The extra space is allocated to guard against glitches under
        // high load.
        private const val BUFFER_SIZE_FACTOR = 1
    }
}