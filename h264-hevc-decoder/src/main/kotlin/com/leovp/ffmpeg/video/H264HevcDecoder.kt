package com.leovp.ffmpeg.video

import androidx.annotation.Keep
import java.io.Closeable

/** H.264/HEVC software decoder backed by an instance-owned native context. */
@Keep
class H264HevcDecoder : Closeable {
    companion object {
        init {
            System.loadLibrary("h264-hevc-decoder")
            System.loadLibrary("avcodec")
            System.loadLibrary("avutil")
            System.loadLibrary("swscale")
        }
    }

    @Suppress("unused")
    private var nativeHandle: Long = 0L

    @Synchronized
    fun init(
        vpsBytes: ByteArray?,
        spsBytes: ByteArray,
        ppsBytes: ByteArray,
        prefixSei: ByteArray? = null,
        suffixSei: ByteArray? = null,
        rgbType: RgbType = RgbType.AV_PIX_FMT_NONE
    ): DecodeVideoInfo {
        check(nativeHandle == 0L) { "Decoder is already initialized." }
        require(spsBytes.isNotEmpty() && ppsBytes.isNotEmpty()) { "SPS and PPS must not be empty." }
        if (vpsBytes != null) require(vpsBytes.isNotEmpty()) { "VPS must not be empty." }
        return nativeInit(vpsBytes, spsBytes, ppsBytes, prefixSei, suffixSei, rgbType.type)
    }

    @Synchronized
    fun decode(encodedBytes: ByteArray): DecodedVideoFrame? {
        check(nativeHandle != 0L) { "Decoder is not initialized or is already closed." }
        require(encodedBytes.isNotEmpty()) { "Encoded frame must not be empty." }
        return nativeDecode(encodedBytes)
    }

    fun getVersion(): String = nativeGetVersion()

    @Synchronized
    fun release() {
        if (nativeHandle == 0L) return
        nativeRelease()
        check(nativeHandle == 0L) { "Native decoder release did not clear its handle." }
    }

    override fun close() = release()

    private external fun nativeInit(
        vpsBytes: ByteArray?,
        spsBytes: ByteArray,
        ppsBytes: ByteArray,
        prefixSei: ByteArray?,
        suffixSei: ByteArray?,
        rgbType: Int
    ): DecodeVideoInfo

    private external fun nativeRelease()
    private external fun nativeDecode(encodedBytes: ByteArray): DecodedVideoFrame?
    private external fun nativeGetVersion(): String

    @Keep
    class DecodedVideoFrame(
        val yuvOrRgbBytes: ByteArray,
        val format: Int,
        val width: Int,
        val height: Int
    )

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
        AV_PIX_FMT_BGRA(1),
        AV_PIX_FMT_RGBA(2),
        AV_PIX_FMT_ARGB(3),
        AV_PIX_FMT_ABGR(4),
        AV_PIX_FMT_BGR24(5),
        AV_PIX_FMT_RGB24(6),
    }
}
