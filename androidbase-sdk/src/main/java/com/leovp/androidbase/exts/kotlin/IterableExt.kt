package com.leovp.androidbase.exts.kotlin

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the collection.
 * @return [Long]
 */
inline fun <T> Iterable<T>.sumByLong(crossinline selector: (T) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the collection.
 * @return [Float]
 */
inline fun <T> Iterable<T>.sumByFloat(crossinline selector: (T) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}