@file:Suppress("unused")

/**
 * Author: Michael Leo
 * Date: 2022/6/2 18:52
 */

package com.leovp.lib_exif

import android.graphics.Matrix
import androidx.annotation.Keep
import androidx.exifinterface.media.ExifInterface
import androidx.exifinterface.media.ExifInterface.LIGHT_SOURCE_UNKNOWN
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val SDF = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH)

/** Transforms rotation and mirroring information into one of the [ExifInterface] constants */
fun computeExifOrientation(rotationDegrees: Int, mirrored: Boolean): Int = when {
    rotationDegrees == 0 && !mirrored   -> ExifInterface.ORIENTATION_NORMAL
    rotationDegrees == 90 && !mirrored  -> ExifInterface.ORIENTATION_ROTATE_90
    rotationDegrees == 180 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_180
    rotationDegrees == 270 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_270

    rotationDegrees == 0 && mirrored    -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
    rotationDegrees == 90 && mirrored   -> ExifInterface.ORIENTATION_TRANSPOSE
    rotationDegrees == 180 && mirrored  -> ExifInterface.ORIENTATION_FLIP_VERTICAL
    rotationDegrees == 270 && mirrored  -> ExifInterface.ORIENTATION_TRANSVERSE

    else                                -> ExifInterface.ORIENTATION_UNDEFINED
}

/**
 * Helper function used to convert an EXIF orientation enum into a transformation matrix
 * that can be applied to a bitmap.
 *
 * @return matrix - Transformation required to properly display [Bitmap]
 */
fun decodeExifOrientation(exifOrientation: Int): Matrix {
    val matrix = Matrix()

    // Apply transformation corresponding to declared EXIF orientation
    when (exifOrientation) {
        ExifInterface.ORIENTATION_NORMAL          -> Unit
        ExifInterface.ORIENTATION_UNDEFINED       -> Unit
        ExifInterface.ORIENTATION_ROTATE_90       -> matrix.postRotate(90F)
        ExifInterface.ORIENTATION_ROTATE_180      -> matrix.postRotate(180F)
        ExifInterface.ORIENTATION_ROTATE_270      -> matrix.postRotate(270F)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1F, 1F)
        ExifInterface.ORIENTATION_FLIP_VERTICAL   -> matrix.postScale(1F, -1F)
        ExifInterface.ORIENTATION_TRANSPOSE       -> {
            matrix.postScale(-1F, 1F)
            matrix.postRotate(270F)
        }
        ExifInterface.ORIENTATION_TRANSVERSE      -> {
            matrix.postScale(-1F, 1F)
            matrix.postRotate(90F)
        }

        // Error out if the EXIF orientation is invalid
        else                                      -> throw IllegalArgumentException("Invalid orientation: $exifOrientation")
    }

    // Return the resulting matrix
    return matrix
}

/**
 * Orientation: The orientation of the camera relative to the scene, when the image was captured.
 *              The start point of stored data is,
 *              '1' means upper left,
 *              '3' lower right,
 *              '6' upper right,
 *              '8' lower left,
 *              '9' undefined.
 *
 * https://www.media.mit.edu/pia/Research/deepview/exif.html
 * http://sylvana.net/jpegcrop/exif_orientation.html
 *
 * ```
 * Value    0th Row   0th Column  Rotation(clockwise)
 *   1	     top        left      0°
 *   2	     top       right      flip horizontal
 *   3	  bottom       right      180°
 *   4	  bottom        left      flip vertical(flip horizontal+180°)
 *   5	    left         top      transpose(flip horizontal+270° or 90°+flip horizontal)
 *   6	   right         top      90°
 *   7	   right      bottom      transverse(flip horizontal+90° or 90°+flip vertical)
 *   8	    left      bottom      270°
 * ```
 *
 * ```
 *     1        2       3      4         5            6           7          8
 *
 *   888888  888888      88  88      8888888888  88                  88  8888888888
 *   88          88      88  88      88  88      88  88          88  88      88  88
 *   8888      8888    8888  8888    88          8888888888  8888888888          88
 *   88          88      88  88
 *   88          88  888888  888888
 * ```
 *
 * ![orient_flag](http://lib.leovp.com/resources/jpeg/orient_flag.jpg)
 *
 * https://blog.csdn.net/ouyangtianhan/article/details/29825885
 */
fun File.saveExif(exif: ExifInfo) {
    ExifInterface(this).apply {
        //            setAttribute(ExifInterface.TAG_CAMERA_OWNER_NAME, "Leo Camera")
        //            setAttribute(ExifInterface.TAG_COPYRIGHT, "Michael Leo")
        setAttribute(ExifInterface.TAG_DATETIME, SDF.format(Date()))
        setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, SDF.format(Date()))
        setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, SDF.format(Date()))
        exif.width?.let { setAttribute(ExifInterface.TAG_IMAGE_WIDTH, it.toString()) }
        exif.height?.let { setAttribute(ExifInterface.TAG_IMAGE_LENGTH, it.toString()) }
        setAttribute(ExifInterface.TAG_LIGHT_SOURCE, LIGHT_SOURCE_UNKNOWN.toString())
        val rotateString =
                computeExifOrientation(exif.rotationDegrees, exif.flipHorizontal).toString()
        setAttribute(ExifInterface.TAG_ORIENTATION, rotateString)
        saveAttributes()
    }
}

@Keep
data class ExifInfo(
    val width: Int? = null,
    val height: Int? = null,
    val flipHorizontal: Boolean = false,
    val rotationDegrees: Int = 0
)