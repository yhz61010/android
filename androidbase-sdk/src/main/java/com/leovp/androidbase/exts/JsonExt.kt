package com.leovp.androidbase.exts

import com.google.gson.Gson
import com.leovp.androidbase.utils.JsonUtil
import java.lang.reflect.Type

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:35
 */
internal val gson
    get() = Gson()

fun Any.toJsonString(): String = runCatching { gson.toJson(this) }.getOrDefault("")

fun ByteArray.toHexadecimalString(): String = JsonUtil.toHexadecimalString(this)

/**
 * Convert json string to object
 *
 * @param clazz the class of T
 * @param <T> the type of the desired object
 * @return an object of type T from the string. Returns `null` if `json` is `null`
 * or if `json` is empty.
 */
fun <T> String.toObject(clazz: Class<T>): T? = JsonUtil.toObject(this, clazz)

/**
 * Convert json string to object
 *
 * Example:
 * ```kotlin
 * val listType  = object : TypeToken<MutableList<Pair<Path, Paint>>>() {}.type
 * val paths: MutableList<Pair<Path, Paint>> = jsonString.toObject(listType)!!
 * ```
 *
 * @param type the type of the desired object
 * @return an object of type T from the string. Returns `null` if `json` is `null`
 * or if `json` is empty.
 */
fun <T> String.toObject(type: Type): T? = JsonUtil.toObject(this, type)
