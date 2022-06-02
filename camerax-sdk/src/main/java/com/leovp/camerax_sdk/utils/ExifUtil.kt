package com.leovp.camerax_sdk.utils

import androidx.exifinterface.media.ExifInterface
import androidx.exifinterface.media.ExifInterface.LIGHT_SOURCE_UNKNOWN
import com.leovp.camerax_sdk.bean.CaptureImage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Author: Michael Leo
 * Date: 2022/6/2 18:52
 */
object ExifUtil {
    private val SDF = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH)

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
    fun saveExif(filePath: String, width: Int? = null, height: Int? = null, savedImage: CaptureImage) {
        ExifInterface(filePath).apply {
//            setAttribute(ExifInterface.TAG_CAMERA_OWNER_NAME, "Leo Camera")
//            setAttribute(ExifInterface.TAG_COPYRIGHT, "Michael Leo")
            setAttribute(ExifInterface.TAG_DATETIME, SDF.format(Date()))
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, SDF.format(Date()))
            setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, SDF.format(Date()))
            width?.let { setAttribute(ExifInterface.TAG_IMAGE_WIDTH, it.toString()) }
            height?.let { setAttribute(ExifInterface.TAG_IMAGE_LENGTH, it.toString()) }
            setAttribute(ExifInterface.TAG_LIGHT_SOURCE, LIGHT_SOURCE_UNKNOWN.toString())
            val rotateString = if (savedImage.mirror) { // Front camera
                when (savedImage.rotationDegrees) {
                    0    -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString()
                    90   -> ExifInterface.ORIENTATION_TRANSVERSE.toString()
                    180  -> ExifInterface.ORIENTATION_FLIP_VERTICAL.toString()
                    270  -> ExifInterface.ORIENTATION_TRANSPOSE.toString()
                    else -> throw IllegalArgumentException("Illegal orientation: ${savedImage.rotationDegrees}")
                }
            } else { // Back camera
                when (savedImage.rotationDegrees) {
                    0    -> ExifInterface.ORIENTATION_NORMAL.toString()
                    90   -> ExifInterface.ORIENTATION_ROTATE_90.toString()
                    180  -> ExifInterface.ORIENTATION_ROTATE_180.toString()
                    270  -> ExifInterface.ORIENTATION_ROTATE_270.toString()
                    else -> throw IllegalArgumentException("Illegal orientation: ${savedImage.rotationDegrees}")
                }
            }
            setAttribute(ExifInterface.TAG_ORIENTATION, rotateString)
            saveAttributes()
        }
    }
}