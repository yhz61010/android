@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.androidbase.utils.media

import android.graphics.ImageFormat
import android.media.MediaCodec
import android.os.Bundle
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2021/5/13 5:25 PM
 */
object VideoUtil {
    fun setBitrateDynamically(mediaCodec: MediaCodec, bitrate: Int) {
        kotlin.runCatching {
            val param = Bundle()
            param.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate)
            mediaCodec.setParameters(param)
        }.onFailure { if (LogContext.enableLog) LogContext.log.e("setBitrateDynamically error", it) }
    }

    fun sendIdrFrameByManual(mediaCodec: MediaCodec) {
        LogContext.log.w("sendIdrFrameByManual()") //        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // 23
        val param = Bundle()
        param.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        mediaCodec.setParameters(param) //        }
    }

    @Suppress("unused")
    fun getImageFormatName(imageFormat: Int) = when (imageFormat) {
        ImageFormat.JPEG -> "JPEG"
        ImageFormat.YUV_420_888 -> "YUV_420_888"
        ImageFormat.YUV_422_888 -> "YUV_422_888"
        ImageFormat.YUV_444_888 -> "YUV_444_888"
        ImageFormat.NV16 -> "NV16"
        ImageFormat.NV21 -> "NV21"
        ImageFormat.HEIC -> "HEIC"
        ImageFormat.RGB_565 -> "RGB_565"
        ImageFormat.YUY2 -> "YUY2"
        ImageFormat.YV12 -> "YV12"
        else -> imageFormat.toString()
    }
}
