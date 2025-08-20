package com.leovp.pref.base

import com.google.gson.reflect.TypeToken
import com.leovp.json.toJsonString
import com.leovp.json.toObject

/**
 * Author: Michael Leo
 * Date: 2022/1/25 10:01
 */
abstract class AbsPref :
    IPrefPut,
    IPrefGet {
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
            is Int -> put(key, v)
            is Long -> put(key, v)
            is Boolean -> put(key, v)
            is Float -> put(key, v)
            is String? -> put(key, v)
            is Set<*> -> throw IllegalArgumentException(
                "Use putSet(key: String, v: Set<String>?) instead."
            )

            else -> put(key, v.toJsonString())
        }
    }

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
            Int::class.java -> put(key, v as Int)
            Long::class.java -> put(key, v as Long)
            Boolean::class.java -> put(key, v as Boolean)
            Float::class.java -> put(key, v as Float)
            String::class.java -> put(key, v as String?)
            Set::class.java -> throw IllegalArgumentException(
                "Use putSet(key: String, v: Set<String>?) instead."
            )

            else -> put(key, v.toJsonString())
        }
    }

    // ----------------------

    /** Get object */
    inline fun <reified T> getObject(key: String): T? = getString(key, null)?.toObject(object : TypeToken<T>() {}.type)

    /**
     * Get value which type is following list:
     * - Int
     * - Long
     * - Boolean
     * - Float
     */
    inline fun <reified T> get(key: String, default: T): T = when (default) {
        is Int -> getInt(key, default) as T
        is Long -> getLong(key, default) as T
        is Boolean -> getBool(key, default) as T
        is Float -> getFloat(key, default) as T
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

    // -----

    /**
     * Call this method if you want to use it in Java due to **`reified`** can't be called in Java.
     */
    fun <T> getObject4Java(key: String): T? = getString(key, null)?.toObject(object : TypeToken<T>() {}.type)

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
    fun <T> get4Java(key: String, default: T, clazz: Class<T>): T = when (clazz) {
        Int::class.java -> getInt(key, default as Int) as T
        Long::class.java -> getLong(key, default as Long) as T
        Boolean::class.java -> getBool(key, default as Boolean) as T
        Float::class.java -> getFloat(key, default as Float) as T
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
