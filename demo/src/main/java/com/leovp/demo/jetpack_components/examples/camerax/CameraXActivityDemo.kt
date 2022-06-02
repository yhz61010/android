package com.leovp.demo.jetpack_components.examples.camerax

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
        override fun onSavedImageUri(savedUri: Uri, rotationInDegree: Int, mirror: Boolean) {
            LogContext.log.w(ITAG, "onSavedImageUri rotationInDegree=$rotationInDegree mirror=$mirror uri=$savedUri")
        }

        override fun onSavedImageBytes(savedImage: CaptureImage) {
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

            ExifInterface(outFile.absolutePath).apply {
                setAttribute(ExifInterface.TAG_CAMERA_OWNER_NAME, "Leo Camera")
                setAttribute(ExifInterface.TAG_DATETIME, SDF.format(Date()))
//                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, sdf1.format(Date()))
//                setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, sdf2.format(Date()))
                setAttribute(ExifInterface.TAG_IMAGE_WIDTH, savedImage.width.toString())
                setAttribute(ExifInterface.TAG_IMAGE_LENGTH, savedImage.height.toString())
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