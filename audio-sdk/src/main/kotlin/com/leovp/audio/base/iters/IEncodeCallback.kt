package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 2023/4/26 08:46
 */
interface IEncodeCallback {
    fun onEncoded(encodedBytes: ByteArray, isConfig: Boolean, isKeyFrame: Boolean)
}
