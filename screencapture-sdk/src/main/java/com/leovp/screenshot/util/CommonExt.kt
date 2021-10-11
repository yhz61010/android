package com.leovp.screenshot.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import java.io.ByteArrayOutputStream

/**
 * Author: Michael Leo
 * Date: 2021/10/11 16:29
 */

/**
 * Using this method will only reduce the bitmap file size. Not the bitmap size loaded in memory.
 * It's better to release the source bitmap by calling Bitmap.recycle() after calling this method.
 */
fun Bitmap.compressBitmap(quality: Int = 100, sampleSize: Int = 1, imgType: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): Bitmap {
    val compressedBmpOS = ByteArrayOutputStream()
    this.compress(imgType, quality, compressedBmpOS)
    val opt = BitmapFactory.Options()
    opt.inSampleSize = sampleSize
    return BitmapFactory.decodeByteArray(compressedBmpOS.toByteArray(), 0, compressedBmpOS.size(), opt)
}

fun Image.createBitmap(): Bitmap {
    val width = this.width
    val height = this.height
    val planes = this.planes
    val buffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * width
    val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(buffer)
    return bitmap
}