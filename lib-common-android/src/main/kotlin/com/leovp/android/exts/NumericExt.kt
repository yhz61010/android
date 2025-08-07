package com.leovp.android.exts

import android.util.Size

/**
 * Author: Michael Leo
 * Date: 2022/5/30 10:29
 */

fun getRatio(size: Size, delimiters: String = ":", swapResult: Boolean = false): String? =
    com.leovp.kotlin.exts.getRatio(size.width, size.height, delimiters, swapResult)

fun getRatio(size: SmartSize, delimiters: String = ":", swapResult: Boolean = false): String? =
    com.leovp.kotlin.exts.getRatio(size.long, size.short, delimiters, swapResult)
