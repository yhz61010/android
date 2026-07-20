@file:Suppress("unused")

package com.leovp.androidbase.utils.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.leovp.log.base.d
import com.leovp.log.base.e
import com.leovp.log.base.w
import java.util.concurrent.ConcurrentHashMap

/**
 * Author: Michael Leo
 * Date: 2025/12/17 10:55
 */
object SoundEffectPlayer {
    private const val TAG = "SndEffPlayer"
    private const val MAX_STREAMS = 3

    private var soundPool: SoundPool? = null

    // Mutated from both caller threads (play/preload) and the SoundPool load-complete
    // callback thread, so these must be concurrent collections.
    private val soundMap = ConcurrentHashMap<String, Int>()
    private val loadedSounds = ConcurrentHashMap.newKeySet<Int>()

    private fun ensureSoundPool() {
        if (soundPool == null) {
            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
                .apply {
                    setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0) {
                            loadedSounds.add(sampleId)
                            d(TAG) { "Sound loaded successfully: sampleId=$sampleId" }
                        } else {
                            e(TAG) { "Failed to load sound: sampleId=$sampleId, status=$status" }
                        }
                    }
                }
        }
    }

    fun preload(context: Context, vararg fileNames: String) {
        ensureSoundPool()
        fileNames.forEach { fileName ->
            if (!soundMap.containsKey(fileName)) {
                loadSound(context, fileName)
                d(TAG) { "Preloading sound: $fileName" }
            }
        }
    }

    fun play(context: Context, fileName: String) {
        try {
            ensureSoundPool()
            val soundId = getSoundId(context, fileName)
            if (soundId != null) {
                if (isSoundReady(soundId)) {
                    soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
                    d(TAG) { "Playing sound: $fileName" }
                } else {
                    w(TAG) { "Sound not ready yet: $fileName (id=$soundId)" }
                }
            } else {
                w(TAG) { "Sound not loaded: $fileName" }
            }
        } catch (e: Exception) {
            e(TAG, e) { "Error playing sound effect: $fileName" }
        }
    }

    private fun isSoundReady(soundId: Int): Boolean = loadedSounds.contains(soundId)

    private fun getSoundId(context: Context, fileName: String): Int? =
        soundMap[fileName] ?: loadSound(context, fileName)

    private fun loadSound(context: Context, fileName: String): Int? = try {
        val soundId = context.assets.openFd(fileName).use { afd -> soundPool?.load(afd, 1) }
        if (soundId != null && soundId > 0) {
            soundMap[fileName] = soundId
            d(TAG) { "Loading sound: $fileName with ID: $soundId" }
            soundId
        } else {
            e(TAG) { "Failed to load sound: $fileName" }
            null
        }
    } catch (e: Exception) {
        e(TAG, e) { "Error loading sound from assets: $fileName" }
        null
    }

    fun release() {
        runCatching { soundPool?.release() }
        soundPool = null
        soundMap.clear()
        loadedSounds.clear()
        d(TAG) { "SoundPool released" }
    }
}
