package com.leovp.screencapture.screenrecord

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.media.MediaCodecInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import com.leovp.log_sdk.LogContext
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordMediaCodecStrategy
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordRawBmpStrategy
import com.leovp.screencapture.screenrecord.base.strategies.Screenshot2H26xStrategy

/**
 * Author: Michael Leo
 * Date: 20-3-12 下午7:31
 */
object ScreenCapture {

    private const val TAG = "ScrCap"

    const val BY_IMAGE_2_H26x = 1
    const val BY_MEDIA_CODEC = 2
    const val BY_RAW_BMP = 3

    @Suppress("unused")
    const val SCREEN_CAPTURE_TYPE_X264 = 3

    private const val REQUEST_CODE_SCREEN_CAPTURE = 0x123

    const val SCREEN_CAPTURE_RESULT_GRANT = 1
    const val SCREEN_CAPTURE_RESULT_DENY = 2

    @Suppress("WeakerAccess")
    const val SCREEN_CAPTURE_RESULT_IGNORE = 3

    interface ScreenCaptureListener {
        fun requestResult(result: Int, resultCode: Int, data: Intent?)
    }

    /**
     * @param dpi Not used for [Screenshot2H26xStrategy] which type is [BY_IMAGE_2_H26x]
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
        private var encodeType: ScreenRecordMediaCodecStrategy.EncodeType = ScreenRecordMediaCodecStrategy.EncodeType.H264
        private var bitrate = width * height
        private var bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        private var keyFrameRate = 20
        private var iFrameInterval = 1
        private var useGoogleEncoder = false

        // Screenshot setting
        private var sampleSize = 1
        private var quality = 100

        // ==================================================
        // ===== Common For H26x
        // ==================================================
        fun setEncodeType(encodeType: ScreenRecordMediaCodecStrategy.EncodeType) = apply { this.encodeType = encodeType }
        fun setFps(fps: Float) = apply { this.fps = fps }
        fun setBitrate(bitrate: Int) = apply { this.bitrate = bitrate }
        fun setBitrateMode(bitrateMode: Int) = apply { this.bitrateMode = bitrateMode }
        fun setKeyFrameRate(keyFrameRate: Int) = apply { this.keyFrameRate = keyFrameRate }
        fun setIFrameInterval(iFrameInterval: Int) = apply { this.iFrameInterval = iFrameInterval }

        // ==================================================
        // ===== Only For H26x
        // ==================================================
        fun setGoogleEncoder(useGoogleEncoder: Boolean) = apply { this.useGoogleEncoder = useGoogleEncoder }

        // ==================================================
        // ===== Only For Image
        // ==================================================
        /** Only used in [BY_IMAGE_2_H26x] mode */
        fun setSampleSize(sample: Int) = apply { this.sampleSize = sample }

        /** Only used in [BY_IMAGE_2_H26x] mode */
        fun setQuality(quality: Int) = apply { this.quality = quality }

        fun build(): ScreenProcessor {
            LogContext.log.i(
                TAG,
                "encodeType=$encodeType width=$width height=$height dpi=$dpi captureType=$captureType fps=$fps bitrate=$bitrate bitrateMode=$bitrateMode keyFrameRate=$keyFrameRate iFrameInterval=$iFrameInterval sampleSize=$sampleSize useGoogleEncoder=$useGoogleEncoder"
            )
            return when (captureType) {
                BY_IMAGE_2_H26x ->
                    Screenshot2H26xStrategy.Builder(width, height, dpi, screenDataListener)
                        .setEncodeType(encodeType)
                        .setFps(fps)
                        .setBitrate(bitrate)
                        .setBitrateMode(bitrateMode)
                        .setKeyFrameRate(keyFrameRate)
                        .setIFrameInterval(iFrameInterval)
                        .setQuality(quality)
                        .setSampleSize(sampleSize)
                        .build()
                BY_MEDIA_CODEC ->
                    ScreenRecordMediaCodecStrategy.Builder(width, height, dpi, mediaProjection, screenDataListener)
                        .setEncodeType(encodeType)
                        .setFps(fps)
                        .setBitrate(bitrate)
                        .setBitrateMode(bitrateMode)
                        .setKeyFrameRate(keyFrameRate)
                        .setIFrameInterval(iFrameInterval)
                        .setGoogleEncoder(useGoogleEncoder)
                        .build()
                BY_RAW_BMP ->
                    ScreenRecordRawBmpStrategy.Builder(width, height, dpi, mediaProjection, screenDataListener)
                        .setFps(fps)
                        .build()
                else -> throw IllegalAccessException("Not support strategy.")
//                    ScreenRecordX264Strategy.Builder(width, height, dpi, mediaProjection, screenDataListener)
//                        .setFps(fps)
//                        .setBitrate(bitrate)
//                        .build()
            }
        }
    }

    fun requestPermission(act: Activity) {
        val mediaProjectionManager = act.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        act.startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?, listener: ScreenCaptureListener) {
        if (requestCode != REQUEST_CODE_SCREEN_CAPTURE) {
            listener.requestResult(SCREEN_CAPTURE_RESULT_IGNORE, resultCode, data)
            return
        }
        if (resultCode != RESULT_OK) {
            listener.requestResult(SCREEN_CAPTURE_RESULT_DENY, resultCode, data)
            return
        }

        listener.requestResult(SCREEN_CAPTURE_RESULT_GRANT, resultCode, data)
    }
}