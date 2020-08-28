package com.ho1ho.androidbase.utils.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.ho1ho.androidbase.R
import kotlinx.coroutines.*

/**
 * Author: Michael Leo
 * Date: 20-8-28 下午1:40
 */
class KeepAlive(private val context: Context) {
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var audioTrack: AudioTrack

    fun keepAlive() {
        ioScope.launch {
            while (true) {
                ensureActive()
                KeepAlive(context).play()
            }
        }
    }

    fun release() {
        ioScope.cancel()
    }

    private fun play() {
        var readSize: Int
        val music = ByteArray(512)

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

        context.resources.openRawResource(R.raw.magic).use { ins ->
            runCatching { audioTrack.play() }.onFailure { it.printStackTrace() }
            while (ins.read(music).also { readSize = it } != -1) audioTrack.write(music, 0, readSize)
        }
        runCatching {
            audioTrack.pause()
            audioTrack.flush()
            audioTrack.stop()
            audioTrack.release()
        }.onFailure { it.printStackTrace() }
    }
}