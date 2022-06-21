@file:Suppress("unused")

package com.leovp.lib_image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.media.Image
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer


/**
 * Author: Michael Leo
 * Date: 2021/7/30 10:10
 */

fun Bitmap?.recycledSafety() {
    if (this != null && !this.isRecycled) this.recycle()
}

/**
 * Convert ARGB bitmap to bytes.
 */
fun Bitmap.toARGBBytes(): ByteArray {
    val size = this.byteCount

    val buffer = ByteBuffer.allocate(size)
    val bytes = ByteArray(size)

    this.copyPixelsToBuffer(buffer)
    buffer.rewind()
    buffer.get(bytes)

    return bytes
}

/**
 * Convert ARGB bitmap bytes to Bitmap.
 */
fun ByteArray.toBitmapFromARGBBytes(width: Int, height: Int): Bitmap? {
    return runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.copyPixelsFromBuffer(ByteBuffer.wrap(this))
        }
    }.getOrNull()
}

fun Drawable.getBitmap(): Bitmap? {
    // API < 26
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || this is BitmapDrawable) {
        return (this as BitmapDrawable).bitmap
    }
    return runCatching {
        if (this is AdaptiveIconDrawable) {
            val layerDrawable = LayerDrawable(arrayOf<Drawable>(this.background, this.foreground))
            val width = layerDrawable.intrinsicWidth
            val height = layerDrawable.intrinsicHeight
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            layerDrawable.setBounds(0, 0, canvas.width, canvas.height)
            layerDrawable.draw(canvas)
            bitmap
        } else {
            null
        }
    }.getOrNull()
}

fun Image.createBitmap(config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
    val width = this.width
    val height = this.height
    val planes = this.planes
    val buffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * width
    val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, config)
    bitmap.copyPixelsFromBuffer(buffer)
    return bitmap
}

/**
 * Using this method will only reduce the bitmap file size. Not the bitmap size loaded in memory.
 * It's better to release the source bitmap by calling Bitmap.recycle() after calling this method.
 */
fun Bitmap.compressBitmap(quality: Int = 100,
    sampleSize: Int = 1,
    imgType: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): Bitmap {
    val compressedBmpOS = ByteArrayOutputStream()
    this.compress(imgType, quality, compressedBmpOS)
    val opt = BitmapFactory.Options()
    opt.inSampleSize = sampleSize
    return BitmapFactory.decodeByteArray(compressedBmpOS.toByteArray(),
        0,
        compressedBmpOS.size(),
        opt)
}

/**
 * Bitmap.compress() method will only reduce the bitmap file size. Not the bitmap size loaded in memory.
 */
fun Bitmap.writeToFile(outputFile: File,
    quality: Int = 100,
    imgType: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG) {
    val outputStream = FileOutputStream(outputFile)
    outputStream.use {
        this.compress(imgType, quality, outputStream)
        outputStream.flush()
    }
}

fun File?.getBitmap(): Bitmap? =
        if (this == null) null else BitmapFactory.decodeFile(this.absolutePath)

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * @param x The center x position of image.
 * @param y The center y position of image.
 */
fun Bitmap.flipHorizontal(x: Float = width / 2f, y: Float = height / 2f): Bitmap {
    val matrix = Matrix().apply { postScale(-1f, 1f, x, y) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * @param x The center x position of image.
 * @param y The center y position of image.
 */
fun Bitmap.flipVertical(x: Float = width / 2f, y: Float = height / 2f): Bitmap {
    val matrix = Matrix().apply { postScale(1f, -1f, x, y) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * @param x The center x position of image.
 * @param y The center y position of image.
 */
fun Bitmap.flip(
    horizontal: Boolean,
    vertical: Boolean,
    x: Float = width / 2f,
    y: Float = height / 2f): Bitmap {
    val matrix = Matrix().apply {
        postScale(
            if (horizontal) -1f else 1f,
            if (vertical) -1f else 1f,
            x, y)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * Flip firstly then rotate image.
 *
 * @param x The center x position of image.
 * @param y The center y position of image.
 */
fun Bitmap.flipRotate(
    horizontal: Boolean,
    vertical: Boolean,
    degrees: Float,
    x: Float = width / 2f,
    y: Float = height / 2f): Bitmap {

    val matrix = Matrix().apply {
        postScale(
            if (horizontal) -1f else 1f,
            if (vertical) -1f else 1f,
            x, y)
        postRotate(degrees)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
