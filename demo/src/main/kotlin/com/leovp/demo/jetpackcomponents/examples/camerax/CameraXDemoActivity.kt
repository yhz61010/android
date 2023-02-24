package com.leovp.demo.jetpackcomponents.examples.camerax

import android.graphics.Bitmap
import com.leovp.android.exts.getBaseDirString
import com.leovp.camerax.CameraXActivity
import com.leovp.camerax.bean.CaptureImage
import com.leovp.camerax.enums.CapturedImageStrategy
import com.leovp.camerax.listeners.CaptureImageListener
import com.leovp.image.toBitmapFromBytes
import com.leovp.image.writeToFile
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
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
        private const val FILENAME = "yyyyMMdd_HHmmss_SSS"
    }

    override fun getOutputCapturedImageStrategy() = CapturedImageStrategy.FILE

    /** You can implement `CaptureImageListener` or `SimpleCaptureImageListener` */
    override fun getCaptureListener(): CaptureImageListener {
        return object : CaptureImageListener {
            override fun onSavedImageUri(savedImage: CaptureImage.ImageUri?, exc: Exception?) {
                LogContext.log.w(
                    ITAG,
                    "onSavedImageUri uri=${savedImage?.fileUri} path=${savedImage?.fileUri?.path} Exc: $exc"
                )

                if (savedImage == null) return

                // To verify the original bitmap orientation.
                // val bmp = BitmapFactory.decodeFile(savedImage.fileUri.path!!)
                // val newFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CameraX")
                // bmp.writeToFile(File(newFile, "new.jpg"))
            }

            override fun onSavedImageBytes(savedImage: CaptureImage.ImageBytes?, exc: Exception?) {
                LogContext.log.w(ITAG, "onSavedImageBytes=$savedImage Exc: $exc")
                if (savedImage == null) return

                savedImage.imgBytes.toBitmapFromBytes(
                    savedImage.width,
                    savedImage.height,
                    Bitmap.Config.ARGB_8888
                )?.apply {
                    val oriOutFile = File(
                        getBaseDirString("Leo"),
                        "${SimpleDateFormat(FILENAME, Locale.US).format(System.currentTimeMillis())}.jpg"
                    )
                    writeToFile(oriOutFile)
                    recycle()
                    LogContext.log.w(ITAG, "oriOutFile=${oriOutFile.absolutePath}")
                }

                //            val outUri: Uri? =
                //                    getContentUriForFilePath(outFile.absolutePath, this@CameraXDemoActivity)
                //            LogContext.log.i(ITAG, "outUri=$outUri")
                //            outUri?.let { uri ->
                //                setOrientation(uri, savedImage.rotationDegrees, this@CameraXDemoActivity)
                //            }

                //            ExifUtil.saveExif(outFile.absolutePath, savedImage.width, savedImage.height, savedImage)
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
}
