@file:Suppress("unused")

package com.leovp.pref.base

import com.leovp.pref.PrefContext

/**
 * Author: Michael Leo
 * Date: 2025/4/11 09:03
 */

//inline fun prefPutStr(key: String, generateMsg: () -> String?) = PrefContext.pref.put(key, generateMsg())

/**
 * Put value which type is following list:
 * - Int
 * - Long
 * - Boolean
 * - Float
 * - String
 * - Object except Set
 */
inline fun <reified T : Any> prefPut(key: String, generateValue: () -> T?) = PrefContext.pref.put(key, generateValue())

inline fun prefPutSet(key: String, generateSet: () -> Set<String>?) = PrefContext.pref.put(key, generateSet())

// ----------

fun prefGetStr(key: String, default: String? = null): String? = PrefContext.pref.getString(key, default)

/**
 * Get value which type is following list:
 * - Int
 * - Long
 * - Boolean
 * - Float
 */
inline fun <reified T> prefGet(key: String, default: T): T = PrefContext.pref.get(key, default)

fun prefGetSet(key: String, default: Set<String>? = null): Set<String>? = PrefContext.pref.getStringSet(key, default)

inline fun <reified T> prefGetObj(key: String): T? = PrefContext.pref.getObject(key)
