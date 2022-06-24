package com.leovp.lib_image

import android.graphics.Bitmap
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Usage:
 * ```
 * val bmpProcessor = BitmapProcessor(bmp)
 * bmpProcessor.flipBitmapHorizontal()
 * bmpProcessor.rotateBitmapCw90()
 * val newBmp: Bitmap? = bmpProcessor.bitmap
 * bmpProcessor.free()
 * ```
 *
 *
 * Author: Michael Leo
 * Date: 2022/6/23 14:32
 */
class BitmapProcessor(bitmap: Bitmap) : Closeable {
    init {
        setBitmap(bitmap)
    }

    companion object {
        init {
            System.loadLibrary("leo-bitmap")
        }
    }

    var bitmapByteBuffer: ByteBuffer? = null

    enum class ScaleMethod {
        NearestNeighbour, BilinearInterpolation
    }

    private external fun setBitmapData(bitmap: Bitmap): ByteBuffer
    private external fun getBitmapFromSavedBitmapData(handler: ByteBuffer): Bitmap
    private external fun freeBitmapData(handler: ByteBuffer)

    private external fun rotateBitmapCcw90(handler: ByteBuffer)
    private external fun rotateBitmapCw90(handler: ByteBuffer)
    private external fun rotateBitmap180(handler: ByteBuffer)

    private external fun cropBitmap(handler: ByteBuffer,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int)

    private external fun scaleNNBitmap(handler: ByteBuffer, newWidth: Int, newHeight: Int)
    private external fun scaleBIBitmap(handler: ByteBuffer, newWidth: Int, newHeight: Int)

    private external fun flipBitmapHorizontal(handler: ByteBuffer)
    private external fun flipBitmapVertical(handler: ByteBuffer)

    fun setBitmap(bitmap: Bitmap) {
        if (bitmapByteBuffer != null) free()
        bitmapByteBuffer = setBitmapData(bitmap)
    }

    fun rotateBitmapCcw90() = bitmapByteBuffer?.let { rotateBitmapCcw90(it) }

    fun rotateBitmapCw90() = bitmapByteBuffer?.let { rotateBitmapCw90(it) }

    fun rotateBitmap180() = bitmapByteBuffer?.let { rotateBitmap180(it) }

    fun cropBitmap(left: Int, top: Int, right: Int, bottom: Int) =
            bitmapByteBuffer?.let { cropBitmap(it, left, top, right, bottom) }

    val bitmap: Bitmap? get() = bitmapByteBuffer?.let { getBitmapFromSavedBitmapData(it) }
    fun getBitmapAndFree(): Bitmap? {
        val bmp = bitmap
        free()
        return bmp
    }

    fun scaleBitmap(newWidth: Int,
        newHeight: Int,
        scaleMethod: ScaleMethod = ScaleMethod.NearestNeighbour) {
        val innerHandler = bitmapByteBuffer ?: return
        when (scaleMethod) {
            ScaleMethod.BilinearInterpolation -> scaleBIBitmap(innerHandler, newWidth, newHeight)
            ScaleMethod.NearestNeighbour      -> scaleNNBitmap(innerHandler, newWidth, newHeight)
        }
    }

    /**
     * flips a bitmap horizontally, as such: <br></br>
     *
     * <pre>
     * 123    321
     * 456 => 654
     * 789    987
    </pre> *
     */
    //
    fun flipBitmapHorizontal() = bitmapByteBuffer?.let { flipBitmapHorizontal(it) }

    /**
     * Flips the bitmap on the vertically, as such:<br></br>
     *
     * <pre>
     * 123    789
     * 456 => 456
     * 789    123
    </pre> *
     */
    fun flipBitmapVertical() = bitmapByteBuffer?.let { flipBitmapVertical(it) }

    fun free() {
        bitmapByteBuffer?.let {
            freeBitmapData(it)
            bitmapByteBuffer = null
        }
    }

    /**
     * To override finalize(), all you need to do is simply declare it, without using the override keyword.
     *
     * https://kotlinlang.org/docs/java-interop.html#finalize
     */
    protected fun finalize() {
        if (bitmapByteBuffer == null) return
        Log.w("LEO-Native",
            "JNI bitmap wasn't freed manually. Free it by finalize automatically. " +
                    "You'd better to free the bitmap as soon as you can.")
        free()
    }

    override fun close() {
        if (bitmapByteBuffer == null) return
        free()
    }
}