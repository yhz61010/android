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
     * This method is not only slower but also generated larger file
     * than Android [Bitmap.compress] method.
     */
    external fun compressBitmap(
        bitmap: Bitmap,
        quality: Int,
        outFilPath: String,
        optimize: Boolean
    ): Int
}
