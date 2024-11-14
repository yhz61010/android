@file:Suppress("unused")

package com.leovp.androidbase.exts.kotlin

/**
 * Returns a [Array] containing all elements.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val intArray: Array<Int> = sequence.toArray()
 * ```
 */
inline fun <reified T> Sequence<T>.toArray(): Array<T> {
    val iterator = this.iterator()
    return Array(this.count()) { iterator.next() }
}

/**
 * Applies the given [transform] function to each element of a new Array.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val longArray: Array<Long> = sequence.toArray { it.toLong() }
 * ```
 */
inline fun <T, reified R> Sequence<T>.toArray(crossinline transform: (T) -> R): Array<R> {
    val iterator = this.iterator()
    return Array(this.count()) { transform(iterator.next()) }
}

/**
 * Applies the given [transform] function to each element of a new ByteArray.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val byteArray: ByteArray = sequence.toByteArray { it.toByte() }
 * ```
 */
inline fun <T> Sequence<T>.toByteArray(crossinline transform: (T) -> Byte): ByteArray {
    val iterator = this.iterator()
    return ByteArray(this.count()) { transform(iterator.next()) }
}

/**
 * Applies the given [transform] function to each element of a new ShortArray.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val shortArray: ShortArray = sequence.toShortArray { it.toShort() }
 * ```
 */
inline fun <T> Sequence<T>.toShortArray(crossinline transform: (T) -> Short): ShortArray {
    val iterator = this.iterator()
    return ShortArray(this.count()) { transform(iterator.next()) }
}

/**
 * Applies the given [transform] function to each element of a new IntArray.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Float> = generateSequence(1f) { if (it + 2 < 10) it + 2 else null }
 * val intArray: IntArray = sequence.toIntArray { it.toInt() }
 * ```
 */
inline fun <T> Sequence<T>.toIntArray(crossinline transform: (T) -> Int): IntArray {
    val iterator = this.iterator()
    return IntArray(this.count()) { transform(iterator.next()) }
}

/**
 * Applies the given [transform] function to each element of a new LongArray.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val longArray: LongArray = sequence.toLongArray { it.toLong() }
 * ```
 */
inline fun <T> Sequence<T>.toLongArray(crossinline transform: (T) -> Long): LongArray {
    val iterator = this.iterator()
    return LongArray(this.count()) { transform(iterator.next()) }
}

/**
 * Applies the given [transform] function to each element of a new FloatArray.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val longArray: FloatArray = sequence.toFloatArray { it.toFloat() }
 * ```
 */
inline fun <T> Sequence<T>.toFloatArray(crossinline transform: (T) -> Float): FloatArray {
    val iterator = this.iterator()
    return FloatArray(this.count()) { transform(iterator.next()) }
}

/**
 * Applies the given [transform] function to each element of a new DoubleArray.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val doubleArray: DoubleArray = sequence.toDoubleArray { it.toDouble() }
 * ```
 */
inline fun <T> Sequence<T>.toDoubleArray(crossinline transform: (T) -> Double): DoubleArray {
    val iterator = this.iterator()
    return DoubleArray(this.count()) { transform(iterator.next()) }
}

/**
 * Applies the given [transform] function to each element of a new BooleanArray.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val booleArray: BooleanArray = sequence.toBooleanArray { it % 2 == 0 }
 * ```
 */
inline fun <T> Sequence<T>.toBooleanArray(crossinline transform: (T) -> Boolean): BooleanArray {
    val iterator = this.iterator()
    return BooleanArray(this.count()) { transform(iterator.next()) }
}

/**
 * Applies the given [transform] function to each element of a new CharArray.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val charArray: CharArray = sequence.toCharArray { it.toChar() }
 * ```
 */
inline fun <T> Sequence<T>.toCharArray(crossinline transform: (T) -> Char): CharArray {
    val iterator = this.iterator()
    return CharArray(this.count()) { transform(iterator.next()) }
}

/**
 * Returns a [ArrayList] containing all elements.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val intList: List<Int> = sequence.toArrayList()
 * ```
 */
fun <T> Sequence<T>.toArrayList(): ArrayList<T> = ArrayList<T>().apply { for (item in this@toArrayList) add(item) }

/**
 * Applies the given [transform] function to each element of a new ArrayList.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val longList: List<Long> = sequence.toArrayList { it.toLong()}
 * ```
 */
inline fun <T, reified R> Sequence<T>.toArrayList(crossinline transform: (T) -> R): ArrayList<R> =
    ArrayList<R>().apply { for (item in this@toArrayList) add(transform(item)) }

/**
 * Appends all elements matching the given [predicate] to the ArrayList.
 *
 * Usage:
 * ```kotlin
 * val sequence: Sequence<Int> = generateSequence(1) { if (it + 2 < 10) it + 2 else null }
 * val filterList: List<Int> = sequence.filterToArrayList { it > 5 }
 * ```
 */
inline fun <T> Sequence<T>.filterToArrayList(crossinline predicate: (T) -> Boolean): ArrayList<T> =
    filterTo(ArrayList(), predicate)
