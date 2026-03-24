@file:Suppress("unused")

package com.leovp.pref.base

import com.leovp.pref.PrefContext.pref

/**
 * Author: Michael Leo
 * Date: 2025/4/11 09:03
 */

/**
 * Put none nullable value which type is following list:
 * - Int
 * - Long
 * - Boolean
 * - Float
 * - String
 * - Object except Set
 */
inline fun <reified T : Any> prefPut(key: String, value: T) = pref.put(key, value)

fun prefPutNullableStr(key: String, value: String?) = pref.put(key, value)

fun prefPutSet(key: String, value: Set<String>?) = pref.putSet(key, value)

// ------------------------------

/**
 * Get none nullable value which type is following list:
 * - Int
 * - Long
 * - Boolean
 * - Float
 * - String
 */
inline fun <reified T : Any> prefGet(key: String, default: T): T = pref.get(key, default)

/**
 * Get nullable string value which type is String.
 */
fun prefGetNullableStr(key: String, default: String? = null): String? = pref.getString(key, default)

fun prefGetSet(key: String, default: Set<String>? = null): Set<String>? = pref.getStringSet(key, default)

inline fun <reified T> prefGetObj(key: String): T? = pref.getObject(key)
