package com.leovp.pref.base

import com.google.gson.reflect.TypeToken
import com.leovp.json.toJsonString
import com.leovp.json.toObject

/**
 * Author: Michael Leo
 * Date: 2022/1/25 10:01
 */
abstract class AbsPref {
    protected abstract fun put(key: String, v: Int)
    protected abstract fun put(key: String, v: Long)
    protected abstract fun put(key: String, v: Boolean)
    protected abstract fun put(key: String, v: Float)
    protected abstract fun put(key: String, v: String?)

    // -----
    abstract fun putSet(key: String, v: Set<String>?)

    /**
     * Put value which type is following list:
     * - Int
     * - Long
     * - Boolean
     * - Float
     * - String
     * - Object except Set
     */
    inline fun <reified T : Any> put(key: String, v: T?) {
        when (v) {
            is Int -> internalPutInt(key, v)
            is Long -> internalPutLong(key, v)
            is Boolean -> internalPutBool(key, v)
            is Float -> internalPutFloat(key, v)
            is String? -> internalPutString(key, v)
            is Set<*> -> throw IllegalArgumentException(
                "Use putSet(key: String, v: Set<String>?) instead."
            )

            else -> internalPutString(key, v.toJsonString())
        }
    }

    // -----
    // -----
    // -----

    /**
     * Call this method if you want to use it in Java due to **`reified`** can't be called in Java.
     *
     * Put value which type is following list:
     * - Int
     * - Long
     * - Boolean
     * - Float
     * - String
     * - Object except Set
     */
    fun <T> put4Java(key: String, v: T?, clazz: Class<T>) {
        when (clazz) {
            Int::class.java -> internalPutInt(key, v as Int)
            Long::class.java -> internalPutLong(key, v as Long)
            Boolean::class.java -> internalPutBool(key, v as Boolean)
            Float::class.java -> internalPutFloat(key, v as Float)
            String::class.java -> internalPutString(key, v as String?)
            Set::class.java -> throw IllegalArgumentException(
                "Use putSet(key: String, v: Set<String>?) instead."
            )

            else -> internalPutString(key, v.toJsonString())
        }
    }

    // ----------------------

    protected abstract fun getInt(key: String, default: Int): Int
    protected abstract fun getLong(key: String, default: Long): Long
    protected abstract fun getBool(key: String, default: Boolean): Boolean
    protected abstract fun getFloat(key: String, default: Float): Float

    // -----
    abstract fun getString(key: String, default: String? = null): String?
    abstract fun getStringSet(key: String, default: Set<String>? = null): Set<String>?

    /**
     * Get object
     */
    inline fun <reified T> getObject(key: String): T? =
        internalGetString(key, null)?.toObject(object : TypeToken<T>() {}.type)
    // inline fun <reified T> getObject(key: String): T? = internalGetString(key, null)?.toObject()

    /**
     * Get value which type is following list:
     * - Int
     * - Long
     * - Boolean
     * - Float
     */
    inline fun <reified T> get(key: String, default: T): T {
        return when (default) {
            is Int -> internalGetInt(key, default) as T
            is Long -> internalGetLong(key, default) as T
            is Boolean -> internalGetBool(key, default) as T
            is Float -> internalGetFloat(key, default) as T
            is String ->
                throw IllegalArgumentException(
                    "Use getString(key: String, default: String? = null) instead."
                )

            is Set<*> ->
                throw IllegalArgumentException(
                    "Use getStringSet(key: String, default: Set<String>? = null) instead."
                )

            else -> throw IllegalArgumentException(
                "To get object use getObject(key: String) instead."
            )
        }
    }

    // -----
    // -----
    // -----

    /**
     * Call this method if you want to use it in Java due to **`reified`** can't be called in Java.
     */
    fun <T> getObject4Java(key: String): T? = internalGetString(key, null)?.toObject(object : TypeToken<T>() {}.type)

    /**
     * Call this method if you want to use it in Java due to **`reified`** can't be called in Java.
     *
     * Get value which type is following list:
     * - Int
     * - Long
     * - Boolean
     * - Float
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get4Java(key: String, default: T, clazz: Class<T>): T {
        return when (clazz) {
            Int::class.java -> internalGetInt(key, default as Int) as T
            Long::class.java -> internalGetLong(key, default as Long) as T
            Boolean::class.java -> internalGetBool(key, default as Boolean) as T
            Float::class.java -> internalGetFloat(key, default as Float) as T
            String::class.java -> throw IllegalArgumentException(
                "Use getString(key: String, default: String? = null) instead."
            )

            Set::class.java -> throw IllegalArgumentException(
                "Use getStringSet(key: String, default: Set<String>? = null) instead."
            )

            else -> throw IllegalArgumentException(
                "To get object use getObject4Java(key: String, clazz: Class<T>) instead."
            )
        }
    }

    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // ------------------------------------------------------------------

    @PublishedApi
    internal fun internalGetInt(key: String, default: Int): Int = getInt(key, default)

    @PublishedApi
    internal fun internalGetLong(key: String, default: Long): Long = getLong(key, default)

    @PublishedApi
    internal fun internalGetBool(key: String, default: Boolean): Boolean = getBool(key, default)

    @PublishedApi
    internal fun internalGetFloat(key: String, default: Float): Float = getFloat(key, default)

    @PublishedApi
    internal fun internalGetString(key: String, default: String?): String? = getString(key, default)

    // ----------

    @PublishedApi
    internal fun internalPutInt(key: String, v: Int) = put(key, v)

    @PublishedApi
    internal fun internalPutLong(key: String, v: Long) = put(key, v)

    @PublishedApi
    internal fun internalPutBool(key: String, v: Boolean) = put(key, v)

    @PublishedApi
    internal fun internalPutFloat(key: String, v: Float) = put(key, v)

    @PublishedApi
    internal fun internalPutString(key: String, v: String?) = put(key, v)
}
