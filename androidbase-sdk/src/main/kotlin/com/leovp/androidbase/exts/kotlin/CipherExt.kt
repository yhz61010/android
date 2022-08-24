package com.leovp.androidbase.exts.kotlin

import com.leovp.androidbase.utils.cipher.MD5Util
import java.util.*

/**
 * Author: Michael Leo
 * Date: 20-6-17 上午10:28
 */
fun String.toMd5(allUpperCase: Boolean = false): String {
    return MD5Util.encrypt(this).let { if (allUpperCase) it.uppercase(Locale.getDefault()) else it }
}
