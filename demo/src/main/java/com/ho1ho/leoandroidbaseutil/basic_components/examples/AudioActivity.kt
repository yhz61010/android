package com.ho1ho.leoandroidbaseutil.basic_components.examples

import android.media.AudioFormat
import android.os.Bundle
import android.widget.Toast
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.file.FileUtil
import com.ho1ho.audio.base.AudioCodecInfo
import com.ho1ho.audio.player.PcmPlayer
import com.ho1ho.audio.recorder.MicRecorder
import com.ho1ho.audio.recorder.aac.AacEncoder
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import kotlinx.android.synthetic.main.activity_audio.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream

class AudioActivity : BaseDemonstrationActivity() {
    companion object {
        private const val RECORD_TYPE_PCM = 1
        private const val RECORD_TYPE_AAC = 2
    }

    private lateinit var micRecorder: MicRecorder
    private var aacEncoder: AacEncoder? = null
    private lateinit var pcmPlayer: PcmPlayer

    private val audioEncoderCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_IN_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)
    private val audioPlayCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_OUT_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)

    private val pcmFile by lazy { FileUtil.createFile(this, "audio.pcm") }
    private val aacFile by lazy { FileUtil.createFile(this, "audio.aac") }
    private var pcmOs: BufferedOutputStream? = null
    private var aacOs: BufferedOutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)

        btnRecordPcm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                record(RECORD_TYPE_PCM)
            } else {
                micRecorder.stopRecord()
            }
        }

        btnRecordAac.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                aacEncoder = AacEncoder(
                    audioEncoderCodec.sampleRate,
                    audioEncoderCodec.bitrate,
                    audioEncoderCodec.channelCount,
                    object : AacEncoder.AacEncodeCallback {
                        override fun onEncoded(aacData: ByteArray) {
                            LLog.i(ITAG, "AAC Data[${aacData.size}]")
                            runCatching { aacOs?.write(aacData) }.onFailure { it.printStackTrace() }
                        }
                    })
                aacEncoder?.start()
                record(RECORD_TYPE_AAC)
            } else {
                micRecorder.stopRecord()
            }
        }

        btnPlayPCM.setOnCheckedChangeListener { btn, isChecked ->
            var playPcmThread: Thread? = null
            if (isChecked) {
                pcmPlayer = PcmPlayer(applicationContext, audioPlayCodec)
                playPcmThread = Thread {
                    val pcmIs = BufferedInputStream(FileInputStream(pcmFile))
                    pcmIs.use { input ->
                        val bufferSize = 8 shl 10
                        val readBuffer = ByteArray(bufferSize)
                        var readSize: Int
                        while (input.read(readBuffer).also { readSize = it } != -1) {
                            LLog.i(ITAG, "PcmPlayer read size[$readSize]")
                            pcmPlayer.play(readBuffer)
                        }
                        runOnUiThread { btn.isChecked = false }
                    }
                }
                playPcmThread.start()
            } else {
                playPcmThread?.interrupt()
                if (::pcmPlayer.isInitialized) pcmPlayer.release()
            }
        }

        btnPlayAac.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
            } else {
            }
        }
    }

    private fun record(type: Int) {
        AndPermission.with(this)
            .runtime()
            .permission(Permission.RECORD_AUDIO)
            .onGranted {
                when (type) {
                    RECORD_TYPE_PCM -> pcmOs = BufferedOutputStream(FileOutputStream(pcmFile))
                    RECORD_TYPE_AAC -> aacOs = BufferedOutputStream(FileOutputStream(aacFile))
                }
                micRecorder = MicRecorder(audioEncoderCodec, object : MicRecorder.RecordCallback {
                    override fun onRecording(pcmData: ByteArray) {
                        LLog.d(ITAG, "PCM data[${pcmData.size}]")
                        when (type) {
                            RECORD_TYPE_PCM -> runCatching { pcmOs?.write(pcmData) }.onFailure { it.printStackTrace() }
                            RECORD_TYPE_AAC -> aacEncoder?.queue?.offer(pcmData)
                        }
                    }

                    override fun onStop(stopResult: Boolean) {
                        runCatching {
                            pcmOs?.flush()
                            pcmOs?.close()
                            aacOs?.flush()
                            aacOs?.close()
                        }.onFailure { it.printStackTrace() }
                        aacEncoder?.release()
                    }
                })
                micRecorder.startRecord()
            }
            .onDenied { Toast.makeText(this, "Deny record permission", Toast.LENGTH_SHORT).show();finish() }
            .start()
    }

    override fun onStop() {
        if (::micRecorder.isInitialized) micRecorder.stopRecord()
        if (::pcmPlayer.isInitialized) pcmPlayer.release()
        aacEncoder?.release()
        super.onStop()
    }
}