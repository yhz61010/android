package com.leovp.androidbase.exts

import java.text.SimpleDateFormat
import java.util.*

/**
 * Author: Michael Leo
 * Date: 20-8-17 下午4:06
 */

fun Date.getToday(format: String) = SimpleDateFormat(format, Locale.getDefault()).format(this)