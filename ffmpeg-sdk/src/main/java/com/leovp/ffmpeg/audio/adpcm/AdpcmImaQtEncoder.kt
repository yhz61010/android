package com.leovp.ffmpeg.audio.adpcm

import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.kotlin.toHexStringLE
import com.leovp.androidbase.utils.file.FileUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.ffmpeg.audio.base.EncodeAudioCallback
import java.io.BufferedOutputStream
import java.io.FileOutputStream

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

    var encodedCallback: EncodeAudioCallback? = null

    private external fun init(sampleRate: Int, channels: Int, bitRate: Int): Int
    external fun release()

    external fun encode(pcmBytes: ByteArray)
    external fun getVersion(): String

    fun encodedAudioCallback(encodeAudio: ByteArray) {
        encodedCallback?.onEncodedUpdate(encodeAudio)
    }
}