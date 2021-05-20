package com.leovp.androidbase.utils.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.media.MediaPlayer
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RawRes
import androidx.core.content.ContextCompat
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.utils.log.LogContext


/**
 * You must assign `app` which means the application context first.
 * Generally, it will be assigned in you custom application or another singleton utility before using it.
 *
 * **DO NOT** forget to register your broadcast receiver in `AndroidManifest.xml`.
 *
 * Need `<uses-permission android:name="android.permission.WAKE_LOCK" />` permission
 *
 * @param keepAliveTime Unit: minute. Default value 25 min
 * Author: Michael Leo
 * Date: 20-8-28 下午1:40
 */
class KeepAlive(
    @RawRes private val undeadAudioResId: Int,
    private val keepAliveTimeInMin: Float = 25f,
    private val callback: BroadcastReceiver? = null
) {
    companion object {
        private const val TAG = "KA"
//        private const val SAMPLE_RATE = 44100
//        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
//        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var mediaPlayer: MediaPlayer? = null

    fun keepAlive() {
        if (LogContext.enableLog) LogContext.log.w(TAG, "Start keepAlive() for $keepAliveTimeInMin min(${keepAliveTimeInMin * 60}s)")
        runCatching {
            mediaPlayer = MediaPlayer.create(app, undeadAudioResId).apply {
                setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
//        setVolume(0.01F, 0.01F)
                isLooping = true
                start()
            }
        }.onFailure { it.printStackTrace() }

        if (callback != null) {
            val aliveTimeInMs: Long = (keepAliveTimeInMin * 60 * 1000).toLong()
            val alarmManager: AlarmManager = ContextCompat.getSystemService(app, AlarmManager::class.java)!!
            val triggerAtTime = SystemClock.elapsedRealtime() + aliveTimeInMs
            val intent = Intent(app, callback::class.java)
//            intent.putExtra("package", app.id)
            val pendingIntent = PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, pendingIntent)
        }
    }

    fun release() {
        LogContext.log.w(TAG, "Release keepAlive()")
        mediaPlayer?.release()
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
//        LogContext.log.w(TAG, "Start keepAlive()")
//        ioScope.launch {
//            runCatching {
//                inputStream = context.resources.openRawResource(R.raw.magic)
//                musicBytes = inputStream.readBytes()
//                init()
//                while (true) {
//                    ensureActive()
//                    if (LogContext.log.DEBUG_MODE) {
//                        LogContext.log.d(TAG, "Keep alive play()")
//                    }
//                    play()
//                }
//            }.onFailure { it.printStackTrace() }
//        }
//    }
//
//    fun release() {
//        LogContext.log.w(TAG, "Release keepAlive()")
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