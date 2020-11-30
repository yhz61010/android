package com.leovp.androidbase.exts.kotlin

/** Returns a [Array] containing all elements. */
inline fun <reified T> Sequence<T>.toArray(): Array<T> {
    val iterator = this.iterator()
    return Array(this.count()) { iterator.next() }
}

/** Returns a [ArrayList] containing all elements. */
fun <T> Sequence<T>.toArrayList(): ArrayList<T> =
    ArrayList<T>().apply { for (item in this@toArrayList) add(item) }


/** Applies the given [transform] function to each element of a new Array. */
inline fun <T, reified R> Sequence<T>.toArray(crossinline transform: (T) -> R ): Array<R> {
    val iterator = this.iterator()
    return Array(this.count()) { transform(iterator.next()) }
}

/** Applies the given [transform] function to each element of a new ByteArray. */
inline fun <T> Sequence<T>.toByteArray(crossinline transform: (T) -> Byte ): ByteArray {
    val iterator = this.iterator()
    return ByteArray(this.count()) { transform(iterator.next()) }
}

/** Applies the given [transform] function to each element of a new ShortArray. */
inline fun <T> Sequence<T>.toShortArray(crossinline transform: (T) -> Short ): ShortArray {
    val iterator = this.iterator()
    return ShortArray(this.count()) { transform(iterator.next()) }
}

/** Applies the given [transform] function to each element of a new IntArray. */
inline fun <T> Sequence<T>.toIntArray(crossinline transform: (T) -> Int ): IntArray {
    val iterator = this.iterator()
    return IntArray(this.count()) { transform(iterator.next()) }
}

/** Applies the given [transform] function to each element of a new LongArray. */
inline fun <T> Sequence<T>.toLongArray(crossinline transform: (T) -> Long ): LongArray {
    val iterator = this.iterator()
    return LongArray(this.count()) { transform(iterator.next()) }
}

/** Applies the given [transform] function to each element of a new FloatArray. */
inline fun <T> Sequence<T>.toFloatArray(crossinline transform: (T) -> Float ): FloatArray {
    val iterator = this.iterator()
    return FloatArray(this.count()) { transform(iterator.next()) }
}

/** Applies the given [transform] function to each element of a new DoubleArray. */
inline fun <T> Sequence<T>.toDoubleArray(crossinline transform: (T) -> Double ): DoubleArray {
    val iterator = this.iterator()
    return DoubleArray(this.count()) { transform(iterator.next()) }
}

/** Applies the given [transform] function to each element of a new CharArray. */
inline fun <T> Sequence<T>.toBooleanArray(crossinline transform: (T) -> Boolean ): BooleanArray {
    val iterator = this.iterator()
    return BooleanArray(this.count()) { transform(iterator.next()) }
}

/** Applies the given [transform] function to each element of a new ArrayList. */
inline fun <T> Sequence<T>.toCharArray(crossinline transform: (T) -> Char ): CharArray {
    val iterator = this.iterator()
    return CharArray(this.count()) { transform(iterator.next()) }
}

/** Applies the given [transform] function to each element of a new ArrayList. */
inline fun <T, reified R> Sequence<T>.toArrayList(crossinline transform: (T) -> R): ArrayList<R> =
    ArrayList<R>().apply { for (item in this@toArrayList) add(transform(item)) }

/** Appends all elements matching the given [predicate] to the ArrayList. */
inline fun <T> Sequence<T>.filterToArrayList(crossinline predicate: (T) -> Boolean): ArrayList<T> = filterTo(ArrayList(), predicate)

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the Sequence.
 * @return [Long]
 */
inline fun <T> Sequence<T>.sumByLong(crossinline selector: (T) -> Long): Long {
    var sum = 0L
    for (i in this) sum += selector(i)
    return sum
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the Sequence.
 * @return [Float]
 */
inline fun <T> Sequence<T>.sumByFloat(crossinline selector: (T) -> Float): Float {
    var sum = 0f
    for (i in this) sum += selector(i)
    return sum
}