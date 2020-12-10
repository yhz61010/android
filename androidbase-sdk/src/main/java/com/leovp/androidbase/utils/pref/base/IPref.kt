package com.leovp.androidbase.utils.pref.base

/**
 * Author: Michael Leo
 * Date: 20-12-10 上午10:01
 */
interface IPref {
    fun put(key: String, v: Int)
    fun put(key: String, v: Long)
    fun put(key: String, v: Float)
    fun put(key: String, v: Boolean)
    fun put(key: String, v: String)
    fun put(key: String, v: Any)

    fun getInt(key: String, default: Int = 0): Int
    fun getLong(key: String, default: Long = 0L): Long
    fun getBool(key: String, default: Boolean = false): Boolean
    fun getFloat(key: String, default: Float = 0F): Float
    fun getString(key: String, default: String? = null): String?
    fun <T> getObject(key: String, clazz: Class<T>): T?
}