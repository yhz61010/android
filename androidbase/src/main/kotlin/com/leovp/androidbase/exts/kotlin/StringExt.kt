package com.leovp.androidbase.exts.kotlin

import android.util.Patterns
import android.webkit.URLUtil
import androidx.annotation.IntRange
import java.util.regex.Pattern

/**
 * Author: Michael Leo
 * Date: 20-3-13 下午4:44
 */

fun String.toAsciiByteArray() = this.toByteArray(Charsets.US_ASCII)

/**
 * Transform each 2 hex chars to one byte
 */
fun String.hexToByteArray(): ByteArray {
    require(this.length % 2 == 0) {
        "The length of string must be an even number."
    }
    val binary = ByteArray(this.length / 2)
    for (i in binary.indices) {
        binary[i] = this.substring(2 * i, 2 * i + 2).toInt(16).toByte()
    }
    return binary
}

fun String.isValidUrl(): Boolean {
    // On 华为畅享5S Android 5.1, if the url ending with slash, although the url is a valid url,
    // the Patterns.WEB_URL.matcher will return false. So we need to remove the ending slash.
    val tmpUrl = if (this.endsWith("/")) this.substring(0, this.length - 1) else this
    return if (Pattern.matches("^[\\x00-\\xff]+$", tmpUrl) && Patterns.WEB_URL.matcher(tmpUrl).matches()) {
        URLUtil.isValidUrl(tmpUrl)
    } else {
        false
    }
}

/**
 * If [length] or [srcPos] is not valid, the original string will be returned.
 */
fun String.truncate(@IntRange(from = 1) length: Int, @IntRange(from = 0) srcPos: Int = 0): String {
    if (srcPos > this.length - 1) return ""
    val endIdx = if (srcPos + length > this.length) this.length else srcPos + length
    return this.substring(srcPos, endIdx)
}
