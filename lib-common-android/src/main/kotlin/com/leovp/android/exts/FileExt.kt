package com.leovp.android.exts

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午2:04
 */

fun InputStream.toFile(
    outFileFullPath: String,
    bufferSize: Int = 256 shl 10,
    force: Boolean = true,
    autoCloseInputStream: Boolean = true
) {
    val outfile = File(outFileFullPath)
    if (force || !outfile.exists()) {
        outfile.outputStream().use { os ->
            this.copyTo(os, bufferSize)
        }
        if (autoCloseInputStream) this.close()
    }
}

fun Context.getBaseDirString(baseFolderName: String = "", type: String? = null): File =
    File(this.getExternalFilesDir(type), baseFolderName).also {
        if (!it.exists()) it.mkdirs()
    }

fun Context.createFile(fileName: String, type: String? = null): File = File(this.getExternalFilesDir(type), fileName)

fun Context.createFile(baseFolderName: String, fileName: String, type: String? = null): File =
    File(getBaseDirString(baseFolderName, type), fileName)

fun Context.createTmpFile(suffix: String?, type: String? = null): File =
    this.createFile("tmp", "${System.currentTimeMillis()}${if (suffix.isNullOrBlank()) "" else suffix}", type)

@Deprecated(
    "Alternatives such as Context.getExternalFilesDir(String), " +
        "MediaStore, or Intent.ACTION_OPEN_DOCUMENT offer better performance."
)
fun getExternalFolder(baseFolder: String): File {
    val dir = File(Environment.getExternalStorageDirectory().absolutePath, baseFolder)
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun fileExists(filePath: String): Boolean = File(filePath).exists()

/**
 * Create a image file which name is a formatted timestamp with the current date and time.
 */
fun Context.createImageFile(extension: String): File {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    return File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "IMG_${sdf.format(Date())}.$extension")
}
