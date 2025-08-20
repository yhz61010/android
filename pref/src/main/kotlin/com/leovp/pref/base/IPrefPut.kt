package com.leovp.pref.base

/**
 * Author: Michael Leo
 * Date: 2025/8/19 14:15
 */
interface IPrefPut {
    fun put(key: String, v: Int)
    fun put(key: String, v: Long)
    fun put(key: String, v: Boolean)
    fun put(key: String, v: Float)
    fun put(key: String, v: String?)

    fun putSet(key: String, v: Set<String>?)
}
