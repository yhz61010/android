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
    fun release()
}
