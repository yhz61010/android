package com.leovp.androidbase.utils.cipher

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GZipUtil {

    private const val BUFFER_SIZE = 8192

    /**
     * Default cap on decompressed output (64 MiB) used by [decompress] to guard against GZIP bombs.
     */
    private const val DEFAULT_MAX_DECOMPRESSED_BYTES = 64L * 1024 * 1024

    /**
     * @return The compressed data are stored in little endian.
     */
    fun compress(str: String, charset: Charset = StandardCharsets.UTF_8): ByteArray =
        ByteArrayOutputStream().also { baos ->
            GZIPOutputStream(baos).bufferedWriter(charset).use { gos -> gos.write(str) }
        }.toByteArray()

    /**
     * @param data Byte order: Little endian
     * @param charset Charset used to decode the decompressed bytes.
     * @param maxOutputBytes Maximum decompressed size to read. Returns `null` if the decompressed
     *   output would exceed this cap, guarding against GZIP bombs (remediation CIP-3).
     */
    @JvmOverloads
    fun decompress(
        data: ByteArray,
        charset: Charset = StandardCharsets.UTF_8,
        maxOutputBytes: Long = DEFAULT_MAX_DECOMPRESSED_BYTES,
    ): String? = runCatching {
        GZIPInputStream(data.inputStream()).use { gis ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = gis.read(buffer)
                if (read == -1) break
                total += read
                require(total <= maxOutputBytes) {
                    "Decompressed size exceeds limit ($maxOutputBytes bytes)"
                }
                out.write(buffer, 0, read)
            }
            String(out.toByteArray(), charset)
        }
    }.getOrNull()

    /**
     * @param data Byte order: Little endian
     *
     * GZIP_MAGIC: 0x8b1f - in big endian
     */
    fun isGzip(data: ByteArray): Boolean = (
        (
            data[0].toInt() and
                0xFF
            ) or
            (data[1].toInt() shl 8)
        ) and
        0xFFFF == GZIPInputStream.GZIP_MAGIC
}
