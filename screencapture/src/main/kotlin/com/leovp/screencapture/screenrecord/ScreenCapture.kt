package com.leovp.screencapture.screenrecord

import android.media.MediaCodecInfo
import android.media.projection.MediaProjection
import com.leovp.log.LogContext
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordMediaCodecStrategy
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordRawBmpStrategy
import com.leovp.screencapture.screenrecord.base.strategies.Screenshot2H26xStrategy
import java.io.File

/**
 * Author: Michael Leo
 * Date: 20-3-12 下午7:31
 */
object ScreenCapture {

    private const val TAG = "ScrCap"

    const val BY_IMAGE_2_H26X = 1
    const val BY_MEDIA_CODEC = 2
    const val BY_RAW_BMP = 3

    @Suppress("unused")
    const val SCREEN_CAPTURE_TYPE_X264 = 3

    /**
     * @param dpi Not used for [Screenshot2H26xStrategy] which type is [BY_IMAGE_2_H26X]
     */
    class Builder(
        private val width: Int,
        private val height: Int,
        private val dpi: Int,
        private val mediaProjection: MediaProjection?,
        private val captureType: Int,
        private val screenDataListener: ScreenDataListener
    ) {
        // Common setting
        private var fps = 20F

        // H26x setting
        private var encodeType: ScreenRecordMediaCodecStrategy.EncodeType =
            ScreenRecordMediaCodecStrategy.EncodeType.H264
        private var bitrate = width * height
        private var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        private var keyFrameRate = 20
        private var iFrameInterval = 1
        private var useGoogleEncoder = false

        // Screenshot setting
        private var sampleSize = 1
        private var quality = 100
        private var mp4OutputFile: File? = null

        // ==================================================
        // ===== Common For H26x
        // ==================================================
        fun setEncodeType(encodeType: ScreenRecordMediaCodecStrategy.EncodeType) =
            apply { this.encodeType = encodeType }

        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setKeyFrameRate(keyFrameRate: Int) = apply { this.keyFrameRate = keyFrameRate }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }

        // ==================================================
        // ===== Only For H26x
        // ==================================================
        fun setGoogleEncoder(useGoogleEncoder: Boolean) =
            apply { this.useGoogleEncoder = useGoogleEncoder }

        // ==================================================
        // ===== Only For Image
        // ==================================================
        /** Only used in [BY_IMAGE_2_H26X] mode */
        fun setSampleSize(sample: Int) = apply { this.sampleSize = sample }

        /** Only used in [BY_IMAGE_2_H26X] mode */
        fun setQuality(quality: Int) = apply { this.quality = quality }

        /**
         * Also write an MP4 to [file], in addition to the stream delivered to the
         * [ScreenDataListener]. Null, the default, writes no MP4.
         *
         * Only used in [BY_IMAGE_2_H26X] mode. Two things change when it is set:
         * the file is only playable once the recorder has been released, because that is when the
         * index is written; and on API 21-23 an H265 request is downgraded to H264, because
         * `MediaMuxer` cannot carry an HEVC track before API 24. Read the effective codec back
         * from [Screenshot2H26xStrategy.encodeType].
         */
        fun setMp4OutputFile(file: File?) = apply { this.mp4OutputFile = file }

        fun build(): ScreenProcessor {
            LogContext.log.i(
                TAG,
                "encodeType=$encodeType width=$width height=$height dpi=$dpi " +
                    "captureType=$captureType " +
                    "fps=$fps bitrate=$bitrate bitrateMode=$bitrateMode " +
                    "keyFrameRate=$keyFrameRate " +
                    "iFrameInterval=$iFrameInterval sampleSize=$sampleSize " +
                    "useGoogleEncoder=$useGoogleEncoder mp4OutputFile=${mp4OutputFile?.name}"
            )
            return when (captureType) {
                BY_IMAGE_2_H26X -> Screenshot2H26xStrategy.Builder(
                    width,
                    height,
                    dpi,
                    screenDataListener
                )
                    .setEncodeType(encodeType)
                    .setFps(fps)
                    .setBitrate(bitrate)
                    .setBitrateMode(bitrateMode)
                    .setKeyFrameRate(keyFrameRate)
                    .setIFrameInterval(iFrameInterval)
                    .setQuality(quality)
                    .setSampleSize(sampleSize)
                    .setMp4OutputFile(mp4OutputFile)
                    .build()
                BY_MEDIA_CODEC -> ScreenRecordMediaCodecStrategy.Builder(
                    width,
                    height,
                    dpi,
                    mediaProjection,
                    screenDataListener
                )
                    .setEncodeType(encodeType)
                    .setFps(fps)
                    .setBitrate(bitrate)
                    .setBitrateMode(bitrateMode)
                    .setKeyFrameRate(keyFrameRate)
                    .setIFrameInterval(iFrameInterval)
                    .setGoogleEncoder(useGoogleEncoder)
                    .build()
                BY_RAW_BMP -> ScreenRecordRawBmpStrategy.Builder(
                    width,
                    height,
                    dpi,
                    mediaProjection,
                    screenDataListener
                ).setFps(fps).build()
                else -> throw IllegalAccessException("Not support strategy.")
// ScreenRecordX264Strategy.Builder(width, height, dpi, mediaProjection, screenDataListener)
//                        .setFps(fps)
//                        .setBitrate(bitrate)
//                        .build()
            }
        }
    }
}
