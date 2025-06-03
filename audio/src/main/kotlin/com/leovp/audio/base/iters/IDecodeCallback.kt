package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 2023/4/26 14:18
 */
interface IDecodeCallback {
    fun onDecoded(pcmData: ByteArray)
}
