package com.leovp.image

import android.graphics.Bitmap
import android.util.Log
import java.io.Closeable

/**
 * Performs bitmap transformations in native memory.
 *
 * Use [use] or call [close] as soon as the processor is no longer needed.
 *
 * Author: Michael Leo
 * Date: 2022/6/23 14:32
 */
class BitmapProcessor(bitmap: Bitmap) : Closeable {
    companion object {
        init {
            System.loadLibrary("leo-bitmap")
        }
    }

    enum class ScaleMethod {
        NearestNeighbour,
        BilinearInterpolation
    }

    private var nativeHandle: Long = nativeSetBitmapData(bitmap)
    private var closed: Boolean = false

    init {
        check(nativeHandle != 0L) { "Unable to create native Bitmap data." }
    }

    private external fun nativeSetBitmapData(bitmap: Bitmap): Long
    private external fun nativeGetBitmap(handle: Long): Bitmap
    private external fun nativeFreeBitmapData(handle: Long)
    private external fun nativeRotateBitmapCcw90(handle: Long)
    private external fun nativeRotateBitmapCw90(handle: Long)
    private external fun nativeRotateBitmap180(handle: Long)

    private external fun nativeCropBitmap(
        handle: Long,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    )

    private external fun nativeScaleNNBitmap(handle: Long, newWidth: Int, newHeight: Int)
    private external fun nativeScaleBIBitmap(handle: Long, newWidth: Int, newHeight: Int)
    private external fun nativeFlipBitmapHorizontal(handle: Long)
    private external fun nativeFlipBitmapVertical(handle: Long)

    @Synchronized
    fun setBitmap(bitmap: Bitmap) {
        checkOpen()
        val newHandle = nativeSetBitmapData(bitmap)
        check(newHandle != 0L) { "Unable to create native Bitmap data." }
        val oldHandle = nativeHandle
        nativeHandle = newHandle
        nativeFreeBitmapData(oldHandle)
    }

    @Synchronized
    fun rotateBitmapCcw90() = nativeRotateBitmapCcw90(requireHandle())

    @Synchronized
    fun rotateBitmapCw90() = nativeRotateBitmapCw90(requireHandle())

    @Synchronized
    fun rotateBitmap180() = nativeRotateBitmap180(requireHandle())

    @Synchronized
    fun cropBitmap(left: Int, top: Int, right: Int, bottom: Int) =
        nativeCropBitmap(requireHandle(), left, top, right, bottom)

    val bitmap: Bitmap
        @Synchronized get() = nativeGetBitmap(requireHandle())

    @Synchronized
    fun getBitmapAndFree(): Bitmap = try {
        nativeGetBitmap(requireHandle())
    } finally {
        close()
    }

    @Synchronized
    fun scaleBitmap(
        newWidth: Int,
        newHeight: Int,
        scaleMethod: ScaleMethod = ScaleMethod.NearestNeighbour
    ) {
        val handle = requireHandle()
        when (scaleMethod) {
            ScaleMethod.BilinearInterpolation -> nativeScaleBIBitmap(handle, newWidth, newHeight)
            ScaleMethod.NearestNeighbour -> nativeScaleNNBitmap(handle, newWidth, newHeight)
        }
    }

    /** Flips the bitmap horizontally. */
    @Synchronized
    fun flipBitmapHorizontal() = nativeFlipBitmapHorizontal(requireHandle())

    /** Flips the bitmap vertically. */
    @Synchronized
    fun flipBitmapVertical() = nativeFlipBitmapVertical(requireHandle())

    /** Releases the native bitmap. Repeated calls have no effect. */
    fun free() = close()

    @Synchronized
    override fun close() {
        if (closed) return
        val handle = nativeHandle
        nativeHandle = 0L
        closed = true
        nativeFreeBitmapData(handle)
    }

    @Suppress("deprecation")
    protected fun finalize() {
        if (closed) return
        Log.w(
            "LEO-Native",
            "JNI bitmap was not closed explicitly. Release it with use {} or close()."
        )
        runCatching { close() }
    }

    private fun requireHandle(): Long {
        checkOpen()
        return nativeHandle
    }

    private fun checkOpen() {
        check(!closed && nativeHandle != 0L) { "BitmapProcessor is closed." }
    }
}
