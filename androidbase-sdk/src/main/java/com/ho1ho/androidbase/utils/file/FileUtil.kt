package com.ho1ho.androidbase.utils.file

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*


/**
 * Author: Michael Leo
 * Date: 20-5-13 下午2:04
 */
object FileUtil {
    fun copyInputStreamToFile(inputStream: InputStream, fullPath: String) {
        val file = File(fullPath)
        if (!file.exists()) {
            val bufferSize = 8 shl 10
            val readBuffer = ByteArray(bufferSize)
            var readLen: Int
            inputStream.use { input ->
                FileOutputStream(file).buffered(bufferSize).use { output ->
                    while (input.read(readBuffer).also { readLen = it } != -1) {
                        output.write(readBuffer, 0, readLen)
                    }
                }
            }
        }
    }

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

    // ===== Start =================================================
    fun getPathFromUri(context: Context, uri: Uri): String? {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }

                // TODO handle non-primary volumes
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
                )
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {

            // Return the remote address
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment else getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val column = MediaStore.Images.Media._ID// MediaStore.Images.Media.DATA
        val projection = arrayOf(column)
        try {
            cursor = context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index: Int = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }
    // ===== End =================================================
}