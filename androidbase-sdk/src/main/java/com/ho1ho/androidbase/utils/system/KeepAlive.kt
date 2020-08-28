package com.ho1ho.androidbase.utils.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.ho1ho.androidbase.BuildConfig
import com.ho1ho.androidbase.R
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import kotlinx.coroutines.*
import java.io.InputStream

/**
 * Author: Michael Leo
 * Date: 20-8-28 下午1:40
 */
class KeepAlive(private val context: Context) {
    companion object {
        private const val TAG = "KA"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var audioTrack: AudioTrack
    private lateinit var inputStream: InputStream
    private lateinit var musicBytes: ByteArray

    fun keepAlive() {
        LLog.w(ITAG, "Start keepAlive()")
        ioScope.launch {
            runCatching {
                inputStream = context.resources.openRawResource(R.raw.magic)
                musicBytes = inputStream.readBytes()
                init()
                while (true) {
                    ensureActive()
                    if (BuildConfig.DEBUG) {
                        LLog.d(TAG, "Keep alive play()")
                    }
                    play()
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    fun release() {
        LLog.w(ITAG, "Release keepAlive()")
        ioScope.cancel()
        stop()
    }

    private fun init() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
        val sessionId = audioManager.generateAudioSessionId()
        val audioAttributesBuilder = AudioAttributes.Builder().apply {
            setUsage(AudioAttributes.USAGE_MEDIA)              // AudioAttributes.USAGE_MEDIA         AudioAttributes.USAGE_VOICE_COMMUNICATION
            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // AudioAttributes.CONTENT_TYPE_MUSIC   AudioAttributes.CONTENT_TYPE_SPEECH
            setLegacyStreamType(AudioManager.STREAM_MUSIC)
        }
        val audioFormat = AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
            .setEncoding(AUDIO_FORMAT)
            .setChannelMask(CHANNEL_CONFIG)
            .build()
        audioTrack = AudioTrack(audioAttributesBuilder.build(), audioFormat, bufferSize * 2, AudioTrack.MODE_STREAM, sessionId)
        runCatching { audioTrack.play() }.onFailure { it.printStackTrace() }
    }

    private fun play() {
        runCatching { audioTrack.write(musicBytes, 0, musicBytes.size) }.onFailure { it.printStackTrace() }
    }

    private fun stop() {
        runCatching {
            audioTrack.pause()
            audioTrack.flush()
        }.onFailure { it.printStackTrace() }
        runCatching {
            audioTrack.stop()
        }.onFailure { it.printStackTrace() }
        runCatching {
            audioTrack.release()
        }.onFailure { it.printStackTrace() }
    }
}