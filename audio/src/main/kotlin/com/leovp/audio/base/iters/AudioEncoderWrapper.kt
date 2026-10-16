package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:27
 */
interface AudioEncoderWrapper {
    /**
     * @param input The byte order of [input] is little endian.
     */
    fun encode(input: ByteArray)

    /**
     * Waits until any asynchronous encoder worker has stopped and its resources are released.
     *
     * Deliberately abstract: a default delegating to a non-suspend release would make this
     * method's own promise false for every implementation that did not override it.
     *
     * Replaces the removed non-suspend release(). Migration: implementations move the whole
     * release into this method and callers await it from a coroutine they own.
     */
    suspend fun releaseAndJoin()
}
