package com.leovp.jpeg

import android.graphics.Bitmap
import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 2022/6/23 09:01
 */
@Keep
object JPEGUtil {
    init {
        System.loadLibrary("leo-jpeg")
    }

    /**
     * Compresses an ARGB_8888 [bitmap] into a JPEG file.
     *
     * [quality] must be between 0 and 100. A successful call returns `0`. Invalid parameters,
     * output failures, native encoder failures, and allocation failures are reported as
     * exceptions instead of negative return codes.
     */
    external fun compressBitmap(
        bitmap: Bitmap,
        quality: Int,
        outFilPath: String,
        optimize: Boolean
    ): Int
}
