package com.leovp.demo.jetpack_components.examples.camerax

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.exifinterface.media.ExifInterface.LIGHT_SOURCE_UNKNOWN
import com.leovp.camerax_sdk.CameraXActivity
import com.leovp.camerax_sdk.bean.CaptureImage
import com.leovp.camerax_sdk.listeners.CaptureImageListener
import com.leovp.lib_common_android.exts.getBaseDirString
import com.leovp.lib_image.writeToFile
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


/**
 * Add following `<activity>` in your `<AndroidManifest.xml>`.
 *
 * Attention:
 * Make sure you've set `android:screenOrientation` to "userPortrait".
 *
 * ```xml
 * <activity android:name=".jetpack_components.examples.camerax.CameraXDemoActivity"
 *     android:configChanges="orientation|screenLayout|screenSize|smallestScreenSize"
 *     android:exported="false"
 *     android:resizeableActivity="true"
 *     android:rotationAnimation="seamless"
 *     android:screenOrientation="userPortrait"
 *     android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar"
 *     tools:ignore="LockedOrientationActivity"
 *     tools:targetApi="O">
 *     <!-- Declare notch support -->
 *     <meta-data android:name="android.notch_support"
 *     android:value="true" />
 * </activity>
 * ```
 *
 * Author: Michael Leo
 * Date: 2022/4/25 14:50
 */
class CameraXDemoActivity : CameraXActivity() {
    companion object {
        private val SDF = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH)
    }

    override fun allowToOutputCaptureFile() = true

    /** You can implement `CaptureImageListener` or `SimpleCaptureImageListener` */
    override var captureImageListener: CaptureImageListener? = object : CaptureImageListener {
        override fun onSavedImageFile(savedImage: CaptureImage.ImageUri) {
            LogContext.log.w(ITAG,
                "onSavedImageUri rotationDegrees=${savedImage.rotationDegrees} " + "mirror=${savedImage.mirror} " + "uri=${savedImage.fileUri} path=${savedImage.fileUri.path}")

//            val filePath: String = savedImage.fileUri.path!!
//            saveExif(filePath, savedImage = savedImage)
        }

        override fun onSavedImageBytes(savedImage: CaptureImage.ImageBytes) {
            LogContext.log.w(ITAG, "onSavedImageBytes=$savedImage")

            val outFile = File(getBaseDirString("Leo"), "" + System.currentTimeMillis() + ".jpg")
            BitmapFactory.decodeByteArray(savedImage.imgBytes, 0, savedImage.imgBytes.size).run {
                writeToFile(outFile)
                recycle()
            }

//            val outUri: Uri? = getContentUriForFilePath(outFile.absolutePath, this@CameraXDemoActivity)
//            LogContext.log.i(ITAG, "outUri=$outUri")
//            outUri?.let { uri ->
//                setOrientation(uri, savedImage.rotationDegrees, this@CameraXDemoActivity)
//            }

            saveExif(outFile.absolutePath, savedImage.width, savedImage.height, savedImage)
        }
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
     *   4	  bottom        left      flip vertical
     *   5	    left         top      transpose(90°+flip horizontal)
     *   6	   right         top      90°
     *   7	   right      bottom      transverse(90°+flip vertical)
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
    private fun saveExif(filePath: String, width: Int? = null, height: Int? = null, savedImage: CaptureImage) {
        ExifInterface(filePath).apply {
            setAttribute(ExifInterface.TAG_CAMERA_OWNER_NAME, "Leo Camera")
            setAttribute(ExifInterface.TAG_COPYRIGHT, "Michael Leo")
            setAttribute(ExifInterface.TAG_DATETIME, SDF.format(Date()))
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, SDF.format(Date()))
            setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, SDF.format(Date()))
            width?.let { setAttribute(ExifInterface.TAG_IMAGE_WIDTH, it.toString()) }
            height?.let { setAttribute(ExifInterface.TAG_IMAGE_LENGTH, it.toString()) }
            setAttribute(ExifInterface.TAG_LIGHT_SOURCE, LIGHT_SOURCE_UNKNOWN.toString())
            val rotateString = when (savedImage.rotationDegrees) {
                0    -> ExifInterface.ORIENTATION_NORMAL.toString()
                90   -> ExifInterface.ORIENTATION_ROTATE_90.toString()
                180  -> ExifInterface.ORIENTATION_ROTATE_180.toString()
                270  -> ExifInterface.ORIENTATION_ROTATE_270.toString()
                else -> throw IllegalArgumentException("Illegal orientation: ${savedImage.rotationDegrees}")
            }
            setAttribute(ExifInterface.TAG_ORIENTATION, rotateString)
            saveAttributes()
        }
    }

//    /**
//     * @param fileUri the media store file uri
//     * @param orientation in degrees 0, 90, 180, 270
//     * @param context
//     * @return
//     */
//    fun setOrientation(fileUri: Uri, orientation: Int, context: Context): Boolean {
//        val values = ContentValues()
//        values.put(MediaStore.Images.Media.ORIENTATION, orientation)
//        val rowsUpdated: Int = context.contentResolver.update(fileUri, values, null, null)
//        return rowsUpdated > 0
//    }
//
//    /**
//     * Get content uri for the file path
//     *
//     * @param path
//     * @param context
//     * @return
//     */
//    fun getContentUriForFilePath(path: String, context: Context): Uri? {
//        val projection = arrayOf(MediaStore.Images.Media._ID)
//        var result: Uri? = null
//        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            projection,
//            MediaStore.Images.Media.DATA + " = ?",
//            arrayOf(path),
//            null)?.use { cursor ->
//            if (cursor.moveToNext()) {
//                val mediaId: Long = cursor.getLong(0)
//                result = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
//            }
//        }
//        return result
//    }
}