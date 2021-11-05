package com.leovp.androidbase.utils.file

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*


/**
 * Author: Michael Leo
 * Date: 20-5-13 下午2:04
 */
@Suppress("unused")
object FileUtil {
    fun copyInputStreamToFile(inputStream: InputStream, fullPath: String, bufferSize: Int = 256 shl 10, force: Boolean = false) {
        val file = File(fullPath)
        if (force || !file.exists()) {
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

    fun getBaseDirString(ctx: Context, baseFolderName: String = ""): File {
        return File(ctx.getExternalFilesDir(null), baseFolderName).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    fun createFile(ctx: Context, fileName: String): File {
        return File(ctx.getExternalFilesDir(null), fileName)
    }

    fun createFile(ctx: Context, baseFolderName: String, fileName: String): File {
        return File(getBaseDirString(ctx, baseFolderName), fileName)
    }

    fun createTmpFile(ctx: Context, suffix: String?): File {
        return createFile(ctx, "tmp", "${System.currentTimeMillis()}${if (suffix.isNullOrBlank()) "" else suffix}")
    }

    @Deprecated("Alternatives such as Context.getExternalFilesDir(String), MediaStore, or Intent.ACTION_OPEN_DOCUMENT offer better performance.")
    fun getExternalFolder(baseFolder: String): File {
        val dir = File(Environment.getExternalStorageDirectory().absolutePath, baseFolder)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun fileExists(filePath: String): Boolean = File(filePath).exists()

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