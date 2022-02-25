package com.leovp.ffmpeg.video

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2021/6/11 09:57
 */
@Keep
class H264HevcDecoder {
    companion object {
        init {
            System.loadLibrary("h264-hevc-decoder")
            System.loadLibrary("avcodec")
            System.loadLibrary("avutil")
        }
    }

    external fun init(vpsBytes: ByteArray?, spsBytes: ByteArray, ppsBytes: ByteArray, prefixSei: ByteArray?, suffixSei: ByteArray?): DecodeVideoInfo
    external fun release()

    //    external fun decode(rawBytes: ByteArray): ByteArray
    external fun decode(rawBytes: ByteArray): DecodedVideoFrame?
    external fun getVersion(): String

    @Keep
    class DecodedVideoFrame(val yuvBytes: ByteArray, val format: Int, val width: Int, val height: Int)

    @Keep
    class DecodeVideoInfo(val codecId: Int, val codecName: String?,
        val pixelFormatId: Int, val pixelFormatName: String?,
        val width: Int, val height: Int)
}
