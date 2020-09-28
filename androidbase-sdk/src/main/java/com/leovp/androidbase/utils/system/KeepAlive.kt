package com.leovp.androidbase.utils.system

import android.content.Context
import android.media.MediaPlayer
import android.os.PowerManager
import com.leovp.androidbase.R
import com.leovp.androidbase.utils.LLog


/**
 * Author: Michael Leo
 * Date: 20-8-28 下午1:40
 */
class KeepAlive(private val context: Context) {
    companion object {
        private const val TAG = "KA"
//        private const val SAMPLE_RATE = 44100
//        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
//        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val mediaPlayer by lazy { MediaPlayer.create(context, R.raw.single_note30) }

    fun keepAlive() {
        LLog.i(TAG, "Start keepAlive()")
        mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
//        mediaPlayer.setVolume(0.01F, 0.01F)
        mediaPlayer.isLooping = true
        mediaPlayer.start()
    }

    fun release() {
        LLog.i(TAG, "Release keepAlive()")
        mediaPlayer.release()
    }

//    private val ioScope = CoroutineScope(Dispatchers.IO)
//    private lateinit var inputStream: InputStream
//    private lateinit var musicBytes: ByteArray
//
//    private lateinit var audioAttributes: AudioAttributes
//    private var audioTrack: AudioTrack? = null
//    private lateinit var audioFormat: AudioFormat
//    private var bufferSize = 0
//    private var sessionId = 0
//
//    fun keepAlive() {
//        CLog.w(TAG, "Start keepAlive()")
//        ioScope.launch {
//            runCatching {
//                inputStream = context.resources.openRawResource(R.raw.magic)
//                musicBytes = inputStream.readBytes()
//                init()
//                while (true) {
//                    ensureActive()
//                    if (CLog.DEBUG_MODE) {
//                        CLog.d(TAG, "Keep alive play()")
//                    }
//                    play()
//                }
//            }.onFailure { it.printStackTrace() }
//        }
//    }
//
//    fun release() {
//        CLog.w(TAG, "Release keepAlive()")
//        ioScope.launch { stop() }
//        ioScope.cancel()
//    }
//
//    private fun init() {
//        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//        bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
//        sessionId = audioManager.generateAudioSessionId()
//        audioAttributes = AudioAttributes.Builder().apply {
//            setUsage(AudioAttributes.USAGE_MEDIA)              // AudioAttributes.USAGE_MEDIA         AudioAttributes.USAGE_VOICE_COMMUNICATION
//            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // AudioAttributes.CONTENT_TYPE_MUSIC   AudioAttributes.CONTENT_TYPE_SPEECH
//            setLegacyStreamType(AudioManager.STREAM_MUSIC)
//        }.build()
//        audioFormat = AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
//            .setEncoding(AUDIO_FORMAT)
//            .setChannelMask(CHANNEL_CONFIG)
//            .build()
//        audioTrack = AudioTrack(audioAttributes, audioFormat, bufferSize, AudioTrack.MODE_STREAM, sessionId)
//        audioTrack?.setVolume(0.001F)
//        audioTrack?.play()
//    }
//
//    private fun play() {
//        runCatching { audioTrack?.write(musicBytes, 0, musicBytes.size) }.onFailure { it.printStackTrace() }
//    }
//
//    private fun pause() {
//        runCatching {
//            audioTrack?.pause()
//            audioTrack?.flush()
//        }.onFailure { it.printStackTrace() }
//    }
//
//    private fun stop() {
//        pause()
//        runCatching {
//            audioTrack?.stop()
//        }.onFailure { it.printStackTrace() }
//        runCatching {
//            audioTrack?.release()
//        }.onFailure { it.printStackTrace() }
//        audioTrack = null
//    }
}