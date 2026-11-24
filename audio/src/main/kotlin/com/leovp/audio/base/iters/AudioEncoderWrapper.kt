package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:27
 */
interface AudioEncoderWrapper {
    /**
     * The selected terminal encoding/output failure, retained after release. This can become
     * visible before the asynchronous failure callback runs. After [releaseAndJoin] returns it
     * includes every failure selected before teardown, even if its notification was delayed.
     * Migration: custom wrappers must expose their stable failure snapshot, or null when healthy.
     */
    val failure: Throwable?

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
