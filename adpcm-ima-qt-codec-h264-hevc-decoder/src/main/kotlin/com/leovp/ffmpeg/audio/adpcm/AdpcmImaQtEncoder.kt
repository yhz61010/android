package com.leovp.ffmpeg.audio.adpcm

import androidx.annotation.Keep
import com.leovp.ffmpeg.audio.base.EncodeAudioCallback
import java.io.Closeable

@Keep
class AdpcmImaQtEncoder private constructor() : Closeable {
    companion object {
        init {
            System.loadLibrary("adpcm-ima-qt-encoder")
            System.loadLibrary("avcodec")
            System.loadLibrary("avutil")
        }
    }

    @Suppress("unused")
    private var nativeHandle: Long = 0L
    private var encoding: Boolean = false
    private var releasePending: Boolean = false

    constructor(sampleRate: Int, channels: Int, bitRate: Int) : this() {
        require(sampleRate > 0) { "Sample rate must be positive." }
        require(channels == 1 || channels == 2) { "Channel count must be 1 or 2." }
        require(bitRate > 0) { "Bit rate must be positive." }
        check(nativeInit(sampleRate, channels, bitRate) == 0 && nativeHandle != 0L) {
            "Unable to initialize the ADPCM encoder."
        }
    }

    @Volatile
    var encodedCallback: EncodeAudioCallback? = null

    @Synchronized
    fun encode(pcmBytes: ByteArray) {
        check(nativeHandle != 0L && !releasePending) { "ADPCM encoder is closed." }
        check(!encoding) { "Reentrant ADPCM encoding is not supported." }
        encoding = true
        try {
            nativeEncode(pcmBytes)
        } finally {
            encoding = false
            if (releasePending) releaseNow()
        }
    }

    fun getVersion(): String = nativeGetVersion()

    @Synchronized
    fun release() {
        if (nativeHandle == 0L) return
        if (encoding) {
            releasePending = true
            return
        }
        releaseNow()
    }

    override fun close() = release()

    fun encodedAudioCallback(encodeAudio: ByteArray) {
        encodedCallback?.onEncodedUpdate(encodeAudio)
    }

    private fun releaseNow() {
        nativeRelease()
        releasePending = false
        check(nativeHandle == 0L) { "Native encoder release did not clear its handle." }
    }

    private external fun nativeInit(sampleRate: Int, channels: Int, bitRate: Int): Int
    private external fun nativeRelease()
    private external fun nativeEncode(pcmBytes: ByteArray)
    private external fun nativeGetVersion(): String
}
