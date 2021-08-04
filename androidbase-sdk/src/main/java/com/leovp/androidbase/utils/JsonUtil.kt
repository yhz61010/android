package com.leovp.androidbase.utils

import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.exts.kotlin.toObject
import java.lang.reflect.Type

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午1:56
 */
object JsonUtil {
    /**
     * Convert json string to object
     *
     * @param json  the string from which the object is to be deserialized
     * @param clazz the class of T
     * @param <T>   the type of the desired object
     * @return an object of type T from the string. Returns `null` if `json` is `null`
     * or if `json` is empty.
     */
    fun <T> toObject(json: String?, clazz: Class<T>): T? = json?.toObject(clazz)

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
    fun <T> toObject(json: String?, type: Type): T? = json?.toObject(type)

    /**
     * Serializing the specified object into string.
     *
     * @param obj the object for which JSON representation is to be created
     * @return Returns `json string` or `""` if serializing failed.
     */
    fun toJsonString(obj: Any?): String = obj?.toJsonString() ?: ""
}