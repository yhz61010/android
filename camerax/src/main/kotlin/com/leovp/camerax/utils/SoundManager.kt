package com.leovp.camerax.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.leovp.android.exts.audioManager
import com.leovp.camerax.R
import com.leovp.kotlin.utils.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Author: Michael Leo
 * Date: 2022/4/25 10:03
 */
class SoundManager private constructor(context: Context) {
    // Hold the application context to avoid leaking a Fragment/Activity Context in a singleton.
    private val appCtx = context.applicationContext
    private var soundPool: SoundPool? = null
    private var soundIdCountdown1: Int = 0
    private var soundIdCountdown2: Int = 0
    private var soundIdCountdownFinal: Int = 0
    private var soundIdShutter: Int = 0
    private var soundIdCamStart: Int = 0
    private var soundIdCamStop: Int = 0

    private val audioManager by lazy { appCtx.audioManager }

    suspend fun loadSounds() = withContext(Dispatchers.IO) {
        // Release any previously created pool before rebuilding (supports reuse after release()).
        soundPool?.release()
        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build()
            )
            .build()
            .apply {
                soundIdCountdown1 = load(appCtx, R.raw.camera_timer, 1)
                soundIdCountdown2 = load(appCtx, R.raw.camera_timer, 1)
                soundIdCountdownFinal = load(appCtx, R.raw.camera_timer_2sec, 1)
                soundIdShutter = load(appCtx, R.raw.camera_shutter, 1)
                soundIdCamStart = load(appCtx, R.raw.cam_start, 1)
                soundIdCamStop = load(appCtx, R.raw.cam_stop, 1)
            }
    }

    fun playShutterSound() {
        playSound(soundIdShutter, getSoundVolume())
    }

    fun playTimerSound(leftTime: Int) {
        if (leftTime == 2) {
            playSound(soundIdCountdownFinal, getSoundVolume())
        } else if (leftTime > 2) {
            playSound(
                if (leftTime % 2 == 0) soundIdCountdown1 else soundIdCountdown2,
                getSoundVolume()
            )
        }
    }

    fun playCameraStartSound() = playSound(soundIdCamStart, getSoundVolume())
    fun playCameraStopSound() = playSound(soundIdCamStop, getSoundVolume())

    fun release() {
        runCatching {
            soundPool?.autoPause()
            soundPool?.release()
        }
        soundPool = null
    }

    // No-op until the pool is loaded, so play-before-load is safe (never crashes).
    private fun playSound(soundId: Int, volume: Float) {
        soundPool?.play(soundId, volume, volume, 1, 0, 1f)
    }

    private fun getSoundVolume(): Float =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    companion object : SingletonHolder<SoundManager, Context>(::SoundManager) {
        private const val MAX_STREAMS = 6
    }
}
