package com.leovp.jpeg_sdk

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

    external fun compressBitmap(bitmap: Bitmap,
        quality: Int,
        outFilPath: String,
        optimize: Boolean): Int
}