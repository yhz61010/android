package com.leovp.leoandroidbaseutil.basic_components.examples.audio

import android.media.AudioFormat
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.leovp.androidbase.exts.toast
import com.leovp.androidbase.utils.file.FileUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.audio.AudioPlayer
import com.leovp.audio.MicRecorder
import com.leovp.audio.aac.AacFilePlayer
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.receiver.AudioReceiver
import com.leovp.leoandroidbaseutil.basic_components.examples.audio.sender.AudioSender
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import kotlinx.android.synthetic.main.activity_audio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI

class AudioActivity : BaseDemonstrationActivity() {
    companion object {
        private const val TAG = "AudioActivity"

        // https://developers.weixin.qq.com/miniprogram/dev/api/media/recorder/RecorderManager.start.html
        val audioEncoderInfo = AudioEncoderInfo(8000, 48000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val audioDecoderInfo = AudioDecoderInfo(8000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val pcmFile by lazy { FileUtil.createFile(this, "audio.pcm") }
    private val aacFile by lazy { FileUtil.createFile(this, "audio.aac") }
    private var pcmOs: BufferedOutputStream? = null
    private var aacOs: BufferedOutputStream? = null

    private var micRecorder: MicRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var aacFilePlayer: AacFilePlayer? = null

    private var audioReceiver: AudioReceiver? = null
    private var audioSender: AudioSender? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)

        AndPermission.with(this)
            .runtime()
            .permission(Permission.RECORD_AUDIO)
            .onGranted {
                toast("Grand recording permission")
            }
            .onDenied { toast("Deny record permission");finish() }
            .start()

        btnRecordPcm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                record(AudioType.PCM)
            } else {
                micRecorder?.stopRecord()
            }
        }

        btnRecordAac.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                record(AudioType.AAC)
            } else {
                micRecorder?.stopRecord()
            }
        }

        btnPlayPCM.setOnCheckedChangeListener { btn, isChecked ->
            var playPcmThread: Thread? = null
            if (isChecked) {
                audioPlayer = AudioPlayer(applicationContext, audioDecoderInfo, AudioType.PCM)
                playPcmThread = Thread {
                    val pcmIs = BufferedInputStream(FileInputStream(pcmFile))
                    pcmIs.use { input ->
                        val bufferSize = 8 shl 10
                        val readBuffer = ByteArray(bufferSize)
                        var readSize: Int
                        while (input.read(readBuffer).also { readSize = it } != -1) {
                            LogContext.log.i(TAG, "PcmPlayer read size[$readSize]")
                            audioPlayer?.play(readBuffer)
                        }
                        runOnUiThread { btn.isChecked = false }
                    }
                }
                playPcmThread.start()
            } else {
                playPcmThread?.interrupt()
                audioPlayer?.release()
            }
        }

        btnPlayAac.setOnCheckedChangeListener { btn, isChecked ->
            if (isChecked) {
                aacFilePlayer = AacFilePlayer(applicationContext, audioDecoderInfo)
                aacFilePlayer?.playAac(aacFile) {
                    runOnUiThread { btn.isChecked = false }
                }
            } else {
                aacFilePlayer?.stop()
            }
        }
    }

    private fun record(type: AudioType) {
        AndPermission.with(this)
            .runtime()
            .permission(Permission.RECORD_AUDIO)
            .onGranted {
                when (type) {
                    AudioType.PCM -> pcmOs = BufferedOutputStream(FileOutputStream(pcmFile))
                    AudioType.AAC -> aacOs = BufferedOutputStream(FileOutputStream(aacFile))
                    else -> Unit
                }
                micRecorder = MicRecorder(audioEncoderInfo, object : MicRecorder.RecordCallback {
                    override fun onRecording(data: ByteArray) {
                        when (type) {
                            AudioType.PCM -> runCatching {
                                LogContext.log.d(TAG, "PCM data[${data.size}]")
                                pcmOs?.write(data)
                            }.onFailure { it.printStackTrace() }
                            AudioType.AAC -> {
                                LogContext.log.i(TAG, "Get encoded AAC Data[${data.size}]")
                                runCatching { aacOs?.write(data) }.onFailure { it.printStackTrace() }
                            }
                            else -> Unit
                        }
                    }

                    override fun onStop(stopResult: Boolean) {
                        runCatching {
                            pcmOs?.flush()
                            pcmOs?.close()
                            aacOs?.flush()
                            aacOs?.close()
                        }.onFailure { it.printStackTrace() }
                    }
                }, type)
                micRecorder?.startRecord()
            }
            .onDenied { Toast.makeText(this, "Deny record permission", Toast.LENGTH_SHORT).show();finish() }
            .start()
    }

    override fun onStop() {
        ioScope.launch { audioReceiver?.stopServer() }
        ioScope.launch { audioSender?.stop() }
        ioScope.launch {
            micRecorder?.stopRecord()
            audioPlayer?.release()
        }
        aacFilePlayer?.stop()
        super.onStop()
    }

    fun onAudioSenderClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val url = URI("ws://${etAudioReceiverIp.text}:10020/ws")
        LogContext.log.w(TAG, "Send to $url")
        audioSender = AudioSender()
        ioScope.launch { audioSender?.start(this@AudioActivity, url) }
    }

    fun onAudioReceiverClick(@Suppress("UNUSED_PARAMETER") view: View) {
        audioReceiver = AudioReceiver()
        ioScope.launch { audioReceiver?.startServer(this@AudioActivity) }
    }
}