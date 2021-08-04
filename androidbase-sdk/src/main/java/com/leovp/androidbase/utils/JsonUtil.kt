package com.leovp.androidbase.utils

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import com.leovp.androidbase.exts.kotlin.Exclude
import com.leovp.androidbase.utils.log.LogContext
import java.lang.reflect.Type

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午1:56
 */
object JsonUtil {
    private const val TAG = "JsonUtil"
    private val gson
        get() = GsonBuilder().addSerializationExclusionStrategy(object : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes) = f.getAnnotation(Exclude::class.java) != null

            override fun shouldSkipClass(clazz: Class<*>?) = false
        }).create()

    /**
     * Convert json string to object
     *
     * @param json  the string from which the object is to be deserialized
     * @param clazz the class of T
     * @param <T>   the type of the desired object
     * @return an object of type T from the string. Returns `null` if `json` is `null`
     * or if `json` is empty.
     */
    fun <T> toObject(json: String?, clazz: Class<T>): T? {
        return runCatching { gson.fromJson(json, clazz) }.getOrElse {
            LogContext.log.e(TAG, "Can not to object. Exception: ${it.message}")
            null
        }
    }

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
    fun <T> toObject(json: String?, type: Type): T? {
        runCatching { return gson.fromJson(json, type) }.onFailure { LogContext.log.e(TAG, "Can not to object. Exception: ${it.message}") }
        return null
    }

    /**
     * Serializing the specified object into string.
     *
     * @param obj the object for which JSON representation is to be created
     * @return Returns `json string` or `""` if serializing failed.
     */
    fun toJsonString(obj: Any?): String {
        return runCatching { gson.toJson(obj) }.getOrElse {
            LogContext.log.e(TAG, "Can not to json string. Exception: ${it.message}")
            ""
        }
    }
}