package com.leovp.demo.basiccomponents.examples.audio

import com.leovp.audio.base.AudioType

/** Orders asynchronous permission results and format toggles without owning Android resources. */
internal class RecordingIntentCoordinator {
    private var generation = 0L
    private var desiredType: AudioType? = null

    @Synchronized
    fun requestStart(type: AudioType): Long {
        generation++
        desiredType = type
        return generation
    }

    @Synchronized
    fun requestStop(type: AudioType): Boolean = requestStopToken(type) != null

    @Synchronized
    fun requestStopToken(type: AudioType): Long? {
        if (desiredType != type) return null
        generation++
        desiredType = null
        return generation
    }

    @Synchronized
    fun failIfCurrent(token: Long, type: AudioType): Boolean {
        if (generation != token || desiredType != type) return false
        generation++
        desiredType = null
        return true
    }

    @Synchronized
    fun stopAll(): Long {
        generation++
        desiredType = null
        return generation
    }

    @Synchronized
    fun isCurrent(token: Long, type: AudioType): Boolean =
        generation == token && desiredType == type

    @Synchronized
    fun isStopped(token: Long): Boolean = generation == token && desiredType == null
}
