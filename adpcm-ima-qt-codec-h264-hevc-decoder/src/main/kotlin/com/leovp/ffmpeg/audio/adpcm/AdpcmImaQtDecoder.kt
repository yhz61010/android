package com.leovp.ffmpeg.audio.adpcm

import androidx.annotation.Keep
import java.io.Closeable

@Keep
class AdpcmImaQtDecoder private constructor() : Closeable {
    companion object {
        init {
            System.loadLibrary("adpcm-ima-qt-decoder")
            System.loadLibrary("avcodec")
            System.loadLibrary("avutil")
        }
    }

    @Suppress("unused")
    private var nativeHandle: Long = 0L

    constructor(sampleRate: Int, channels: Int) : this() {
        require(sampleRate > 0) { "Sample rate must be positive." }
        require(channels == 1 || channels == 2) { "Channel count must be 1 or 2." }
        check(nativeInit(sampleRate, channels) == 0 && nativeHandle != 0L) {
            "Unable to initialize the ADPCM decoder."
        }
    }

    @Synchronized
    fun decode(adpcmBytes: ByteArray): ByteArray {
        checkOpen()
        require(adpcmBytes.size == nativeChunkSize()) {
            "ADPCM input must contain exactly one 34-byte chunk per channel."
        }
        return checkNotNull(nativeDecode(adpcmBytes)) { "ADPCM decoder rejected the input." }
    }

    @Synchronized
    fun chunkSize(): Int {
        checkOpen()
        return nativeChunkSize()
    }

    fun getVersion(): String = nativeGetVersion()

    @Synchronized
    fun release() {
        if (nativeHandle == 0L) return
        nativeRelease()
        check(nativeHandle == 0L) { "Native decoder release did not clear its handle." }
    }

    override fun close() = release()

    private fun checkOpen() {
        check(nativeHandle != 0L) { "ADPCM decoder is closed." }
    }

    private external fun nativeInit(sampleRate: Int, channels: Int): Int
    private external fun nativeRelease()
    private external fun nativeDecode(adpcmBytes: ByteArray): ByteArray?
    private external fun nativeGetVersion(): String
    private external fun nativeChunkSize(): Int
}
