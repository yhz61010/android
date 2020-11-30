package com.leovp.androidbase.exts.kotlin

/** Convert Boolean to Int */
val Boolean.toInt get() = if (this) 1 else 0