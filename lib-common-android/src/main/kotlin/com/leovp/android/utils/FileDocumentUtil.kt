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
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.leovp.android.exts.fileExists
import java.io.File
import java.io.FileOutputStream

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
    fun getFileUri(context: Context, file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.applicationContext.packageName}.fileprovider",
        file
    )

    @SuppressLint("NewApi", "ObsoleteSdkInt")
    fun getFileRealPath(context: Context, uri: Uri): String? {
        return when { // ExternalStorageProvider
            isExternalStorageDocument(uri) -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray() //                val type = split[0]
                getPathFromExtSD(split)?.takeIf { it.isNotBlank() }
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
                getDataColumn(context, contentUri, selection, selectionArgs)
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
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val fileName = cursor.getString(0)
                    val path = Environment.getExternalStorageDirectory()
                        .toString() + "/Download/" + fileName
                    if (!TextUtils.isEmpty(path)) {
                        return path
                    }
                }
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
                            contentUriPrefix.toUri(),
                            java.lang.Long.valueOf(id)
                        )
                        getDataColumn(context, contentUri, null, null)
                    } catch (e: NumberFormatException) {
                        // In Android 8 and Android P the id is not a number. Opaque Uris have a
                        // null path, so guard against NPE instead of `uri.path!!` (remediation H3).
                        stripRawDownloadPrefix(uri.path)
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
                    "content://downloads/public_downloads".toUri(),
                    java.lang.Long.valueOf(id)
                )
                return getDataColumn(context, contentUri, null, null)
            } catch (e: NumberFormatException) {
                Log.e(TAG, "ContentUris.withAppendedId() exception")
            }
        }

        return null
    }

    fun resourceToUri(context: Context, resId: Int): Uri = (
        ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
            context.resources.getResourcePackageName(resId) + "/" +
            context.resources.getResourceTypeName(resId) + "/" +
            context.resources.getResourceEntryName(resId)
        ).toUri()

    /**
     * Strips the `/document/raw:` and `raw:` prefixes a DownloadsProvider may return, tolerating a
     * null [path] (opaque Uris) by returning null rather than throwing (remediation H3).
     */
    @VisibleForTesting
    internal fun stripRawDownloadPrefix(path: String?): String? =
        path
            ?.replaceFirst("^/document/raw:".toRegex(), "")
            ?.replaceFirst("^raw:".toRegex(), "")

    /**
     * Sanitizes an externally-supplied file name (e.g. a content Uri DISPLAY_NAME) down to a bare
     * file name, stripping any path segments so it cannot be used to escape a target directory.
     * See remediation C1: content DISPLAY_NAME must never be trusted for path construction.
     */
    @VisibleForTesting
    internal fun sanitizedFileName(rawName: String): String {
        val name = File(rawName).name
        require(name.isNotBlank() && name != "." && name != "..") { "Invalid file name" }
        require(!name.contains('/') && !name.contains('\\')) { "Illegal path separator in name" }
        return name
    }

    /**
     * Resolves [childName] under [base], rejecting any result that escapes [base] after
     * canonicalization. Guards against path-traversal writes (remediation C1).
     */
    @VisibleForTesting
    internal fun resolveWithinBase(base: File, childName: String): File {
        val target = File(base, sanitizedFileName(childName)).canonicalFile
        val baseCanonical = base.canonicalFile.canonicalPath
        require(target.canonicalPath.startsWith(baseCanonical + File.separator)) {
            "Path escapes base directory"
        }
        return target
    }

    /**
     * Returns [candidate] only when it exists AND stays within [base] after canonicalization,
     * otherwise null. Prevents a `..` in a docId relative path from escaping the storage root.
     */
    private fun containedRealPath(candidate: String, base: File): String? {
        val canonical = File(candidate).canonicalFile
        val baseCanonical = base.canonicalFile.canonicalPath
        val contained = canonical.canonicalPath == baseCanonical ||
            canonical.canonicalPath.startsWith(baseCanonical + File.separator)
        if (!contained) return null
        return if (fileExists(canonical.path)) canonical.path else null
    }

    @VisibleForTesting
    internal fun getPathFromExtSD(pathData: Array<String>): String? {
        // Malformed docId (no relative-path segment) must not crash with index-out-of-bounds.
        if (pathData.size < 2) return null
        val type = pathData[0]
        val relativePath = "/" + pathData[1]

        // on my Sony devices (4.4.4 & 5.1.1), `type` is a dynamic string
        // something like "71F8-2C0A", some kind of unique id per storage
        // don't know any API that can get the root path of that storage based on its id.
        //
        // so no "primary" type, but let the check here for other devices
        if ("primary".equals(type, ignoreCase = true)) {
            val root = Environment.getExternalStorageDirectory()
            containedRealPath(root.toString() + relativePath, root)?.let { return it }
        }

        // Environment.isExternalStorageRemovable() is `true` for external and internal storage
        // so we cannot rely on it. Instead, for each possible path, verify the file exists AND
        // that its canonical path stays within the storage root (reject `..` traversal).
        System.getenv("SECONDARY_STORAGE")?.let { secondary ->
            containedRealPath("$secondary$relativePath", File(secondary))?.let { return it }
        }
        System.getenv("EXTERNAL_STORAGE")?.let { external ->
            containedRealPath("$external$relativePath", File(external))?.let { return it }
        }
        return null
    }

    private fun getDriveFilePath(context: Context, uri: Uri): String? {
        context.contentResolver
            .query(uri, null, null, null, null)?.use { cursor ->

                /*
                 * Get the column indexes of the data in the Cursor,
                 * move to the first row in the Cursor, get the data,
                 * and display cursor.
                 */
                // Guard the cursor/column/stream against a missing column, an empty result set, or
                // a provider that returns no data, instead of crashing (remediation H4).
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex < 0 || !cursor.moveToFirst()) return null
                val name = cursor.getString(nameIndex) ?: return null
                // Sanitize the untrusted DISPLAY_NAME so it cannot escape cacheDir (remediation C1).
                val file = resolveWithinBase(context.cacheDir, name)

                val input = context.contentResolver.openInputStream(uri) ?: return null
                input.use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        var read: Int
                        // Do not size the buffer from InputStream.available(): content providers may
                        // legally return 0, and a zero-length buffer makes read() return 0 forever.
                        val buffers = ByteArray(8 * 1024)
                        while (inputStream.read(buffers).also { read = it } != -1) {
                            outputStream.write(buffers, 0, read)
                        }
                    }
                }
                return file.path
            }
        return null
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
    ): String? {
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
            // Guard the cursor/column/stream against a missing column, an empty result set, or a
            // provider that returns no data, instead of crashing (remediation H4).
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            // val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex < 0 || !cursor.moveToFirst()) return null
            // val size = cursor.getLong(sizeIndex).toString()
            val name = cursor.getString(nameIndex) ?: return null
            // Sanitize the untrusted DISPLAY_NAME so it cannot escape filesDir (remediation C1).
            val output: File = if (newDirName != "") {
                val dir = File(context.filesDir, newDirName)
                if (!dir.exists()) {
                    dir.mkdir()
                }
                resolveWithinBase(dir, name)
            } else {
                resolveWithinBase(context.filesDir, name)
            }
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { inputStream ->
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
        return null
    }

    private fun getFilePathForWhatsApp(context: Context, uri: Uri): String? =
        copyFileToInternalStorage(context, uri, "whatsapp")

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean =
        "com.android.externalstorage.documents" == uri.authority

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean =
        "com.android.providers.downloads.documents" == uri.authority

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri: Uri): Boolean =
        "com.android.providers.media.documents" == uri.authority

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private fun isGooglePhotosUri(uri: Uri): Boolean =
        "com.google.android.apps.photos.content" == uri.authority

    fun isWhatsAppFile(uri: Uri): Boolean = "com.whatsapp.provider.media" == uri.authority

    private fun isGoogleDriveUri(uri: Uri): Boolean =
        "com.google.android.apps.docs.storage" == uri.authority ||
            "com.google.android.apps.docs.storage.legacy" == uri.authority

    private fun getDataColumn(
        context: Context,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): String? {
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
    // if (it.moveToFirst()) return it.getString(it.getColumnIndex(MediaStore.Images.Media.DATA))
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
    // return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
    //                }
    //
    //                // T O D O: handle non-primary volumes
    //            } else if (isDownloadsDocument(uri)) {
    //                val id = DocumentsContract.getDocumentId(uri)
    //                val contentUri = ContentUris.withAppendedId(
    // Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
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
