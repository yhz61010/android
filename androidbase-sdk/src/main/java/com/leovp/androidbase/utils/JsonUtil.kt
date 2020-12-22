package com.leovp.androidbase.utils

import com.google.gson.Gson
import com.leovp.androidbase.exts.kotlin.toHexString
import com.leovp.androidbase.utils.log.LogContext
import java.lang.reflect.Type

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午1:56
 */
object JsonUtil {
    private const val TAG = "JsonUtil"
    private val GSON = Gson()

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
        try {
            return GSON.fromJson(json, clazz)
        } catch (ex: Exception) {
            LogContext.log.e(
                TAG,
                "Can not toObject. Generally, you can ignore this exception. Exception",
                ex
            )
        }
        return null
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
        try {
            return GSON.fromJson(json, type)
        } catch (ex: Exception) {
            LogContext.log.e(
                TAG,
                "Can not toObject with Type. Generally, you can ignore this exception. Exception",
                ex
            )
        }
        return null
    }

    /**
     * Serializing the specified object into string.
     *
     * @param obj the object for which JSON representation is to be created
     * @return Returns `json string` or `""` if serializing failed.
     */
    fun toJsonString(obj: Any?): String {
        try {
            return GSON.toJson(obj)
        } catch (ex: Exception) {
            LogContext.log.e(
                TAG,
                "Can not toJson. Generally, you can ignore this exception. Exception",
                ex
            )
        }
        return ""
    }

    /**
     * Serializing the specified object into string.
     *
     * @param bytes the object for which JSON representation is to be created
     * @return Returns `Hex string in big endian` or `[]` if serializing failed.
     */
    @Deprecated(
        "This method is not efficiency. Use ByteArray.toHexStringLE() instead",
        ReplaceWith("toHexStringLE(addPadding, delimiter)", "com.leovp.androidbase.exts.kotlin.toHexStringLE")
    )
    fun toHexadecimalString(bytes: ByteArray?, addPadding: Boolean = false, delimiter: CharSequence = ","): String {
        if (bytes == null || bytes.isEmpty()) {
            return ""
        }
        try {
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(b.toHexString(addPadding))
                if (delimiter.isNotEmpty()) sb.append(delimiter)
            }
            sb.deleteCharAt(sb.length - 1)
            return sb.toString()
        } catch (ex: Exception) {
            LogContext.log.e(
                TAG,
                "Can not toHexadecimalString. Generally, you can ignore this exception.",
                ex
            )
        }
        return "[]"
    }
}