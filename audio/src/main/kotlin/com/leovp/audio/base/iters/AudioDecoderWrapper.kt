package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:27
 */
interface AudioDecoderWrapper {
    fun decode(input: ByteArray)

    /**
     * Waits until any asynchronous decoder worker has stopped and its resources are released.
     *
     * Replaces the removed non-suspend release(). Migration: implementations move the whole
     * release into this method and callers await it from a coroutine they own.
     */
    suspend fun releaseAndJoin()
}
