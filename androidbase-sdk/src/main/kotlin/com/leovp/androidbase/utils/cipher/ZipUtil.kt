package com.leovp.androidbase.utils.cipher

import androidx.annotation.IntRange
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A utility class for handling ZIP file operations in Android.
 * Supports compressing files/directories, decompressing ZIP files, and reading ZIP contents.
 *
 * @since 1.0.0
 *
 * Author: Michael Leo
 * Date: 2025/4/1 13:32
 */

object ZipUtil {

    private const val BUFFER_SIZE = 8192

    /**
     * Checks if a file is likely a valid ZIP file by checking its magic number.
     *
     * @param file File to check
     * @return true if the file appears to be a valid ZIP file
     */
    fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false

        DataInputStream(FileInputStream(file)).use { dis ->
            val magicNumber = dis.readInt()
            return magicNumber == 0x504B0304 || magicNumber == 0x504B0506 || magicNumber == 0x504B0708
        }
    }

    /**
     * List all files and directories inside a ZIP file.
     * @param zipFilePath Path to the ZIP file.
     * @return List of all files and directories within the ZIP.
     */
    fun listFilesInZip(zipFilePath: String): List<String> {
        val fileList = mutableListOf<String>()
        ZipInputStream(FileInputStream(zipFilePath)).use { zipIn ->
            var entry: ZipEntry?
            while (zipIn.nextEntry.also { entry = it } != null) {
                fileList.add(entry!!.name)
            }
        }
        return fileList
    }

    /**
     * Read the content of a specific file inside a ZIP.
     * @param zipFilePath Path to the ZIP file.
     * @param fileName The target file inside the ZIP (e.g., "folder/test.txt").
     * @return The text content of the file (only for text files).
     */
    fun readTxtFileFromZip(zipFilePath: String, fileName: String): String? {
        ZipInputStream(FileInputStream(zipFilePath)).use { zipIn ->
            var entry: ZipEntry?
            while (zipIn.nextEntry.also { entry = it } != null) {
                if (entry!!.name == fileName) {
                    return zipIn.bufferedReader().use { it.readText() }
                }
            }
        }
        return null // File not found inside ZIP
    }

    /**
     * Compress a file or directory into a ZIP file.
     * @param sourceFilePath Path of the file or directory to be compressed.
     * @param zipFilePath Destination path for the output ZIP file.
     */
    fun zip(sourceFilePath: String, zipFilePath: String, @IntRange(0, 9) compressionLevel: Int = 6) {
        zip(listOf(sourceFilePath), zipFilePath, compressionLevel)
    }

    /**
     * Compress a file or directory into a ZIP file.
     * @param sourceFile The file or directory to be compressed.
     * @param zipFilePath Destination path for the output ZIP file.
     */
    fun zip(sourceFile: File, zipFilePath: String, @IntRange(0, 9) compressionLevel: Int = 6) {
        zip(listOf(sourceFile), zipFilePath, compressionLevel)
    }

    /**
     * Compresses a list of files/directories into a ZIP file.
     * @param sourceFilePathList List of files/directories to compress
     * @param zipFilePath Destination path for the output ZIP file.
     */
    inline fun <reified T : Any> zip(
        sourceFilePathList: List<T>,
        zipFilePath: String,
        @IntRange(0, 9) compressionLevel: Int = 6,
    ) {
        if (sourceFilePathList.isEmpty()) throw IllegalArgumentException("Source files list cannot be empty")
        val srcFileList: List<File> = when (T::class) {
            String::class -> sourceFilePathList.map { File(it as String) }
            File::class -> sourceFilePathList.map { it as File}
            else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
        }

        FileOutputStream(zipFilePath).use { fileOut ->
            BufferedOutputStream(fileOut).use { bufferedOut ->
                ZipOutputStream(bufferedOut).apply {
                    setLevel(compressionLevel.coerceIn(0, 9))

                    srcFileList.forEach { source ->
                        if (source.exists()) {
                            zipFile(source, "", this)
                        }
                    }
                }.finish()
            }
        }
    }

    /**
     * Recursively add files to the ZIP output stream.
     */
    fun zipFile(file: File, parentPath: String, zipOut: ZipOutputStream) {
        val entryName = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"

        if (file.isDirectory) {
            val files = file.listFiles()
            if (files.isNullOrEmpty()) {
                zipOut.putNextEntry(ZipEntry("$entryName/")) // Add empty folder
                zipOut.closeEntry()
            } else {
                for (child in files) {
                    zipFile(child, entryName, zipOut)
                }
            }
        } else {
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    val entry = ZipEntry(entryName)
                    zipOut.putNextEntry(entry)

                    val buffer = ByteArray(BUFFER_SIZE)
                    var length: Int
                    while (bis.read(buffer).also { length = it } != -1) {
                        zipOut.write(buffer, 0, length)
                    }

                    zipOut.closeEntry()
                }
            }
        }
    }

    /**
     * Extract a ZIP file to a specified directory.
     * @param zipFilePath Path to the ZIP file.
     * @param destDir Destination directory where files will be extracted.
     */
    fun unzip(zipFilePath: String, destDir: String) {
        val destFile = File(destDir)
        if (!destFile.exists()) destFile.mkdirs()

        ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath))).use { zipIn ->
            var entry: ZipEntry?
            val buffer = ByteArray(BUFFER_SIZE)

            while (zipIn.nextEntry.also { entry = it } != null) {
                val entryFile = File(destFile, entry!!.name)

                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    FileOutputStream(entryFile).use { fos ->
                        BufferedOutputStream(fos).use { bos ->
                            var length: Int
                            while (zipIn.read(buffer).also { length = it } != -1) {
                                bos.write(buffer, 0, length)
                            }
                        }
                    }
                }
            }
        }
    }
}
