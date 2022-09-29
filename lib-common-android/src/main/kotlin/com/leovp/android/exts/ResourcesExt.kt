@file:Suppress("unused")

package com.leovp.android.exts

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.RawRes
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Author: Michael Leo
 * Date: 20-7-30 下午7:14
 */

fun Context.readAssetsFileAsString(subdirectory: String?, filename: String): String {
    return resources.assets.open(if (subdirectory.isNullOrBlank()) filename else "$subdirectory${File.separatorChar}$filename")
        .use {
            it.readBytes().toString(StandardCharsets.UTF_8)
        }
}

fun Context.readAssetsFileAsString(@RawRes rawId: Int): String {
    return resources.openRawResource(rawId).use { it.readBytes().toString(StandardCharsets.UTF_8) }
}

fun Context.saveRawResourceToFile(
    @RawRes id: Int,
    storagePath: String,
    outFileName: String,
    force: Boolean = false
): String {
    val inputStream: InputStream = resources.openRawResource(id)
    val file = File(storagePath)
    if (!file.exists()) {
        file.mkdirs()
    }
    inputStream.toFile(storagePath + File.separator + outFileName, force = force)
    return storagePath + File.separatorChar + outFileName
}

fun Context.saveAssetToFile(
    assetFileName: String,
    storagePath: String,
    outFileName: String,
    force: Boolean = false
): Boolean {
    return runCatching {
        assets.open(assetFileName).toFile(File(storagePath, outFileName).absolutePath, force = force)
        true
    }.getOrElse {
        it.printStackTrace()
        false
    }
}

// TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, ctx.resources.displayMetrics).toInt()
// (dipValue * ctx.resources.displayMetrics.density + 0.5f).toInt()
/**
 * Can I use [Resources.getSystem()] to get [Resources]?
 *
 * @return The return type is either `Int` or `Float`
 */
inline fun <reified T : Number> Resources.dp2px(dipValue: Float): T = px(TypedValue.COMPLEX_UNIT_DIP, dipValue)

/** Converts dp to pixel. */
val Int.px get(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

/** Converts dp to pixel. */
val Float.px get(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

/**
 * @return The return type is either `Int` or `Float`
 */
inline fun <reified T : Number> Resources.px2dp(pxValue: Int): T {
    val result: Float = pxValue * 1.0f / displayMetrics.density + 0.5f
    return when (T::class) {
        Float::class -> result as T
        Int::class -> result.toInt() as T
        else -> error("Type not supported")
    }
}

/** Converts pixel to dp. */
val Int.dp get(): Float = (this * 1.0f / Resources.getSystem().displayMetrics.density + 0.5f)

/** Converts pixel to dp. */
val Float.dp get(): Float = (this * 1.0f / Resources.getSystem().displayMetrics.density + 0.5f)

/**
 * Can I use [Resources.getSystem()] to get [Resources]?
 *
 * @return The return type is either `Int` or `Float`
 */
inline fun <reified T : Number> Resources.sp2px(spValue: Float): T = px(TypedValue.COMPLEX_UNIT_SP, spValue)

/**
 * Converts an unpacked complex data value holding a dimension to its final floating point value.
 *
 * @param unit [TypedValue]
 * TypedValue.COMPLEX_UNIT_DIP: dp -> px
 * TypedValue.COMPLEX_UNIT_PT:  pt -> px
 * TypedValue.COMPLEX_UNIT_MM:  mm -> px
 * TypedValue.COMPLEX_UNIT_IN:  inch -> px
 *
 * Can I use [Resources.getSystem()] to get [Resources]?
 *
 * @return The return type is either `Int` or `Float`
 */
@JvmOverloads
inline fun <reified T : Number> Resources.px(unit: Int = TypedValue.COMPLEX_UNIT_DIP, value: Float): T {
    val result: Float = TypedValue.applyDimension(unit, value, displayMetrics)
    return when (T::class) {
        Float::class -> result as T
        Int::class -> result.toInt() as T
        else -> error("Type not supported")
    }
}
