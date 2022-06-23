@file:Suppress("unused")

package com.leovp.lib_image

import android.content.res.Resources
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
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer


/**
 * Author: Michael Leo
 * Date: 2021/7/30 10:10
 */

// ====================
@Keep
data class ImageInfo(val width: Int, val height: Int, val type: String)

// https://developer.android.com/topic/performance/graphics/load-bitmap#read-bitmap
fun getImageInfo(resources: Resources, @DrawableRes resId: Int): ImageInfo {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeResource(resources, resId, options)
    return ImageInfo(options.outWidth, options.outHeight, options.outMimeType)
}

/**
 * @param t Supported following type:
 * - String: File path name
 * - ByteArray: Image bytes
 * - InputStream: Image input stream
 *
 * https://developer.android.com/topic/performance/graphics/load-bitmap#read-bitmap
 */
inline fun <reified T> getImageInfo(t: T & Any): ImageInfo {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    when (t) {
        is String      -> BitmapFactory.decodeFile(t, options) // t is pathName
        is ByteArray   -> BitmapFactory.decodeByteArray(t, 0, t.size, options)
        is InputStream -> BitmapFactory.decodeStream(t, null, options)
        else           -> throw IllegalArgumentException("Unsupported type ${t::class.java}")
    }
    return ImageInfo(options.outWidth, options.outHeight, options.outMimeType)
}

// ====================

fun Bitmap?.recycledSafety() {
    if (this != null && !this.isRecycled) this.recycle()
}

/**
 * Convert bitmap to bytes.
 */
fun Bitmap.toBytes(): ByteArray {
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
fun ByteArray.toBitmapFromBytes(width: Int,
    height: Int,
    config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
    return runCatching {
        Bitmap.createBitmap(width, height, config).also {
            it.copyPixelsFromBuffer(ByteBuffer.wrap(this))
        }
    }.getOrNull()
}

fun Drawable.getBitmap(config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
    // API < 26
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || this is BitmapDrawable) {
        return (this as BitmapDrawable).bitmap
    }
    return runCatching {
        if (this is AdaptiveIconDrawable) {
            val layerDrawable = LayerDrawable(arrayOf<Drawable>(this.background, this.foreground))
            val width = layerDrawable.intrinsicWidth
            val height = layerDrawable.intrinsicHeight
            val bitmap = Bitmap.createBitmap(width, height, config)
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
 * Using this method will only reduce the bitmap file size NOT the bitmap size loaded in memory.
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
