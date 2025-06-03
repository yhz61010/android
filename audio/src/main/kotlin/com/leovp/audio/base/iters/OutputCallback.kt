package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 20-11-14 下午4:17
 */
interface OutputCallback {
    fun output(out: ByteArray, isConfig: Boolean, isKeyFrame: Boolean)
}
