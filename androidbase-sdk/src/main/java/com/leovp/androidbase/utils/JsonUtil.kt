package com.leovp.androidbase.utils

import com.google.gson.Gson
import com.leovp.androidbase.exts.toHexString

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
    </T> */
    fun <T> toObject(json: String?, clazz: Class<T>): T? {
        try {
            return GSON.fromJson(json, clazz)
        } catch (ex: Exception) {
            LLog.e(
                TAG,
                "Can not toObject. Generally, you can ignore this exception. Exception",
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
            LLog.e(
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
    fun toHexadecimalString(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) {
            return "[]"
        }
        try {
            val sb = StringBuilder()
            sb.append("HEX[")
            for (b in bytes) {
                sb.append(b.toHexString())
                sb.append(',')
            }
            sb.deleteCharAt(sb.length - 1)
            sb.append(']')
            return sb.toString()
        } catch (ex: Exception) {
            LLog.e(
                TAG,
                "Can not toHexadecimalString. Generally, you can ignore this exception.",
                ex
            )
        }
        return "[]"
    }
}