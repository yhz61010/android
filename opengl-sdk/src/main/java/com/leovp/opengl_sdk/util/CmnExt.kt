package com.leovp.opengl_sdk.util

import android.content.Context
import androidx.annotation.RawRes
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Author: Michael Leo
 * Date: 2022/4/2 11:12
 */
internal fun Context.readAssetsFileAsString(subdirectory: String?, filename: String): String {
    return resources.assets.open(if (subdirectory.isNullOrBlank()) filename else "$subdirectory${File.separatorChar}$filename").use {
        it.readBytes().toString(StandardCharsets.UTF_8)
    }
}

internal fun Context.readAssetsFileAsString(@RawRes rawId: Int): String {
    return resources.openRawResource(rawId).use { it.readBytes().toString(StandardCharsets.UTF_8) }
}