package com.leovp.demo.basiccomponents.examples.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.os.Bundle
import android.view.View
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
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
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.audio.receiver.AudioReceiver
import com.leovp.demo.basiccomponents.examples.audio.sender.AudioSender
import com.leovp.demo.databinding.ActivityAudioBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.log.base.d
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioActivity : BaseDemonstrationActivity<ActivityAudioBinding>(R.layout.activity_audio) {
    override fun getTagName(): String = ITAG

    companion object {
        private const val TAG = "AudioActivity"
        private const val SAMPLE_RATE = 48000
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // https://developers.weixin.qq.com/miniprogram/dev/api/media/recorder/RecorderManager.start
        // .html
        // Check comments in OpusEncoder.kt file.
        val audioEncoderInfo = AudioEncoderInfo(
            sampleRate = SAMPLE_RATE,
            bitrate = 128000,
            channelConfig = AudioFormat.CHANNEL_IN_MONO,
            audioFormat = AUDIO_FORMAT
        )
        val audioDecoderInfo = AudioDecoderInfo(
            sampleRate = SAMPLE_RATE,
            channelConfig = AudioFormat.CHANNEL_OUT_MONO,
            audioFormat = AUDIO_FORMAT
        )

        // AudioAttributes.USAGE_VOICE_COMMUNICATION
        // AudioAttributes.USAGE_MEDIA
        const val AUDIO_ATTR_USAGE: Int = AudioAttributes.USAGE_MEDIA

        // AudioAttributes.CONTENT_TYPE_SPEECH
        // AudioAttributes.CONTENT_TYPE_MUSIC
        const val AUDIO_ATTR_CONTENT_TYPE: Int = AudioAttributes.CONTENT_TYPE_MUSIC
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAudioBinding =
        ActivityAudioBinding.inflate(layoutInflater)

    private val ioScopeJob = SupervisorJob()
    private val ioExceptionHandler = CoroutineExceptionHandler { _, error ->
        LogContext.log.e(TAG, "Unhandled audio demo coroutine failure", error)
    }
    private val ioScope = CoroutineScope(ioScopeJob + Dispatchers.IO + ioExceptionHandler)
    private val recordingMutex = Mutex()
    private val recordingIntents = RecordingIntentCoordinator()

    // Named for the channel layout on purpose. Recording and playback used to be stereo; a
    // leftover stereo capture opened with the mono configuration below would play at the wrong
    // frame width. New names keep the old files from being picked up at all.
    private val pcmFile by lazy { this.createFile("audio_mono.pcm") }
    private val aacFile by lazy { this.createFile("audio_mono.aac") }
    private val opusFile by lazy { this.createFile("audio_mono.opus") }
    private var micRecorder: MicRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var aacFilePlayer: AacFilePlayer? = null
    private var opusFilePlayer: OpusFilePlayer? = null
    private var playPcmThread: Thread? = null
    private val pcmInputStream = AtomicReference<BufferedInputStream?>(null)

    private var audioReceiver: AudioReceiver? = null
    private var audioSender: AudioSender? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d(TAG) { "pcmFile=$pcmFile" }
        d(TAG) { "aacFile=$aacFile" }
        d(TAG) { "opusFile=$opusFile" }

        XXPermissions.with(this)
            .permission(PermissionLists.getRecordAudioPermission())
            .request { _, deniedList ->
                val allGranted = deniedList.isEmpty()
                if (allGranted) {
                    toast("Grand recording permission")
                } else {
                    //  val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(
                    //      this@AudioActivity,
                    //      deniedList
                    //  )
                    toast("Deny record permission")
                    finish()
                }
            }

        binding.tvIp.text = NetworkUtil.getIp()[0]

        binding.btnRecordPcm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) record(AudioType.PCM) else stopRecording(AudioType.PCM)
        }

        binding.btnRecordAac.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) record(AudioType.AAC) else stopRecording(AudioType.AAC)
        }

        binding.btnRecordOpus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) record(AudioType.OPUS) else stopRecording(AudioType.OPUS)
        }

        binding.btnPlayPCM.setOnCheckedChangeListener { btn, isChecked ->
            if (isChecked) {
                audioPlayer = AudioPlayer(
                    this@AudioActivity,
                    audioDecoderInfo,
                    onFailure = { error -> LogContext.log.e(TAG, "PCM playback failed", error) },
                    type = AudioType.PCM,
                    usage = AUDIO_ATTR_USAGE,
                    contentType = AUDIO_ATTR_CONTENT_TYPE
                )
                val playbackThread = Thread {
                    val pcmIs = BufferedInputStream(FileInputStream(pcmFile))
                    pcmInputStream.set(pcmIs)
                    try {
                        pcmIs.use { input ->
                            val bufferSize = 8 shl 10
                            val readBuffer = ByteArray(bufferSize)
                            while (!Thread.currentThread().isInterrupted) {
                                val readSize = input.read(readBuffer)
                                if (readSize == -1) break
                                LogContext.log.i(TAG, "PcmPlayer read size[$readSize]")
                                val pcmData =
                                    if (readSize == readBuffer.size) {
                                        readBuffer
                                    } else {
                                        readBuffer.copyOf(readSize)
                                    }
                                audioPlayer?.play(pcmData)
                            }
                        }
                    } catch (e: Exception) {
                        if (!Thread.currentThread().isInterrupted) {
                            LogContext.log.e(TAG, "PCM playback failed", e)
                        }
                    } finally {
                        pcmInputStream.compareAndSet(pcmIs, null)
                        runOnUiThread { btn.isChecked = false }
                    }
                }
                playPcmThread = playbackThread
                playbackThread.start()
            } else {
                stopPcmPlayback()
            }
        }

        binding.btnPlayAac.setOnCheckedChangeListener { btn, isChecked ->
            if (isChecked) {
                val player = AacFilePlayer(
                    this@AudioActivity,
                    audioDecoderInfo,
                    AUDIO_ATTR_USAGE,
                    AUDIO_ATTR_CONTENT_TYPE
                )
                aacFilePlayer = player
                ioScope.launch {
                    try {
                        player.playAac(
                            aacFile = aacFile,
                            endCallback = { runOnUiThread { btn.isChecked = false } },
                            errorCallback = { error ->
                                LogContext.log.e(TAG, "AAC playback failed", error)
                                runOnUiThread {
                                    if (aacFilePlayer === player) aacFilePlayer = null
                                    btn.isChecked = false
                                    toast("Unable to play AAC file")
                                }
                            }
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LogContext.log.e(TAG, "Play AAC file failed", e)
                        runOnUiThread {
                            if (aacFilePlayer === player) aacFilePlayer = null
                            btn.isChecked = false
                            toast("Unable to play AAC file")
                        }
                    }
                }
            } else {
                stopAacPlayback()
            }
        }

        binding.btnPlayOpus.setOnCheckedChangeListener { btn, isChecked ->
            if (isChecked) {
                val player = OpusFilePlayer(
                    this@AudioActivity,
                    audioDecoderInfo,
                    AUDIO_ATTR_USAGE,
                    AUDIO_ATTR_CONTENT_TYPE
                )
                opusFilePlayer = player
                ioScope.launch {
                    try {
                        player.playOpus(
                            opusFile = opusFile,
                            endCallback = { runOnUiThread { btn.isChecked = false } },
                            errorCallback = { error ->
                                LogContext.log.e(TAG, "Play OPUS file failed", error)
                                runOnUiThread {
                                    if (opusFilePlayer === player) opusFilePlayer = null
                                    btn.isChecked = false
                                    toast("Unable to play OPUS file")
                                }
                            }
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LogContext.log.e(TAG, "Start OPUS playback failed", e)
                        runOnUiThread {
                            if (opusFilePlayer === player) opusFilePlayer = null
                            btn.isChecked = false
                            toast("Unable to play OPUS file")
                        }
                    }
                }
            } else {
                stopOpusPlayback()
            }
        }
    }

    /**
     * One callback per recording attempt, owning the stream that attempt opened.
     *
     * [MicRecorder.startRecord] rolls a start failure back asynchronously, so `onStop(false)`
     * from a failed attempt can arrive after [record] has opened the stream for a later one.
     * Shared `pcmOs` / `aacOs` / `opusOs` fields let that late callback close the stream the
     * current recording is writing into, and `onRecording` swallows the resulting write error -
     * so the recording would produce an empty file with nothing to show the user why.
     */
    private fun recordCallbackFor(
        token: Long,
        type: AudioType,
        out: BufferedOutputStream?,
        recorderRef: AtomicReference<MicRecorder?>
    ) = object : MicRecorder.RecordCallback {
        override fun onRecording(data: ByteArray, isConfig: Boolean, isKeyFrame: Boolean) {
            when (type) {
                AudioType.PCM -> {
                    LogContext.log.d(TAG, "PCM data[${data.size}]")
                    out?.write(data)
                }

                AudioType.AAC -> {
                    LogContext.log.i(
                        TAG,
                        "Get encoded AAC Data[${data.size}] isConfig=$isConfig " +
                            "isKeyFrame=$isKeyFrame"
                    )
                    out?.write(data)
                }

                AudioType.OPUS -> {
                    LogContext.log.i(
                        TAG,
                        "Get encoded OPUS Data[${data.size}] isConfig=$isConfig " +
                            "isKeyFrame=$isKeyFrame"
                    )
                    out?.write(OpusFilePlayer.START_CODE.encodeToByteArray())
                    out?.write(data)
                }

                else -> Unit
            }
        }

        override fun onStop(stopResult: Boolean) {
            runCatching {
                out?.flush()
                out?.close()
            }.onFailure { LogContext.log.e(TAG, "Close recording output failed", it) }
            if (!stopResult && recordingIntents.failIfCurrent(token, type)) {
                val failedRecorder = recorderRef.get()
                launchCleanup("Finalize failed recording") {
                    recordingMutex.withLock {
                        if (micRecorder === failedRecorder) micRecorder = null
                    }
                    failedRecorder?.stopRecordAndJoin()
                }
                runOnUiThread {
                    when (type) {
                        AudioType.PCM -> binding.btnRecordPcm.isChecked = false
                        AudioType.AAC -> binding.btnRecordAac.isChecked = false
                        AudioType.OPUS -> binding.btnRecordOpus.isChecked = false
                        else -> Unit
                    }
                    toast("Recording stopped after a start or capture failure")
                }
            }
        }
    }

    private fun record(type: AudioType) {
        val token = recordingIntents.requestStart(type)
        when (type) {
            AudioType.PCM -> {
                binding.btnRecordAac.isChecked = false
                binding.btnRecordOpus.isChecked = false
            }
            AudioType.AAC -> {
                binding.btnRecordPcm.isChecked = false
                binding.btnRecordOpus.isChecked = false
            }
            AudioType.OPUS -> {
                binding.btnRecordPcm.isChecked = false
                binding.btnRecordAac.isChecked = false
            }
            else -> Unit
        }
        XXPermissions.with(this)
            .permission(PermissionLists.getRecordAudioPermission())
            .request { _, deniedList ->
                val allGranted = deniedList.isEmpty()
                if (allGranted) {
                    ioScope.launch {
                        recordingMutex.withLock {
                            if (!recordingIntents.isCurrent(token, type)) return@withLock
                            val previous = micRecorder
                            micRecorder = null
                            previous?.stopRecordAndJoin()
                            if (!recordingIntents.isCurrent(token, type)) return@withLock

                            LogContext.log.i(ITAG, "Record type: $type")
                            var out: BufferedOutputStream? = null
                            var recorder: MicRecorder? = null
                            val recorderRef = AtomicReference<MicRecorder?>(null)
                            try {
                                out = when (type) {
                                    AudioType.PCM ->
                                        BufferedOutputStream(FileOutputStream(pcmFile))
                                    AudioType.AAC ->
                                        BufferedOutputStream(FileOutputStream(aacFile))
                                    AudioType.OPUS ->
                                        BufferedOutputStream(FileOutputStream(opusFile))
                                    else -> null
                                }
                                recorder =
                                    MicRecorder(
                                        audioEncoderInfo,
                                        recordCallbackFor(token, type, out, recorderRef),
                                        type
                                    )
                                recorderRef.set(recorder)
                                if (!recordingIntents.isCurrent(token, type)) {
                                    recorder.stopRecordAndJoin()
                                    return@withLock
                                }
                                micRecorder = recorder
                                recorder.startRecord()
                            } catch (error: Exception) {
                                val failedCurrentIntent =
                                    recordingIntents.failIfCurrent(token, type)
                                if (micRecorder === recorder) micRecorder = null
                                try {
                                    recorder?.stopRecordAndJoin()
                                } catch (cleanupFailure: Exception) {
                                    error.addSuppressed(cleanupFailure)
                                }
                                runCatching { out?.close() }
                                    .onFailure(error::addSuppressed)
                                LogContext.log.e(TAG, "Start recording failed", error)
                                if (failedCurrentIntent) {
                                    runOnUiThread {
                                        toast("Unable to start recording")
                                        when (type) {
                                            AudioType.PCM -> binding.btnRecordPcm.isChecked = false
                                            AudioType.AAC -> binding.btnRecordAac.isChecked = false
                                            AudioType.OPUS ->
                                                binding.btnRecordOpus.isChecked = false
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    //  val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(
                    //      this@AudioActivity,
                    //      deniedList
                    //  )
                    if (recordingIntents.failIfCurrent(token, type)) {
                        toast("Deny record permission")
                        finish()
                    }
                }
            }
    }

    private fun stopRecording(type: AudioType) {
        val token = recordingIntents.requestStopToken(type) ?: return
        stopRecording(token)
    }

    private fun stopAllRecording() {
        stopRecording(recordingIntents.stopAll())
    }

    private fun stopRecording(token: Long) {
        launchCleanup("Stop recording") {
            recordingMutex.withLock {
                if (!recordingIntents.isStopped(token)) return@withLock
                val recorder = micRecorder
                micRecorder = null
                recorder?.stopRecordAndJoin()
            }
        }
    }

    private fun stopPcmPlayback() {
        val playbackThread = playPcmThread
        val player = audioPlayer
        playPcmThread = null
        audioPlayer = null
        if (playbackThread == null && player == null) return
        runCatching { pcmInputStream.getAndSet(null)?.close() }
            .onFailure { LogContext.log.e(TAG, "Close PCM input failed", it) }
        playbackThread?.interrupt()
        launchCleanup("Stop PCM playback") {
            // Stop before joining: interrupt() cannot wake a blocking native AudioTrack.write(),
            // and the AudioTrack.stop() that can is inside releaseAndJoin(), after this join.
            // Same ordering as StreamPlayerStopper.prepareStop() and AacFilePlayer.
            player?.stop()
            playbackThread?.join()
            player?.releaseAndJoin()
        }
    }

    private fun stopAacPlayback() {
        val player = aacFilePlayer ?: return
        aacFilePlayer = null
        launchCleanup("Stop AAC playback") { player.stop() }
    }

    private fun stopOpusPlayback() {
        val player = opusFilePlayer ?: return
        opusFilePlayer = null
        launchCleanup("Stop OPUS playback") { player.stop() }
    }

    override fun onStop() {
        // Detach first, like the four cleanups below: the gates are one-shot, so a second
        // onStop() would only re-report the stored result.
        val receiver = audioReceiver
        val sender = audioSender
        audioReceiver = null
        audioSender = null
        launchCleanup("Stop audio receiver") { receiver?.stopServer() }
        launchCleanup("Stop audio sender") { sender?.stop() }
        stopAllRecording()
        stopPcmPlayback()
        stopAacPlayback()
        stopOpusPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        // onStop() has already scheduled deterministic teardown in this scope. Reject new work
        // while allowing those cleanup children to finish and release their Activity references.
        ioScopeJob.complete()
        super.onDestroy()
    }

    private fun launchCleanup(name: String, block: suspend () -> Unit) {
        ioScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogContext.log.e(TAG, "$name failed", e)
            }
        }
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
