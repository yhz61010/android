package com.leovp.json

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.leovp.log.LogContext
import java.lang.reflect.Type
import kotlin.coroutines.cancellation.CancellationException

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:35
 */
const val TAG = "JsonExt"

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Exclude(
    /**
     * If `true`, the field marked with this annotation is skipped from the serialized output.
     * If `false`, the field marked with this annotation is written out in the JSON while
     * serializing. Defaults to `true`.
     */
    val serialize: Boolean = true,
    /**
     * If `true`, the field marked with this annotation is skipped during deserialization.
     * If `false`, the field marked with this annotation is deserialized from the JSON.
     * Defaults to `true`.
     */
    val deserialize: Boolean = true,
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
val gson: Gson by lazy {
    GsonBuilder().addSerializationExclusionStrategy(object : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes) =
            (f.annotations.find { it is Exclude } as? Exclude)?.serialize == true ||
                f.annotations.find { it is ExcludeSerialize } != null

        override fun shouldSkipClass(clazz: Class<*>?) = false
    }).addDeserializationExclusionStrategy(object : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes) =
            (f.annotations.find { it is Exclude } as? Exclude)?.deserialize == true ||
                f.annotations.find { it is ExcludeDeserialize } != null

        override fun shouldSkipClass(clazz: Class<*>?) = false
    }).create()
}

fun Any?.toJsonString(): String = try {
    gson.toJson(this)
} catch (err: Exception) {
    if (err is CancellationException) throw err
    LogContext.log.e(TAG, "toJsonString() error.", err)
    ""
}

/**
 * Convert JSON string to object.
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
inline fun <reified T> String?.toObject(): T? = try {
    // Use a reified TypeToken so generic type arguments (e.g. List<CmdBean>) survive erasure;
    // T::class.java dropped them and Gson produced LinkedTreeMap elements (remediation H18).
    gson.fromJson(this, object : TypeToken<T>() {}.type)
} catch (err: Exception) {
    if (err is CancellationException) throw err
    LogContext.log.e(TAG, "toObject() error.", err)
    null
}

/**
 * Convert JSON string to object
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
fun <T> String?.toObject(type: Type): T? = try {
    gson.fromJson(this, type)
} catch (err: Exception) {
    if (err is CancellationException) throw err
    LogContext.log.e(TAG, "toObject($type) error.", err)
    null
}
