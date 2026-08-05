@file:Suppress("unused")

package com.leovp.compress

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Author: Michael Leo
 * Date: 20-11-11 上午11:22
 */

/** Default cap for [decompress] output to guard against decompression bombs (remediation H19). */
private const val DEFAULT_MAX_DECOMPRESSED_SIZE: Long = 64L shl 20 // 64 MiB

/**
 * Deflate this [ByteArray] using [DeflaterOutputStream].
 *
 * @throws java.io.IOException on an underlying stream failure (remediation M-CP1).
 */
fun ByteArray.compress(): ByteArray {
    val arrayOutputStream = ByteArrayOutputStream()
    DeflaterOutputStream(arrayOutputStream).use { it.write(this) }
    return arrayOutputStream.toByteArray()
}

/**
 * Inflate a deflated [ByteArray].
 *
 * @param bufferSize the read/copy chunk size.
 * @param maxOutputSize the maximum allowed decompressed size in bytes. A small malicious input can
 * inflate to a huge output (decompression bomb); once the accumulated output exceeds this cap an
 * [IOException] is thrown instead of exhausting the heap (remediation H19).
 * @throws java.util.zip.ZipException on malformed/corrupt deflate input.
 * @throws IOException if the decompressed size exceeds [maxOutputSize], or on other stream failure.
 */
fun ByteArray.decompress(
    bufferSize: Int = 8 shl 10,
    maxOutputSize: Long = DEFAULT_MAX_DECOMPRESSED_SIZE,
): ByteArray {
    val arrayInputStream = ByteArrayInputStream(this)
    var readLen: Int
    var total = 0L
    val readBuffer = ByteArray(bufferSize)
    val baos = ByteArrayOutputStream()
    InflaterInputStream(arrayInputStream).use { ins ->
        baos.buffered(bufferSize).use { os ->
            while (ins.read(readBuffer).also { readLen = it } != -1) {
                total += readLen
                if (total > maxOutputSize) {
                    throw IOException("Decompressed size exceeds limit: $maxOutputSize bytes")
                }
                os.write(readBuffer, 0, readLen)
            }
        }
    }
    return baos.toByteArray()
}
