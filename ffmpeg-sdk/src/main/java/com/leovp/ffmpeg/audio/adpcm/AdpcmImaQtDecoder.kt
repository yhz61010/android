package com.leovp.ffmpeg.audio.adpcm

import com.leovp.ffmpeg.base.DecodedAudioResult

/**
 * Author: Michael Leo
 * Date: 2021/6/11 09:57
 */
class AdpcmImaQtDecoder private constructor() {
    companion object {
        init {
            System.loadLibrary("adpcm-ima-qt")
            System.loadLibrary("avcodec")
            System.loadLibrary("avutil")
        }
    }

    constructor(sampleRate: Int, channels: Int) : this() {
        init(sampleRate, channels)
    }

    private external fun init(sampleRate: Int, channels: Int): Int
    external fun release()
    external fun decode(adpcmBytes: ByteArray): DecodedAudioResult
    val version: String
        external get

    /**
     * In QuickTime, IMA is encoded by chunks of 34 bytes (=64 samples).
     * Channel data is interleaved per-chunk.
     *
     * The return result is 34 bytes * channels
     */
    external fun chunkSize(): Int
}