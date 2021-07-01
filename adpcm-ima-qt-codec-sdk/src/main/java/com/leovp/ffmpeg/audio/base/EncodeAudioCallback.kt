package com.leovp.ffmpeg.audio.base

/**
 * Author: Michael Leo
 * Date: 2021/6/28 16:21
 */
interface EncodeAudioCallback {
    fun onEncodedUpdate(encodedAudio: ByteArray)
}