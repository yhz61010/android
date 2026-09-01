package com.leovp.ffmpeg.video

import androidx.annotation.Keep
import java.io.Closeable
import java.util.ArrayDeque

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
    private val pendingFrames = ArrayDeque<DecodedVideoFrame>()
    private var endOfInput = false
    private var drainCompleted = false

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
        return nativeInit(vpsBytes, spsBytes, ppsBytes, prefixSei, suffixSei, rgbType.type).also {
            pendingFrames.clear()
            endOfInput = false
            drainCompleted = false
        }
    }

    /**
     * Decodes one packet and returns the oldest available frame.
     *
     * Codecs may produce zero or multiple frames for one packet. Additional frames are retained
     * and returned by subsequent [decode] calls or [drain]. Call [drain] after the last packet
     * to retrieve delayed frames.
     */
    @Synchronized
    fun decode(encodedBytes: ByteArray): DecodedVideoFrame? {
        checkCanDecode(encodedBytes)
        pendingFrames.addAll(nativeDecodeFrames(encodedBytes))
        return pendingFrames.pollFirst()
    }

    /**
     * Returns retained compatibility output followed by every frame made available by this packet.
     */
    @Synchronized
    fun decodeFrames(encodedBytes: ByteArray): List<DecodedVideoFrame> {
        checkCanDecode(encodedBytes)
        val decodedFrames = nativeDecodeFrames(encodedBytes)
        if (pendingFrames.isEmpty()) return decodedFrames

        val result = ArrayList<DecodedVideoFrame>(pendingFrames.size + decodedFrames.size)
        while (pendingFrames.isNotEmpty()) result.add(pendingFrames.removeFirst())
        result.addAll(decodedFrames)
        return result
    }

    /**
     * Signals end of input and returns queued and codec-delayed frames, including output delayed by
     * B-frame reordering.
     */
    @Synchronized
    fun drain(): List<DecodedVideoFrame> {
        check(nativeHandle != 0L) { "Decoder is not initialized or is already closed." }
        endOfInput = true
        val drainedFrames = if (drainCompleted) emptyList() else nativeDrain()
        val result = ArrayList<DecodedVideoFrame>(pendingFrames.size + drainedFrames.size)
        result.addAll(pendingFrames)
        result.addAll(drainedFrames)
        pendingFrames.clear()
        drainCompleted = true
        return result
    }

    fun getVersion(): String = nativeGetVersion()

    @Synchronized
    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease()
            check(nativeHandle == 0L) { "Native decoder release did not clear its handle." }
        }
        pendingFrames.clear()
        endOfInput = true
        drainCompleted = true
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
    private external fun nativeDecodeFrames(encodedBytes: ByteArray): List<DecodedVideoFrame>
    private external fun nativeDrain(): List<DecodedVideoFrame>
    private external fun nativeGetVersion(): String

    private fun checkCanDecode(encodedBytes: ByteArray) {
        check(nativeHandle != 0L) { "Decoder is not initialized or is already closed." }
        check(!endOfInput) { "Decoder has reached end of input." }
        require(encodedBytes.isNotEmpty()) { "Encoded frame must not be empty." }
    }

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
