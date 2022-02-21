package com.leovp.ffmpeg.video

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2021/6/11 09:57
 */
@Keep
class H264HevcDecoder(vpsBytes: ByteArray?, spsBytes: ByteArray, ppsBytes: ByteArray) {
    companion object {
        init {
            System.loadLibrary("h264-hevc-decoder")
            System.loadLibrary("avcodec")
            System.loadLibrary("avutil")
            System.loadLibrary("avformat")
        }
    }

    init {
        init(vpsBytes, spsBytes, ppsBytes)
    }

    private external fun init(vpsBytes: ByteArray?, spsBytes: ByteArray, ppsBytes: ByteArray): Int
    external fun release()

    //    external fun decode(rawBytes: ByteArray): ByteArray
    external fun decode(rawBytes: ByteArray): DecodedVideoFrame
    external fun getVersion(): String

    @Keep
    class DecodedVideoFrame(val yuvBytes: ByteArray, val format: Int, val width: Int, val height: Int)
}
