package com.ho1ho.androidbase.utils.media

import android.annotation.TargetApi
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.ho1ho.androidbase.R
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.file.FileUtil
import java.util.*

/**
 * Author: Michael Leo
 * Date: 20-6-1 下午1:54
 */
object CameraUtil {

    @Suppress("WeakerAccess")
    const val REQUEST_CODE_OPEN_CAMERA = 0x2234

    @Suppress("WeakerAccess")
    const val REQUEST_CODE_CAMERA_CROP = 0x2235

    @Suppress("WeakerAccess")
    const val REQUEST_CODE_OPEN_GALLERY = 0x2236

    private const val TAG = "CameraUtil"

    fun takePhoto(ctx: Activity): Uri? {
        var imageUri: Uri? = null
        val takePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePhotoIntent.resolveActivity(ctx.packageManager) != null) {
            val imageFile = FileUtil.createImageFile(ctx, "jpg")
            CLog.i(TAG, "takePhoto Image saved path=${imageFile.absolutePath}")
            //            boolean deleteFlag = imageFile.delete();
//            Log.w(TAG, "deleteFlag=" + deleteFlag);
            imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Above Android 7.0, we need convert File to Uri through FileProvider.
                FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", imageFile)
            } else {
                // Below Android 7.0. Directly using Uri to convert File to Uri.
                Uri.fromFile(imageFile)
            }
            takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            ctx.startActivityForResult(takePhotoIntent, REQUEST_CODE_OPEN_CAMERA)
        }
        return imageUri
    }

    fun performCrop(ctx: Activity, srcImageUri: Uri?): Uri? {
        var croppedImageUri: Uri? = null
        try {
            //call the standard crop action intent (the user device may not support it)
            val cropIntent = Intent("com.android.camera.action.CROP")


            //indicate image type and Uri
            cropIntent.setDataAndType(srcImageUri, "image/*")
            //set crop properties
//            cropIntent.putExtra("crop", "true");
//            //indicate aspect of desired crop
//            cropIntent.putExtra("aspectX", 1);
//            cropIntent.putExtra("aspectY", 1);
//            //indicate output X and Y
//            cropIntent.putExtra("outputX", 256);
//            cropIntent.putExtra("outputY", 256);
//            //retrieve data on return
//            cropIntent.putExtra("return-data", true);
            // You must grant read uri permission. Or else app will be crashed.
            cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            //start the activity - we handle returning in onActivityResult
//             startActivityForResult(cropIntent, PIC_CROP);
            val croppedImageFile = FileUtil.createImageFile(ctx, "jpg")
            CLog.w(TAG, "Cropped image saved path=${croppedImageFile.absolutePath}")
            //            boolean deleteFlag = imageFile.delete();
//            Log.w(TAG, "deleteFlag=" + deleteFlag);

            // Only Uri.fromeFile can be used for cropping output Uri.
            // You CAN NOT use FileProvider.getUriForFile
            croppedImageUri = Uri.fromFile(croppedImageFile)
            cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, croppedImageUri)
            ctx.startActivityForResult(cropIntent, REQUEST_CODE_CAMERA_CROP)
        } catch (e: ActivityNotFoundException) {
            CLog.e(TAG, "performCrop error", e)
        }
        return croppedImageUri
    }

    fun openGallery(act: Activity, multiple: Boolean) {
        val getIntent = Intent(Intent.ACTION_GET_CONTENT)
        getIntent.type = "image/*"
        getIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)

        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickIntent.type = "image/*"

        val chooserIntent = Intent.createChooser(getIntent, act.getString(R.string.cmn_chooser_gallery))
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pickIntent))
        act.startActivityForResult(chooserIntent, REQUEST_CODE_OPEN_GALLERY)
    }

    @TargetApi(19)
    fun handleImageAboveKitKat(ctx: Context, data: Intent?): List<String> {
        val selectedImage: MutableList<String> = ArrayList()
        data?.data?.let { url -> FileUtil.getImageRealFilePath(ctx, url)?.let { imagePath -> selectedImage.add(imagePath) } }
        data?.clipData?.let {
            for (i in 0 until it.itemCount) {
                FileUtil.getImageRealFilePath(ctx, it.getItemAt(i).uri)?.let { selectedImage.add(it) }
            }
        }
        return selectedImage
    }
}