package com.leovp.ffmpeg.audio.adpcm

import androidx.annotation.Keep
import com.leovp.ffmpeg.audio.base.EncodeAudioCallback

/**
 * Author: Michael Leo
 * Date: 2021/6/11 09:57
 */
@Keep
class AdpcmImaQtEncoder private constructor() {
    companion object {
        init {
            System.loadLibrary("adpcm-ima-qt-encoder")
            System.loadLibrary("avcodec")
            System.loadLibrary("avutil")
        }
    }

    constructor(sampleRate: Int, channels: Int, bitRate: Int) : this() {
        init(sampleRate, channels, bitRate)
    }

    var encodedCallback: EncodeAudioCallback? = null

    private external fun init(sampleRate: Int, channels: Int, bitRate: Int): Int
    external fun release()

    external fun encode(pcmBytes: ByteArray)
    external fun getVersion(): String

    fun encodedAudioCallback(encodeAudio: ByteArray) {
        encodedCallback?.onEncodedUpdate(encodeAudio)
    }
}