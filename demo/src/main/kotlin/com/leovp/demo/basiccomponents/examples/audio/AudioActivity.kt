package com.leovp.demo.basiccomponents.examples.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.os.Bundle
import android.view.View
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.android.exts.createFile
import com.leovp.android.exts.toast
import com.leovp.android.utils.NetworkUtil
import com.leovp.audio.AudioPlayer
import com.leovp.audio.MicRecorder
import com.leovp.audio.aac.AacFilePlayer
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.opus.OpusFilePlayer
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.audio.receiver.AudioReceiver
import com.leovp.demo.basiccomponents.examples.audio.sender.AudioSender
import com.leovp.demo.databinding.ActivityAudioBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioActivity : BaseDemonstrationActivity<ActivityAudioBinding>() {
    override fun getTagName(): String = ITAG

    companion object {
        private const val TAG = "AudioActivity"

        // https://developers.weixin.qq.com/miniprogram/dev/api/media/recorder/RecorderManager.start.html
        // In practice, on the devices I tested on, opus encoder by MediaCodec ONLY supports 48kHz as sample rate.
        // And the frame size seems like 960 samples (20ms).
        val audioEncoderInfo = AudioEncoderInfo(48000, 128000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val audioDecoderInfo = AudioDecoderInfo(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)

        // AudioAttributes.USAGE_VOICE_COMMUNICATION
        // AudioAttributes.USAGE_MEDIA
        const val AUDIO_ATTR_USAGE: Int = AudioAttributes.USAGE_MEDIA

        // AudioAttributes.CONTENT_TYPE_SPEECH
        // AudioAttributes.CONTENT_TYPE_MUSIC
        const val AUDIO_ATTR_CONTENT_TYPE: Int = AudioAttributes.CONTENT_TYPE_MUSIC
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAudioBinding {
        return ActivityAudioBinding.inflate(layoutInflater)
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val pcmFile by lazy { this.createFile("audio.pcm") }
    private val aacFile by lazy { this.createFile("audio.aac") }
    private val opusFile by lazy { this.createFile("audio.opus") }
    private var pcmOs: BufferedOutputStream? = null
    private var aacOs: BufferedOutputStream? = null
    private var opusOs: BufferedOutputStream? = null

    private var recordType: AudioType? = null

    private var micRecorder: MicRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var aacFilePlayer: AacFilePlayer? = null
    private var opusFilePlayer: OpusFilePlayer? = null

    private var audioReceiver: AudioReceiver? = null
    private var audioSender: AudioSender? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        XXPermissions.with(this)
            .permission(Permission.RECORD_AUDIO)
            .request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>, all: Boolean) {
                    toast("Grand recording permission")
                }

                override fun onDenied(denied: MutableList<String>, never: Boolean) {
                    toast("Deny record permission"); finish()
                }
            })

        binding.tvIp.text = NetworkUtil.getIp()[0]

        binding.btnRecordPcm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) record(AudioType.PCM) else micRecorder?.stopRecord()
        }

        binding.btnRecordAac.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) record(AudioType.AAC) else micRecorder?.stopRecord()
        }

        binding.btnRecordOpus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) record(AudioType.OPUS) else micRecorder?.stopRecord()
        }

        var playPcmThread: Thread? = null
        binding.btnPlayPCM.setOnCheckedChangeListener { btn, isChecked ->
            if (isChecked) {
                audioPlayer = AudioPlayer(
                    this@AudioActivity,
                    audioDecoderInfo,
                    type = AudioType.PCM,
                    usage = AUDIO_ATTR_USAGE,
                    contentType = AUDIO_ATTR_CONTENT_TYPE)
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
                playPcmThread?.start()
            } else {
                playPcmThread?.interrupt()
                audioPlayer?.release()
            }
        }

        binding.btnPlayAac.setOnCheckedChangeListener { btn, isChecked ->
            if (isChecked) {
                aacFilePlayer = AacFilePlayer(this@AudioActivity, audioDecoderInfo, AUDIO_ATTR_USAGE, AUDIO_ATTR_CONTENT_TYPE)
                aacFilePlayer?.playAac(aacFile) {
                    runOnUiThread { btn.isChecked = false }
                    // LogContext.log.e(TAG, "=====> End callback <=====")
                }
            } else {
                aacFilePlayer?.stop()
            }
        }

        binding.btnPlayOpus.setOnCheckedChangeListener { btn, isChecked ->
            if (isChecked) {
                opusFilePlayer = OpusFilePlayer(this@AudioActivity, audioDecoderInfo, AUDIO_ATTR_USAGE, AUDIO_ATTR_CONTENT_TYPE)
                opusFilePlayer?.playOpus(opusFile) {
                    runOnUiThread { btn.isChecked = false }
                    // LogContext.log.e(TAG, "=====> End callback <=====")
                }
            } else {
                opusFilePlayer?.stop()
            }
        }
    }

    private val recordCallback = object : MicRecorder.RecordCallback {
        override fun onRecording(data: ByteArray, isConfig: Boolean, isKeyFrame: Boolean) {
            when (recordType) {
                AudioType.PCM -> runCatching {
                    LogContext.log.d(TAG, "PCM data[${data.size}]")
                    pcmOs?.write(data)
                }.onFailure { it.printStackTrace() }

                AudioType.AAC -> {
                    LogContext.log.i(TAG, "Get encoded AAC Data[${data.size}] isConfig=$isConfig isKeyFrame=$isKeyFrame")
                    runCatching { aacOs?.write(data) }.onFailure { it.printStackTrace() }
                }

                AudioType.OPUS -> {
                    LogContext.log.i(TAG, "Get encoded OPUS Data[${data.size}] isConfig=$isConfig isKeyFrame=$isKeyFrame")
                    runCatching {
                        opusOs?.write(OpusFilePlayer.startCode.encodeToByteArray())
                        opusOs?.write(data)
                    }.onFailure { it.printStackTrace() }
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

                opusOs?.flush()
                opusOs?.close()
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun record(type: AudioType) {
        recordType = type
        XXPermissions.with(this)
            .permission(Permission.RECORD_AUDIO)
            .request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>, all: Boolean) {
                    LogContext.log.i(ITAG, "Record type: $type")
                    when (type) {
                        AudioType.PCM -> pcmOs = BufferedOutputStream(FileOutputStream(pcmFile))
                        AudioType.AAC -> aacOs = BufferedOutputStream(FileOutputStream(aacFile))
                        AudioType.OPUS -> opusOs = BufferedOutputStream(FileOutputStream(opusFile))
                        else -> Unit
                    }
                    micRecorder = MicRecorder(audioEncoderInfo, recordCallback, type)
                    micRecorder?.startRecord()
                }

                override fun onDenied(denied: MutableList<String>, never: Boolean) {
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
        opusFilePlayer?.stop()
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
