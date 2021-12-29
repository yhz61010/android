@file:Suppress("unused")

package com.leovp.lib_compress

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Author: Michael Leo
 * Date: 20-11-11 上午11:22
 */
fun ByteArray.compress(): ByteArray {
    val arrayOutputStream = ByteArrayOutputStream()
    DeflaterOutputStream(arrayOutputStream).use { it.write(this) }
    return arrayOutputStream.toByteArray()
}

fun ByteArray.decompress(bufferSize: Int = 8 shl 10): ByteArray {
    val arrayInputStream = ByteArrayInputStream(this)
    var readLen: Int
    val readBuffer = ByteArray(bufferSize)
    val baos = ByteArrayOutputStream()
    InflaterInputStream(arrayInputStream).use { ins ->
        baos.buffered(bufferSize).use { os ->
//                baos.buffered(bufferSize).write(ins.readBytes())
            while (ins.read(readBuffer).also { readLen = it } != -1) {
                os.write(readBuffer, 0, readLen)
            }
        }
    }
    return baos.toByteArray()
}