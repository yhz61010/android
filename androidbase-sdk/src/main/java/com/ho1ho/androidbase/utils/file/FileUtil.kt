package com.ho1ho.androidbase.utils.file

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午2:04
 */
object FileUtil {

    fun getLogDir(ctx: Context, baseFolderName: String): File {
        val builder = getBaseDirString(ctx, baseFolderName) + File.separator + "log"
        val dir = File(builder)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Suppress("WeakerAccess")
    fun getBaseDirString(ctx: Context, baseFolderName: String): String {
        return ctx.getExternalFilesDir(null)?.let {
            it.absolutePath + File.separator + baseFolderName
        } ?: ""
    }

    @Suppress("unused")
    fun resourceToUri(context: Context, resId: Int): Uri? {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://"
                    + context.resources.getResourcePackageName(resId)
                    + "/"
                    + context.resources.getResourceTypeName(resId)
                    + "/"
                    + context.resources.getResourceEntryName(resId)
        )
    }

    //----------------------------------------------------
    @Suppress("unused")
    fun getImageRealFilePath(ctx: Context, uri: Uri): String? {
        var imagePath: String? = null
        if (DocumentsContract.isDocumentUri(ctx, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            if ("com.android.providers.media.documents" == uri.authority) {
                val id = docId.split(":").toTypedArray()[1]
                val selection = MediaStore.Images.Media._ID + "=" + id
                imagePath = getImagePath(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection)
            } else if ("com.android.providers.downloads.documents" == uri.authority) {
                val contentUri =
                    ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(docId))
                imagePath = getImagePath(ctx, contentUri, null)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            imagePath = getImagePath(ctx, uri, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            imagePath = uri.path
        }
        return imagePath
    }

    private fun getImagePath(ctx: Context, uri: Uri, selection: String?): String? {
        ctx.contentResolver.query(uri, null, selection, null, null)?.use {
            if (it.moveToFirst()) return it.getString(it.getColumnIndex(MediaStore.Images.Media.DATA))
        }
        return null
    }

    /**
     * Usage:
     *
     * ```xml
     *  <provider
     *   android:name="androidx.core.content.FileProvider"
     *   android:authorities="${applicationId}.fileprovider"
     *   android:exported="false"
     *   android:grantUriPermissions="true">
     *  <meta-data
     *      android:name="android.support.FILE_PROVIDER_PATHS"
     *      android:resource="@xml/path" />
     * </provider>
     * ```
     */
    fun getFileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.applicationContext.packageName + ".fileprovider", file)

    /**
     * Create a [File] named a using formatted timestamp with the current date and time.
     *
     * @return [File] created.
     */
    fun createImageFile(ctx: Context, extension: String): File {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        return File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "IMG_${sdf.format(Date())}.$extension")
    }
}