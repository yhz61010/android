@file:Suppress("unused", "MemberVisibilityCanBePrivate", "WeakerAccess")

package com.leovp.android.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.leovp.android.exts.fileExists
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 20-8-31 上午11:36
 *
 * [Check this post](https://stackoverflow.com/a/50664805)
 */
object FileDocumentUtil {
    private const val TAG = "FDU"

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
        FileProvider.getUriForFile(context, "${context.applicationContext.packageName}.fileprovider", file)

    @SuppressLint("NewApi", "ObsoleteSdkInt")
    fun getFileRealPath(context: Context, uri: Uri): String? {
        return when { // ExternalStorageProvider
            isExternalStorageDocument(uri) -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray() //                val type = split[0]
                getPathFromExtSD(split).takeIf { it.isNotBlank() }
            } // DownloadsProvider
            isDownloadsDocument(uri) -> getDownloadsDocumentRealPath(context, uri) // MediaProvider
            isMediaDocument(uri) -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                if (contentUri == null) return null
                val selection = "_id=?" // DocumentProvider
                val selectionArgs: Array<String> = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }

            isGoogleDriveUri(uri) -> {
                getDriveFilePath(context, uri)
            }

            isWhatsAppFile(uri) -> {
                getFilePathForWhatsApp(context, uri)
            }

            "content".equals(uri.scheme, ignoreCase = true) -> {
                if (isGooglePhotosUri(uri)) {
                    uri.lastPathSegment
                }
                if (isGoogleDriveUri(uri)) {
                    getDriveFilePath(context, uri)
                } // return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // // return getFilePathFromURI(context,uri);
                //     copyFileToInternalStorage(context,
                //         uri,
                //         "userfiles") // return getRealPathFromURI(context,uri);
                // } else {
                //     getDataColumn(context, uri, null, null)
                // }
                getDataColumn(context, uri, null, null)
            }

            "file".equals(uri.scheme, ignoreCase = true) -> {
                uri.path
            }

            else -> null
        }
    }

    private fun getDownloadsDocumentRealPath(context: Context, uri: Uri): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val fileName = cursor.getString(0)
                    val path = Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName
                    if (!TextUtils.isEmpty(path)) {
                        return path
                    }
                }
            } finally {
                cursor?.close()
            }
            val id: String = DocumentsContract.getDocumentId(uri)
            if (!TextUtils.isEmpty(id)) {
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:".toRegex(), "")
                }
                val contentUriPrefixesToTry = arrayOf(
                    "content://downloads/public_downloads",
                    "content://downloads/my_downloads"
                )
                for (contentUriPrefix in contentUriPrefixesToTry) {
                    return try {
                        val contentUri = ContentUris.withAppendedId(
                            Uri.parse(contentUriPrefix),
                            java.lang.Long.valueOf(id)
                        )
                        getDataColumn(context, contentUri, null, null)
                    } catch (e: NumberFormatException) {
                        // In Android 8 and Android P the id is not a number
                        uri.path!!
                            .replaceFirst("^/document/raw:".toRegex(), "")
                            .replaceFirst("^raw:".toRegex(), "")
                    }
                }
            }
        } else {
            val id = DocumentsContract.getDocumentId(uri)
            if (id.startsWith("raw:")) {
                return id.replaceFirst("raw:".toRegex(), "")
            }
            try {
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    java.lang.Long.valueOf(id)
                )
                return getDataColumn(context, contentUri, null, null)
            } catch (e: NumberFormatException) {
                Log.e(TAG, "ContentUris.withAppendedId() exception")
            }
        }

        return null
    }

    fun resourceToUri(context: Context, resId: Int): Uri? {
        return (
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                context.resources.getResourcePackageName(resId) + "/" +
                context.resources.getResourceTypeName(resId) + "/" +
                context.resources.getResourceEntryName(resId)
            ).toUri()
    }

    private fun getPathFromExtSD(pathData: Array<String>): String {
        val type = pathData[0]
        val relativePath = "/" + pathData[1]
        var fullPath: String

        // on my Sony devices (4.4.4 & 5.1.1), `type` is a dynamic string
        // something like "71F8-2C0A", some kind of unique id per storage
        // don't know any API that can get the root path of that storage based on its id.
        //
        // so no "primary" type, but let the check here for other devices
        if ("primary".equals(type, ignoreCase = true)) {
            fullPath = Environment.getExternalStorageDirectory().toString() + relativePath
            if (fileExists(fullPath)) {
                return fullPath
            }
        }

        // Environment.isExternalStorageRemovable() is `true` for external and internal storage
        // so we cannot relay on it.
        //
        // instead, for each possible path, check if file exists
        // we'll start with secondary storage as this could be our (physically) removable sd card
        fullPath = "${System.getenv("SECONDARY_STORAGE")}$relativePath"
        if (fileExists(fullPath)) {
            return fullPath
        }
        fullPath = "${System.getenv("EXTERNAL_STORAGE")}$relativePath"
        return if (fileExists(fullPath)) fullPath else fullPath
    }

    private fun getDriveFilePath(context: Context, uri: Uri): String {
        context.contentResolver
            .query(uri, null, null, null, null)?.use { cursor ->

                /*
                 * Get the column indexes of the data in the Cursor,
                 * move to the first row in the Cursor, get the data,
                 * and display cursor.
                 */
                // val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                // val size = cursor.getLong(sizeIndex).toString()
                val name = cursor.getString(nameIndex)
                val file = File(context.cacheDir, name)

                context.contentResolver.openInputStream(uri).use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        var read: Int
                        val maxBufferSize = 1 * 1024 * 1024
                        val bytesAvailable = inputStream!!.available()

                        // int bufferSize = 1024;
                        val bufferSize = min(bytesAvailable, maxBufferSize)
                        val buffers = ByteArray(bufferSize)
                        while (inputStream.read(buffers).also { read = it } != -1) {
                            outputStream.write(buffers, 0, read)
                        }
                    }
                }
                return file.path
            }
        return ""
    }

    /***
     * @param uri
     * @param newDirName if you want to create a directory, you can set this variable
     * @return
     */
    private fun copyFileToInternalStorage(
        context: Context,
        uri: Uri,
        @Suppress("SameParameterValue") newDirName: String,
    ): String {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->

            /*
             * Get the column indexes of the data in the Cursor,
             *     * move to the first row in the Cursor, get the data,
             *     * and display it.
             *
             */
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            // val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            // val size = cursor.getLong(sizeIndex).toString()
            val name = cursor.getString(nameIndex)
            val output: File = if (newDirName != "") {
                val dir = File(context.filesDir.toString() + "/" + newDirName)
                if (!dir.exists()) {
                    dir.mkdir()
                }
                File(context.filesDir.toString() + "/" + newDirName + "/" + name)
            } else {
                File(context.filesDir.toString() + "/" + name)
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(output).use { outputStream ->
                    var read: Int
                    val bufferSize = 1024
                    val buffers = ByteArray(bufferSize)
                    while (inputStream.read(buffers).also { read = it } != -1) {
                        outputStream.write(buffers, 0, read)
                    }
                }
            }

            return output.path
        }
        return ""
    }

    private fun getFilePathForWhatsApp(context: Context, uri: Uri): String {
        return copyFileToInternalStorage(context, uri, "whatsapp")
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    fun isWhatsAppFile(uri: Uri): Boolean {
        return "com.whatsapp.provider.media" == uri.authority
    }

    private fun isGoogleDriveUri(uri: Uri): Boolean {
        return "com.google.android.apps.docs.storage" == uri.authority ||
            "com.google.android.apps.docs.storage.legacy" == uri.authority
    }

    private fun getDataColumn(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        // MediaStore.Images.Media.DATA
        // MediaStore.Images.Media._ID
        val column = MediaStore.Images.Media.DATA
        val projection = arrayOf(column)
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use {
            if (it.moveToFirst()) return it.getString(it.getColumnIndexOrThrow(column))
        }
        return null
    }

    //    @Deprecated(
    //    "Using FileDocumentUtils#getPath() instead",
    //    ReplaceWith("getDataColumn(ctx, uri, selection, null)",
    //    "com.leovp.androidbase.utils.file.FileDocumentUtils"))
    //    private fun getImagePath(ctx: Context, uri: Uri, selection: String?): String? {
    //        ctx.contentResolver.query(uri, null, selection, null, null)?.use {
    //            if (it.moveToFirst()) return it.getString(it.getColumnIndex(MediaStore.Images.Media.DATA))
    //        }
    //        return null
    //    }

    // =================================================================

    //    @Deprecated("Using FileDocumentUtils#getPath() instead", ReplaceWith("getPath(ctx, uri)",
    //    "com.leovp.androidbase.utils.file.FileDocumentUtils"))
    //    fun getRealPath(ctx: Context, uri: Uri): String? {
    //        var imagePath: String? = null
    //        if (DocumentsContract.isDocumentUri(ctx, uri)) {
    //            val docId = DocumentsContract.getDocumentId(uri)
    //            if ("com.android.providers.media.documents" == uri.authority) {
    //                val id = docId.split(":").toTypedArray()[1]
    //                val selection = MediaStore.Images.Media._ID + "=" + id
    //                imagePath = getDataColumn(
    //                ctx,
    //                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    //                selection,
    //                null)
    //            } else if ("com.android.providers.downloads.documents" == uri.authority) {
    //                val contentUri =
    //                    ContentUris.withAppendedId(
    //                    Uri.parse("content://downloads/public_downloads"),
    //                    java.lang.Long.valueOf(docId)
    //                    )
    //                imagePath = getDataColumn(ctx, contentUri, null, null)
    //            }
    //        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
    //            imagePath = getDataColumn(ctx, uri, null, null)
    //        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
    //            imagePath = uri.path
    //        }
    //        return imagePath
    //    }

    // ===== Start =================================================
    //    @SuppressLint("ObsoleteSdkInt")
    //    fun getPathFromUri(context: Context, uri: Uri): String? {
    //        val aboveKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
    //
    //        // DocumentProvider
    //        if (aboveKitKat && DocumentsContract.isDocumentUri(context, uri)) {
    //            // ExternalStorageProvider
    //            if (isExternalStorageDocument(uri)) {
    //                val docId = DocumentsContract.getDocumentId(uri)
    //                val split = docId.split(":".toRegex()).toTypedArray()
    //                val type = split[0]
    //                if ("primary".equals(type, ignoreCase = true)) {
    //                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
    //                }
    //
    //                // T O D O: handle non-primary volumes
    //            } else if (isDownloadsDocument(uri)) {
    //                val id = DocumentsContract.getDocumentId(uri)
    //                val contentUri = ContentUris.withAppendedId(
    //                    Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
    //                )
    //                return getDataColumn(context, contentUri, null, null)
    //            } else if (isMediaDocument(uri)) {
    //                val docId = DocumentsContract.getDocumentId(uri)
    //                val split = docId.split(":".toRegex()).toTypedArray()
    //                val type = split[0]
    //                var contentUri: Uri? = null
    //                if ("image" == type) {
    //                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    //                } else if ("video" == type) {
    //                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    //                } else if ("audio" == type) {
    //                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    //                }
    //                val selection = "_id=?"
    //                val selectionArgs = arrayOf(split[1])
    //                return getDataColumn(context, contentUri, selection, selectionArgs)
    //            }
    //        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
    //
    //            // Return the remote address
    //            return if (isGooglePhotosUri(uri))
    //            uri.lastPathSegment
    //            else getDataColumn(context, uri, null, null)
    //        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
    //            return uri.path
    //        }
    //        return null
    //    }
    // ===== End =================================================
}
