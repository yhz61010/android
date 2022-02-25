package com.leovp.ffmpeg.audio.adpcm

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2021/6/11 09:57
 */
@Keep
class AdpcmImaQtDecoder private constructor() {
    companion object {
        init {
            System.loadLibrary("adpcm-ima-qt-decoder")
            System.loadLibrary("avcodec")
            System.loadLibrary("avutil")
        }
    }

    constructor(sampleRate: Int, channels: Int) : this() {
        init(sampleRate, channels)
    }

    private external fun init(sampleRate: Int, channels: Int): Int
    external fun release()

    /**
     * In QuickTime, IMA is encoded by chunks of 34 bytes (=64 samples) for each channel.
     * Channel data is interleaved per-chunk.
     *
     * That means for mono audio, it will decode 34 bytes each time,
     * for stereo audio, it will decode 68 bytes each time,
     *
     * @param adpcmBytes The length of this parameter is 34 bytes * channels
     * @return The returned pcm data is interleaved.
     * Like this:
     * ```
     * | L0        | L0         | R0        | R0         | L1        | L1         | L1        |R1          |
     * | --------- | ---------- | --------- | ---------- | --------- | ---------- | --------- | ---------- |
     * | Low 8bits | High 8bits | Low 8bits | High 8bits | Low 8bits | High 8bits | Low 8bits | High 8bits |
     * ```
     */
    external fun decode(adpcmBytes: ByteArray): ByteArray
    external fun getVersion(): String

    /**
     * @return The result is 34 bytes * channels
     */
    external fun chunkSize(): Int
}