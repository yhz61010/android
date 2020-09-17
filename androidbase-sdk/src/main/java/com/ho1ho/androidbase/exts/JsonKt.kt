package com.ho1ho.androidbase.exts

import com.google.gson.Gson
import com.ho1ho.androidbase.utils.JsonUtil
import com.ho1ho.androidbase.utils.LLog

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午3:35
 */
internal val gson
    get() = Gson()

fun Any.toJsonString(): String {
    try {
        return gson.toJson(this)
    } catch (ex: Exception) {
        LLog.e(
            "JsonExt",
            "Can not toJson. Generally, you can ignore this exception. Exception",
            ex
        )
    }
    return ""
}

fun ByteArray.toHexadecimalString(): String {
    return JsonUtil.toHexadecimalString(this)
}
