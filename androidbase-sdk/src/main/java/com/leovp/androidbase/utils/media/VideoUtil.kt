package com.leovp.androidbase.utils.media

import android.media.MediaCodec
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.leovp.androidbase.utils.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2021/5/13 5:25 PM
 */
object VideoUtil {
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun setBitrateDynamically(mediaCodec: MediaCodec, bitrate: Int) {
        kotlin.runCatching {
            val param = Bundle()
            param.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate)
            mediaCodec.setParameters(param)
        }.onFailure { if (LogContext.enableLog) LogContext.log.e("setBitrateDynamically error", it) }
    }
}