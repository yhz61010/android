package com.leovp.ffmpeg.audio.adpcm

/**
 * Author: Michael Leo
 * Date: 2021/6/11 09:57
 */
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

    private external fun init(sampleRate: Int, channels: Int, bitRate: Int): Int
    external fun release()

    external fun encode(pcmBytes: ByteArray): ByteArray
    external fun getVersion(): String
}