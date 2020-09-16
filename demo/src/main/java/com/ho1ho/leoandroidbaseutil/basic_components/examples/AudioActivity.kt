package com.ho1ho.leoandroidbaseutil.basic_components.examples

import android.media.AudioFormat
import android.os.Bundle
import android.widget.Toast
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.file.FileUtil
import com.ho1ho.audio.player.PcmPlayer
import com.ho1ho.audio.recorder.MicRecorder
import com.ho1ho.audio.recorder.bean.AudioCodecInfo
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
    private lateinit var micRecorder: MicRecorder
    private lateinit var pcmPlayer: PcmPlayer

    private val audioEncoderCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_IN_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)
    private val audioPlayCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_OUT_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)

    private val pcmFile by lazy { FileUtil.createFile(this, "pcm.pcm") }
    private var pcmOs: BufferedOutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)

        btnRecordPcm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AndPermission.with(this)
                    .runtime()
                    .permission(Permission.RECORD_AUDIO)
                    .onGranted {
                        micRecorder = MicRecorder(audioEncoderCodec, object : MicRecorder.RecordCallback {
                            override fun onRecording(pcmData: ByteArray) {
                                LLog.d(ITAG, "PCM data[${pcmData.size}]")
                                runCatching { pcmOs?.write(pcmData) }.onFailure { it.printStackTrace() }
                            }

                            override fun onStop(stopResult: Boolean) {
                                runCatching {
                                    pcmOs?.flush()
                                    pcmOs?.close()
                                }.onFailure { it.printStackTrace() }
                            }
                        })

                        pcmOs = BufferedOutputStream(FileOutputStream(pcmFile))
                        micRecorder.startRecord()
                    }
                    .onDenied { Toast.makeText(this, "Deny record permission", Toast.LENGTH_SHORT).show();finish() }
                    .start()
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
    }

    override fun onStop() {
        if (::micRecorder.isInitialized) micRecorder.stopRecord()
        if (::pcmPlayer.isInitialized) pcmPlayer.release()
        super.onStop()
    }
}