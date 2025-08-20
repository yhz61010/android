package com.leovp.pref.base

/**
 * Author: Michael Leo
 * Date: 2025/8/19 14:15
 */
interface IPrefGet {
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getBool(key: String, default: Boolean): Boolean
    fun getFloat(key: String, default: Float): Float
    fun getString(key: String, default: String? = null): String?

    fun getStringSet(key: String, default: Set<String>? = null): Set<String>?
}
