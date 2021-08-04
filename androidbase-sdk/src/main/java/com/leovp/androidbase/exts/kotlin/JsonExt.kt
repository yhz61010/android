package com.leovp.androidbase.exts.kotlin

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import com.leovp.androidbase.utils.JsonUtil
import java.lang.reflect.Type

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:35
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Exclude {

}

private val gson
    get() = GsonBuilder().addSerializationExclusionStrategy(object : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes) = f.getAnnotation(Exclude::class.java) != null

        override fun shouldSkipClass(clazz: Class<*>?) = false
    }).create()

fun Any.toJsonString(): String = runCatching { gson.toJson(this) }.getOrDefault("")

//@Deprecated(
//    "This method is not efficiency. Use ByteArray.toHexStringLE() instead",
//    ReplaceWith("toHexStringLE(addPadding, delimiter)", "com.leovp.androidbase.exts.kotlin.toHexStringLE")
//)
//fun ByteArray.toHexadecimalString(addPadding: Boolean = false, delimiter: CharSequence = ","): String = JsonUtil.toHexadecimalString(this, addPadding, delimiter)

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
