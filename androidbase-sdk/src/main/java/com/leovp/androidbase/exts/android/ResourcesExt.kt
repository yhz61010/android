@file:Suppress("unused")

package com.leovp.androidbase.exts.android

import android.content.Context
import androidx.annotation.RawRes
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Author: Michael Leo
 * Date: 20-7-30 下午7:14
 */

fun Context.readAssetsFileAsString(subdirectory: String?, filename: String): String {
    return resources.assets.open(if (subdirectory.isNullOrBlank()) filename else "$subdirectory${File.separatorChar}$filename").use {
        it.readBytes().toString(StandardCharsets.UTF_8)
    }
}

fun Context.readAssetsFileAsString(@RawRes rawId: Int): String {
    return resources.openRawResource(rawId).use { it.readBytes().toString(StandardCharsets.UTF_8) }
}

fun Context.saveRawResourceToFile(@RawRes id: Int, storagePath: String, outFileName: String, force: Boolean = false): String {
    val inputStream: InputStream = resources.openRawResource(id)
    val file = File(storagePath)
    if (!file.exists()) {
        file.mkdirs()
    }
    inputStream.toFile(storagePath + File.separator + outFileName, force = force)
    return storagePath + File.separatorChar + outFileName
}

fun Context.saveAssetToFile(assetFileName: String, storagePath: String, outFileName: String, force: Boolean = false): Boolean {
    return runCatching {
        assets.open(assetFileName).toFile(File(storagePath, outFileName).absolutePath, force = force)
        true
    }.getOrElse {
        it.printStackTrace()
        false
    }
}