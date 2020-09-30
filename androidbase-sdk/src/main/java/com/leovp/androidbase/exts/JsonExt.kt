package com.leovp.androidbase.exts

import com.google.gson.Gson
import com.leovp.androidbase.utils.JsonUtil

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
 * @param json  the string from which the object is to be deserialized
 * @param clazz the class of T
 * @param <T>   the type of the desired object
 * @return an object of type T from the string. Returns `null` if `json` is `null`
 * or if `json` is empty.
</T> */
fun <T> String.toObject(json: String?, clazz: Class<T>): T? = JsonUtil.toObject(json, clazz)
