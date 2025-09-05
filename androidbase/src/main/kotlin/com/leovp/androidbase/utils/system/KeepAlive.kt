package com.leovp.androidbase.utils.system

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RawRes
import androidx.core.content.ContextCompat
import com.leovp.log.LogContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * You must assign `app` which means the application context first.
 * Generally, it will be assigned in you custom application or another singleton utility before using it.
 *
 * **DO NOT** forget to register your broadcast receiver in `AndroidManifest.xml` like this:
 * ```xml
 * <receiver android:name="com.leovp.androidbase.utils.system.KeepAlive$KeepAliveReceiver"
 *     android:enabled="true"
 *     android:exported="false" />
 * ```
 *
 * Need `<uses-permission android:name="android.permission.WAKE_LOCK" />` permission
 *
 * @param keepAliveTimeInMin Unit: minute. Default value 25 min
 * Author: Michael Leo
 * Date: 20-8-28 下午1:40
 */
class KeepAlive(
    private val app: Application,
    @param:RawRes private val undeadAudioResId: Int,
    private val keepAliveTimeInMin: Float = 25f,
    val callback: () -> Unit,
) {
    companion object {
        private const val TAG = "KA"
//        private const val SAMPLE_RATE = 44100
//        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
//        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var mediaPlayer: MediaPlayer? = null
    private var job: Job? = null

    fun start() {
        release()
        LogContext.log.w(TAG, "Start keepAlive() for $keepAliveTimeInMin min(${keepAliveTimeInMin * 60}s)")
        runCatching {
            mediaPlayer = MediaPlayer.create(app, undeadAudioResId).apply {
                setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
//        setVolume(0.01F, 0.01F)
                isLooping = true
                start()
            }
        }.onFailure { it.printStackTrace() }

        val aliveTimeInMs: Long = (keepAliveTimeInMin * 60 * 1000).toLong()
        val alarmManager: AlarmManager = ContextCompat.getSystemService(app, AlarmManager::class.java)!!
        val triggerAtTime = SystemClock.elapsedRealtime() + aliveTimeInMs
        val intent = Intent(app, KeepAliveReceiver::class.java)
//            intent.putExtra("package", app.id)
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            0,
            intent,
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            // } else {
            //     PendingIntent.FLAG_UPDATE_CURRENT
            // }
        )
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, pendingIntent)

        job = CoroutineScope(Dispatchers.Main).launch {
            KeepAliveBus.events.collect {
                LogContext.log.d(TAG, "KeepAlive event received.")
                callback()
            }
        }
    }

    fun release() {
        LogContext.log.w(TAG, "Release keepAlive()")
        runCatching { mediaPlayer?.run { release() } }.onFailure { it.printStackTrace() }
        job?.cancel()
        job = null
    }

    internal class KeepAliveReceiver : BroadcastReceiver() {
        @OptIn(DelicateCoroutinesApi::class)
        override fun onReceive(context: Context, intent: Intent) {
            LogContext.log.d(TAG, "KeepAliveReceiver")
            GlobalScope.launch {
                KeepAliveBus.sendAliveEvent()
            }
        }
    }
}

object KeepAliveBus {
    private val _events = MutableSharedFlow<Unit>()
    val events = _events.asSharedFlow()

    suspend fun sendAliveEvent() {
        _events.emit(Unit)
    }
}
