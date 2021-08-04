package com.leovp.androidbase.exts.kotlin

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import com.leovp.androidbase.utils.log.LogContext
import java.lang.reflect.Type

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:35
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Exclude(
    /**
     * If `true`, the field marked with this annotation is skipped from the serialized output.
     * If `false`, the field marked with this annotation is written out in the JSON while serializing. Defaults to `true`.
     */
    val serialize: Boolean = true,
    /**
     * If `true`, the field marked with this annotation is skipped during deserialization.
     * If `false`, the field marked with this annotation is deserialized from the JSON.
     * Defaults to `true`.
     */
    val deserialize: Boolean = true
)

private const val TAG = "JsonExt"

private val gson
    get() = GsonBuilder().addSerializationExclusionStrategy(object : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes) = (f.annotations.find { it is Exclude } as? Exclude)?.serialize == true
        override fun shouldSkipClass(clazz: Class<*>?) = false
    }).addDeserializationExclusionStrategy(object : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes) = (f.annotations.find { it is Exclude } as? Exclude)?.deserialize == true
        override fun shouldSkipClass(clazz: Class<*>?) = false
    }).create()

fun Any?.toJsonString(): String {
    return runCatching { gson.toJson(this) }.getOrElse {
        LogContext.log.e(TAG, "Can not to json string. Exception: ${it.message}")
        ""
    }
}

/**
 * Convert json string to object
 *
 * @param clazz the class of T
 * @param <T> the type of the desired object
 * @return an object of type T from the string. Returns `null` if `json` is `null`
 * or if `json` is empty.
 */
fun <T> String?.toObject(clazz: Class<T>): T? {
    return runCatching { gson.fromJson(this, clazz) }.getOrElse {
        LogContext.log.e(TAG, "Can not to object. Exception: ${it.message}")
        null
    }
}

/**
 * Convert json string to object
 *
 * Example:
 * ```kotlin
 * val listType = object : TypeToken<MutableList<Pair<Path, Paint>>>() {}.type
 * val paths: MutableList<Pair<Path, Paint>> = jsonString.toObject(listType)!!
 * ```
 *
 * @param type the type of the desired object
 * @return an object of type T from the string. Returns `null` if `json` is `null`
 * or if `json` is empty.
 */
fun <T> String?.toObject(type: Type): T? {
//    runCatching { return gson.fromJson(this, type) }.onFailure { LogContext.log.e(TAG, "Can not to object. Exception: ${it.message}") }
//    return null
    return runCatching { return gson.fromJson(this, type) }.getOrElse {
        LogContext.log.e(TAG, "Can not to object. Exception: ${it.message}")
        null
    }
}
