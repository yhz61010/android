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
            System.loadLibrary("swscale")
        }
    }

    fun init(
        vpsBytes: ByteArray?,
        spsBytes: ByteArray,
        ppsBytes: ByteArray,
        prefixSei: ByteArray? = null,
        suffixSei: ByteArray? = null,
        rgbType: RgbType = RgbType.AV_PIX_FMT_NONE
    ): DecodeVideoInfo = init(vpsBytes, spsBytes, ppsBytes, prefixSei, suffixSei, rgbType.type)

    private external fun init(
        vpsBytes: ByteArray?,
        spsBytes: ByteArray,
        ppsBytes: ByteArray,
        prefixSei: ByteArray? = null,
        suffixSei: ByteArray? = null,
        rgbType: Int = RgbType.AV_PIX_FMT_NONE.type
    ): DecodeVideoInfo

    external fun release()

    external fun decode(encodedBytes: ByteArray): DecodedVideoFrame?
    external fun getVersion(): String

    @Keep
    class DecodedVideoFrame(val yuvOrRgbBytes: ByteArray, val format: Int, val width: Int, val height: Int)

    @Keep
    class DecodeVideoInfo(
        val codecId: Int,
        val codecName: String?,
        val pixelFormatId: Int,
        val pixelFormatName: String?,
        val width: Int,
        val height: Int
    )

    @Keep
    enum class RgbType(val type: Int) {
        AV_PIX_FMT_NONE(-1),
        AV_PIX_FMT_BGRA(1), // For Mac x86_64
        AV_PIX_FMT_RGBA(2), // For Android
        AV_PIX_FMT_ARGB(3),
        AV_PIX_FMT_ABGR(4),
        AV_PIX_FMT_BGR24(5),
        AV_PIX_FMT_RGB24(6),
    }
}
