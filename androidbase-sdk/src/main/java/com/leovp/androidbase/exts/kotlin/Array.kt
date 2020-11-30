package com.leovp.androidbase.exts.kotlin

import java.math.BigInteger

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun <T> Array<T>.sumByLong(crossinline selector: (T) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun ByteArray.sumByLong(crossinline selector: (Byte) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun ShortArray.sumByLong(crossinline selector: (Short) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun IntArray.sumByLong(crossinline selector: (Int) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun LongArray.sumByLong(crossinline selector: (Long) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun FloatArray.sumByLong(crossinline selector: (Float) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun DoubleArray.sumByLong(crossinline selector: (Double) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun BooleanArray.sumByLong(crossinline selector: (Boolean) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun CharArray.sumByLong(crossinline selector: (Char) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun <T> Array<T>.sumByFloat(crossinline selector: (T) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun ByteArray.sumByFloat(crossinline selector: (Byte) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun ShortArray.sumByFloat(crossinline selector: (Short) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun IntArray.sumByFloat(crossinline selector: (Int) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun LongArray.sumByFloat(crossinline selector: (Long) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun FloatArray.sumByFloat(crossinline selector: (Float) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun DoubleArray.sumByFloat(crossinline selector: (Double) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun BooleanArray.sumByFloat(crossinline selector: (Boolean) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the array.
 * @return [Long]
 */
inline fun CharArray.sumByFloat(crossinline selector: (Char) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}

/** Convert ByteArray to positive BigInteger */
val ByteArray.toPositiveBigInteger get() = BigInteger(1, this)

/** Convert ByteArray to negative BigInteger */
val ByteArray.toNegativeBigInteger get() = BigInteger(-1, this)

/* Convert ByteArray to UTF-8 */
val ByteArray.toUTF8 get() = this.toString(Charsets.UTF_8)