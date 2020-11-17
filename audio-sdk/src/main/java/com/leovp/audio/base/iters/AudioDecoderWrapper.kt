package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:27
 */
interface AudioDecoderWrapper {
    fun decode(input: ByteArray)
    fun release()
}