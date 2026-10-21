package com.leovp.screencapture.screenrecord.base.strategies

import android.media.MediaCodecInfo
import android.os.Build
import com.leovp.log.LogContext
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import java.io.File

/**
 * Configures and creates a [Screenshot2H26xStrategy].
 *
 * Replace `Screenshot2H26xStrategy.Builder(...)` with this top-level builder when upgrading.
 */
class Screenshot2H26xBuilder(
    val width: Int,
    val height: Int,
    val dpi: Int,
    val screenDataListener: ScreenDataListener,
) {
    var encodeType: ScreenRecordMediaCodecStrategy.EncodeType =
        ScreenRecordMediaCodecStrategy.EncodeType.H264
        private set

    /** Capture rate. Drives the capture loop's cadence, timestamps and `KEY_FRAME_RATE`. */
    var fps = 20F
        private set
    var quality = 100
        private set
    var sampleSize = 1
        private set
    var bitrate = width * height
        private set
    var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        private set

    /** Key-frame interval in seconds. Encoder frame rate is configured through [setFps]. */
    var iFrameInterval = 1
        private set

    /**
     * Where to write an MP4 alongside the stream handed to [ScreenDataListener], or null to write
     * no MP4. The listener's stream is never replaced or altered.
     */
    var mp4OutputFile: File? = null
        private set

    fun setEncodeType(encodeType: ScreenRecordMediaCodecStrategy.EncodeType) =
        apply { this.encodeType = encodeType }

    fun setFps(fps: Float) = apply { this.fps = fps }
    fun setQuality(quality: Int) = apply { this.quality = quality }
    fun setSampleSize(sample: Int) = apply { this.sampleSize = sample }
    fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
    fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
    fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }

    /** @see mp4OutputFile */
    fun setMp4OutputFile(file: File?) = apply { this.mp4OutputFile = file }

    fun build(): Screenshot2H26xStrategy {
        fallBackToAvcIfMuxerCannotCarryHevc()
        LogContext.log.w(
            TAG,
            "encodeType=$encodeType width=$width height=$height dpi=$dpi fps=$fps " +
                "sampleSize=$sampleSize mp4OutputFile=${mp4OutputFile?.name}"
        )
        return Screenshot2H26xStrategy(this)
    }

    /**
     * `MediaMuxer` gained HEVC track support in API 24. Only callers that requested MP4 fall back
     * to AVC; raw HEVC elementary streams remain available on API 21–23.
     */
    private fun fallBackToAvcIfMuxerCannotCarryHevc() {
        if (mp4OutputFile == null) return
        if (encodeType != ScreenRecordMediaCodecStrategy.EncodeType.H265) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return
        LogContext.log.w(
            TAG,
            "MediaMuxer cannot write an HEVC track before API 24 and this device is API " +
                "${Build.VERSION.SDK_INT}. Encoding H264 instead so the MP4 and raw stream " +
                "stay the same codec. Drop setMp4OutputFile() to keep H265 here."
        )
        encodeType = ScreenRecordMediaCodecStrategy.EncodeType.H264
    }

    private companion object {
        const val TAG = "ScrShotRec"
    }
}
