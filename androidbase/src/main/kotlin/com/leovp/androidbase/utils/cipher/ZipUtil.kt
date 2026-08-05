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

    /** Default per-entry decompressed size cap (200 MiB) used by [unzip]. */
    private const val DEFAULT_MAX_ENTRY_SIZE_BYTES = 200L * 1024 * 1024

    /** Default total decompressed size cap (1 GiB) used by [unzip]. */
    private const val DEFAULT_MAX_TOTAL_SIZE_BYTES = 1024L * 1024 * 1024

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
            return magicNumber == 0x504B0304 ||
                magicNumber == 0x504B0506 ||
                magicNumber == 0x504B0708
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
    fun zip(
        sourceFilePath: String,
        zipFilePath: String,
        @IntRange(0, 9) compressionLevel: Int = 6
    ) {
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
        require(sourceFilePathList.isNotEmpty())
        val srcFileList: List<File> = when (T::class) {
            String::class -> sourceFilePathList.map { File(it as String) }
            File::class -> sourceFilePathList.map { it as File }
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
     * Output size limits applied by [unzip] to mitigate ZIP bombs.
     *
     * @property maxEntryBytes Maximum decompressed size allowed for a single entry.
     * @property maxTotalBytes Maximum decompressed size allowed for the whole archive.
     */
    data class UnzipLimits(
        val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_SIZE_BYTES,
        val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_SIZE_BYTES,
    )

    /**
     * Extract a ZIP file to a specified directory.
     *
     * Security notes:
     * - Each entry is resolved against the canonical destination path, so entries that would
     *   escape the destination (e.g. `../evil.txt`, absolute paths, or symlinked parents) are
     *   rejected with [IllegalArgumentException] (Zip Slip protection).
     * - Per-entry and total decompressed sizes are capped by [limits] to mitigate ZIP bombs.
     * - Each entry is written to a temporary file first. Existing targets are moved to a backup
     *   before replacement and restored if the final rename fails, avoiding partial output and
     *   preserving the previous file on replacement failure.
     *
     * @param zipFilePath Path to the ZIP file.
     * @param destDir Destination directory where files will be extracted.
     * @param limits Decompression size limits; defaults to [UnzipLimits].
     */
    @JvmOverloads
    fun unzip(zipFilePath: String, destDir: String, limits: UnzipLimits = UnzipLimits()) {
        val destFile = File(destDir).canonicalFile
        require(destFile.isDirectory || destFile.mkdirs()) {
            "Cannot create destination directory: $destFile"
        }
        val destRoot = destFile.path + File.separator

        ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath))).use { zipIn ->
            val buffer = ByteArray(BUFFER_SIZE)
            var totalWritten = 0L

            while (true) {
                val entry = zipIn.nextEntry ?: break
                val entryFile = File(destFile, entry.name)
                require(isInsideDest(entryFile.canonicalPath, destFile.path, destRoot)) {
                    "Blocked Zip Slip path traversal for entry: ${entry.name}"
                }

                if (entry.isDirectory) {
                    require(entryFile.isDirectory || entryFile.mkdirs()) {
                        "Cannot create directory for entry: ${entry.name}"
                    }
                } else {
                    totalWritten = extractEntry(
                        zipIn = zipIn,
                        entryFile = entryFile,
                        destPath = destFile.path,
                        destRoot = destRoot,
                        buffer = buffer,
                        totalWrittenSoFar = totalWritten,
                        limits = limits,
                        entryName = entry.name
                    )
                }
                zipIn.closeEntry()
            }
        }
    }

    private fun isInsideDest(canonicalPath: String, destPath: String, destRoot: String): Boolean =
        canonicalPath == destPath || canonicalPath.startsWith(destRoot)

    @Suppress("LongParameterList")
    private fun extractEntry(
        zipIn: ZipInputStream,
        entryFile: File,
        destPath: String,
        destRoot: String,
        buffer: ByteArray,
        totalWrittenSoFar: Long,
        limits: UnzipLimits,
        entryName: String,
    ): Long {
        val parent =
            requireNotNull(entryFile.parentFile) {
                "Missing parent directory for entry: $entryName"
            }
        require(parent.isDirectory || parent.mkdirs()) { "Cannot create directory: $parent" }
        require(isInsideDest(parent.canonicalPath, destPath, destRoot)) {
            "Blocked Zip Slip path traversal for entry: $entryName"
        }

        var totalWritten = totalWrittenSoFar
        val tempFile = File.createTempFile(".unzip-", ".tmp", parent)
        try {
            BufferedOutputStream(FileOutputStream(tempFile)).use { bos ->
                var entryWritten = 0L
                while (true) {
                    val length = zipIn.read(buffer)
                    if (length == -1) break
                    entryWritten += length
                    totalWritten += length
                    require(entryWritten <= limits.maxEntryBytes) {
                        "Entry exceeds size limit (${limits.maxEntryBytes} bytes): $entryName"
                    }
                    require(totalWritten <= limits.maxTotalBytes) {
                        "Archive exceeds total size limit (${limits.maxTotalBytes} bytes)"
                    }
                    bos.write(buffer, 0, length)
                }
            }
            val backupFile = if (entryFile.exists()) {
                File.createTempFile(".unzip-backup-", ".tmp", parent).also { backup ->
                    require(backup.delete()) { "Cannot prepare backup for entry: $entryName" }
                    require(entryFile.renameTo(backup)) {
                        "Cannot back up existing entry before replacement: $entryName"
                    }
                }
            } else {
                null
            }
            if (!tempFile.renameTo(entryFile)) {
                val restored = backupFile?.renameTo(entryFile) ?: true
                check(restored) {
                    "Cannot restore existing entry after replacement failed: $entryName; " +
                        "backup=${backupFile?.absolutePath}"
                }
                error("Cannot move extracted entry into place: $entryName")
            }
            backupFile?.delete()
        } finally {
            tempFile.delete() // Remove the temp file if it is still present (e.g. on failure).
        }
        return totalWritten
    }
}
