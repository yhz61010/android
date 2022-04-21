package com.leovp.lib_json

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ExcludeSerialize

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ExcludeDeserialize

/**
 * The usage of annotations `Exclude`, `ExcludeSerialize` and `ExcludeDeserialize`,
 * please check `JsonUnitTest.kt` file.
 */
val gson: Gson
    get() = GsonBuilder().addSerializationExclusionStrategy(object : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes) =
                (f.annotations.find { it is Exclude } as? Exclude)?.serialize == true || f.annotations.find { it is ExcludeSerialize } != null

        override fun shouldSkipClass(clazz: Class<*>?) = false
    }).addDeserializationExclusionStrategy(object : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes) =
                (f.annotations.find { it is Exclude } as? Exclude)?.deserialize == true || f.annotations.find { it is ExcludeDeserialize } != null

        override fun shouldSkipClass(clazz: Class<*>?) = false
    }).create()

fun Any?.toJsonString(): String = runCatching { gson.toJson(this) }.getOrElse { "" }

/**
 * Convert json string to object.
 *
 * Example:
 * ```kotlin
 * val cmdBean: CmdBean? = stringData.toObject()
 * val cmdBean2 = stringData.toObject<CmdBean?>()
 * ```
 *
 * @return an object of type T from the string. Returns `null` if `json` is `null`
 * or if `json` is empty.
 */
inline fun <reified T> String?.toObject(): T? = runCatching { gson.fromJson(this, T::class.java) }.getOrNull()

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
fun <T> String?.toObject(type: Type): T? = runCatching { return gson.fromJson(this, type) }.getOrNull()
