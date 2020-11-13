package com.leovp.leoandroidbaseutil.basic_components.examples.audio

import android.media.AudioFormat
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.leovp.androidbase.exts.toByteArrayLE
import com.leovp.androidbase.exts.toShortArrayLE
import com.leovp.androidbase.exts.toast
import com.leovp.androidbase.utils.file.FileUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.audio.base.AudioCodecInfo
import com.leovp.audio.player.PcmPlayer
import com.leovp.audio.player.aac.AacFilePlayer
import com.leovp.audio.recorder.MicRecorder
import com.leovp.audio.recorder.aac.AacEncoder
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
        private const val RECORD_TYPE_PCM = 1
        private const val RECORD_TYPE_AAC = 2

        // https://developers.weixin.qq.com/miniprogram/dev/api/media/recorder/RecorderManager.start.html
        val audioPlayCodec = AudioCodecInfo(16000, 48000, AudioFormat.CHANNEL_OUT_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)
        val audioEncoderCodec = AudioCodecInfo(16000, 48000, AudioFormat.CHANNEL_IN_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val pcmFile by lazy { FileUtil.createFile(this, "audio.pcm") }
    private val aacFile by lazy { FileUtil.createFile(this, "audio.aac") }
    private var pcmOs: BufferedOutputStream? = null
    private var aacOs: BufferedOutputStream? = null

    private var micRecorder: MicRecorder? = null
    private var aacEncoder: AacEncoder? = null
    private var pcmPlayer: PcmPlayer? = null
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
                record(RECORD_TYPE_PCM)
            } else {
                micRecorder?.stopRecord()
            }
        }

        btnRecordAac.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                record(RECORD_TYPE_AAC)
            } else {
                micRecorder?.stopRecord()
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
                            LogContext.log.i(TAG, "PcmPlayer read size[$readSize]")
                            pcmPlayer?.play(readBuffer.toShortArrayLE())
                        }
                        runOnUiThread { btn.isChecked = false }
                    }
                }
                playPcmThread.start()
            } else {
                playPcmThread?.interrupt()
                pcmPlayer?.release()
            }
        }

        btnPlayAac.setOnCheckedChangeListener { btn, isChecked ->
            if (isChecked) {
                aacFilePlayer = AacFilePlayer(applicationContext, audioPlayCodec)
                aacFilePlayer?.playAac(aacFile) {
                    runOnUiThread { btn.isChecked = false }
                }
            } else {
                aacFilePlayer?.stop()
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
                    RECORD_TYPE_AAC -> {
                        aacOs = BufferedOutputStream(FileOutputStream(aacFile))
                        aacEncoder = AacEncoder(
                            audioEncoderCodec.sampleRate,
                            audioEncoderCodec.bitrate,
                            audioEncoderCodec.channelCount,
                            object : AacEncoder.AacEncodeCallback {
                                override fun onEncoded(aacData: ByteArray) {
                                    LogContext.log.i(TAG, "Get encoded AAC Data[${aacData.size}]")
                                    runCatching { aacOs?.write(aacData) }.onFailure { it.printStackTrace() }
                                }
                            }).apply { start() }
                    }
                }
                micRecorder = MicRecorder(audioEncoderCodec, object : MicRecorder.RecordCallback {
                    override fun onRecording(pcmData: ShortArray, st: Long, ed: Long) {
                        val pcmBytes = pcmData.toByteArrayLE()
                        LogContext.log.d(TAG, "PCM data[${pcmBytes.size}]")
                        when (type) {
                            RECORD_TYPE_PCM -> runCatching { pcmOs?.write(pcmBytes) }.onFailure { it.printStackTrace() }
                            RECORD_TYPE_AAC -> aacEncoder?.queue?.offer(pcmBytes)
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
                micRecorder?.startRecord()
            }
            .onDenied { Toast.makeText(this, "Deny record permission", Toast.LENGTH_SHORT).show();finish() }
            .start()
    }

    override fun onStop() {
        ioScope.launch {
            audioReceiver?.stopServer()
        }
        ioScope.launch {
            audioSender?.stop()
        }
        ioScope.launch {
            micRecorder?.stopRecord()
            pcmPlayer?.release()
        }
        aacFilePlayer?.stop()
        aacEncoder?.release()
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