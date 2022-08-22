package com.leovp.demo.basiccomponents.examples.audio

import android.media.AudioFormat
import android.os.Bundle
import android.view.View
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.android.exts.toast
import com.leovp.audio.AudioPlayer
import com.leovp.audio.MicRecorder
import com.leovp.audio.aac.AacFilePlayer
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.audio.receiver.AudioReceiver
import com.leovp.demo.basiccomponents.examples.audio.sender.AudioSender
import com.leovp.demo.databinding.ActivityAudioBinding
import com.leovp.android.exts.createFile
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI

class AudioActivity : BaseDemonstrationActivity<ActivityAudioBinding>() {
    override fun getTagName(): String = ITAG

    companion object {
        private const val TAG = "AudioActivity"

        // https://developers.weixin.qq.com/miniprogram/dev/api/media/recorder/RecorderManager.start.html
        val audioEncoderInfo =
            AudioEncoderInfo(
                16000,
                32000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        val audioDecoderInfo =
            AudioDecoderInfo(
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAudioBinding {
        return ActivityAudioBinding.inflate(layoutInflater)
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val pcmFile by lazy { this.createFile("audio.pcm") }
    private val aacFile by lazy { this.createFile("audio.aac") }
    private var pcmOs: BufferedOutputStream? = null
    private var aacOs: BufferedOutputStream? = null

    private var micRecorder: MicRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var aacFilePlayer: AacFilePlayer? = null

    private var audioReceiver: AudioReceiver? = null
    private var audioSender: AudioSender? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        XXPermissions.with(this)
            .permission(Permission.RECORD_AUDIO)
            .request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>?, all: Boolean) {
                    toast("Grand recording permission")
                }

                override fun onDenied(denied: MutableList<String>?, never: Boolean) {
                    toast("Deny record permission"); finish()
                }
            })

        binding.btnRecordPcm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                record(AudioType.PCM)
            } else {
                micRecorder?.stopRecord()
            }
        }

        binding.btnRecordAac.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                record(AudioType.AAC)
            } else {
                micRecorder?.stopRecord()
            }
        }

        binding.btnPlayPCM.setOnCheckedChangeListener { btn, isChecked ->
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

        binding.btnPlayAac.setOnCheckedChangeListener { btn, isChecked ->
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
        XXPermissions.with(this)
            .permission(Permission.RECORD_AUDIO)
            .request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>?, all: Boolean) {
                    when (type) {
                        AudioType.PCM -> pcmOs = BufferedOutputStream(FileOutputStream(pcmFile))
                        AudioType.AAC -> aacOs = BufferedOutputStream(FileOutputStream(aacFile))
                        else -> Unit
                    }
                    micRecorder =
                        MicRecorder(
                            audioEncoderInfo,
                            object : MicRecorder.RecordCallback {
                                override fun onRecording(data: ByteArray) {
                                    when (type) {
                                        AudioType.PCM -> runCatching {
                                            LogContext.log.d(TAG, "PCM data[${data.size}]")
                                            pcmOs?.write(data)
                                        }.onFailure { it.printStackTrace() }
                                        AudioType.AAC -> {
                                            LogContext.log.i(
                                                TAG,
                                                "Get encoded AAC Data[${data.size}]"
                                            )
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
                            },
                            type
                        )
                    micRecorder?.startRecord()
                }

                override fun onDenied(denied: MutableList<String>?, never: Boolean) {
                    toast("Deny record permission")
                    finish()
                }
            })
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
        val uri = URI.create(binding.etAudioReceiverIp.text.toString())
        LogContext.log.w(TAG, "Send to $uri")
        audioSender = AudioSender()
        ioScope.launch { audioSender?.start(this@AudioActivity, uri) }
    }

    fun onAudioReceiverClick(@Suppress("UNUSED_PARAMETER") view: View) {
        audioReceiver = AudioReceiver()
        ioScope.launch { audioReceiver?.startServer(this@AudioActivity) }
    }
}
