package com.ho1ho.androidbase.utils.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Author: Michael Leo
 * Date: 20-5-19 上午10:14
 */
object ImageUtil {
    fun createBitmapFromImage(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    /**
     * Using this method will only reduce the bitmap file size. Not the bitmap size loaded in memory.
     * It's better to release the source bitmap by calling Bitmap.recycle() after calling this method.
     */
    fun compressBitmap(bitmap: Bitmap, quality: Int = 100, sampleSize: Int = 1): Bitmap {
        val compressedBmpOS = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, compressedBmpOS)
        val opt = BitmapFactory.Options()
        opt.inSampleSize = sampleSize
        return BitmapFactory.decodeByteArray(compressedBmpOS.toByteArray(), 0, compressedBmpOS.size(), opt)
    }

    /**
     * Bitmap.compress() method will only reduce the bitmap file size. Not the bitmap size loaded in memory.
     */
    fun writeBitmapToFile(outputFile: File, bitmap: Bitmap, quality: Int = 100) {
        val outputStream = FileOutputStream(outputFile)
        outputStream.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.flush()
        }
    }
}