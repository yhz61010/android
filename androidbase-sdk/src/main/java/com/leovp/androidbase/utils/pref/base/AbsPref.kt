package com.leovp.androidbase.utils.pref.base

import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.exts.kotlin.toObject
import com.leovp.lib_exception.fail

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
    abstract fun putSet(key: String, v: Set<String>?)

    protected abstract fun getInt(key: String, default: Int): Int
    protected abstract fun getLong(key: String, default: Long): Long
    protected abstract fun getBool(key: String, default: Boolean): Boolean
    protected abstract fun getFloat(key: String, default: Float): Float
    abstract fun getString(key: String, default: String? = null): String?
    abstract fun getStringSet(key: String, default: Set<String>? = null): Set<String>?

    // ----------------------

    /**
     * Put value which type is following list:
     * - Int
     * - Long
     * - Boolean
     * - Float
     * - Object except Set
     */
    inline fun <reified T> put(key: String, v: T?) {
        when (v) {
            is Int     -> internalPutInt(key, v)
            is Long    -> internalPutLong(key, v)
            is Boolean -> internalPutBool(key, v)
            is Float   -> internalPutFloat(key, v)
            is String  -> internalPutString(key, v)
            is Set<*>  -> fail("Use putSet(key: String, v: Set<String>?) instead.")
            else       -> internalPutString(key, v.toJsonString())
        }
    }

    // ----------

    /**
     * Get object
     */
    inline fun <reified T> getObject(key: String): T? = internalGetString(key, null)?.toObject()

    /**
     * Get value which type is following list:
     * - Int
     * - Long
     * - Boolean
     * - Float
     */
    inline fun <reified T> getObject(key: String, default: T): T {
        return when (default) {
            is Int     -> internalGetInt(key, default) as T
            is Long    -> internalGetLong(key, default) as T
            is Boolean -> internalGetBool(key, default) as T
            is Float   -> internalGetFloat(key, default) as T
            is String  -> fail("Use getString(key: String, default: String? = null) instead.")
            is Set<*>  -> fail("Use getStringSet(key: String, default: Set<String>? = null) instead.")
            else       -> fail("To get object use getObject(key: String) instead.")
        }
    }

    // ----------

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