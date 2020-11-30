package com.leovp.androidbase.exts.kotlin

/** Convert Int value to Boolean value */
val Int.toBoolean: Boolean get() = this != 0

/** Convert Int value to binary value(Int.toString(2)) */
val Int.toBinary: String get() = this.toString(2)

/** Convert Int value to octal value(Int.toString(8)) */
val Int.toOctal: String get() = this.toString(8)

/** Convert Int value to decimal value(Int.toString(10)) */
val Int.toDecimal: String get() = this.toString(10)

/** Convert Int value to hexadecimal value(Int.toString(16)) */
val Int.toHexadecimal: String get() = this.toString(16)