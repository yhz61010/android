package com.leovp.androidbase.utils.cipher

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GZipUtil {

    /**
     * @return The compressed data are stored in little endian.
     */
    fun compress(str: String, charset: Charset = StandardCharsets.UTF_8): ByteArray =
        ByteArrayOutputStream().also { baos -> GZIPOutputStream(baos).bufferedWriter(charset).use { gos -> gos.write(str) } }.toByteArray()

    /**
     * @param data Byte order: Little endian
     */
    fun decompress(data: ByteArray, charset: Charset = StandardCharsets.UTF_8): String? =
        runCatching { GZIPInputStream(data.inputStream()).bufferedReader(charset).use { it.readText() } }.getOrNull()

    /**
     * @param data Byte order: Little endian
     */
    fun isGzip(data: ByteArray): Boolean = ((data[0].toInt() and 0xFF) or (data[1].toInt() shl 8)) and 0xFFFF == GZIPInputStream.GZIP_MAGIC // 0x8b1f - in big endian
}