package com.leovp.androidbase.http.retrofit

import okhttp3.HttpUrl

/**
 * Author: Michael Leo
 * Date: 19-7-24 下午5:08
 */
@Suppress("unused")
object ApiService {
    fun <T> getService(baseUrl: String, clazz: Class<T>, headers: Map<String, String>? = null): T {
        return HttpRequest.getInstance(headers ?: emptyMap()).getRetrofit(baseUrl).create(clazz)
    }

    fun <T> getService(baseUrl: HttpUrl, clazz: Class<T>, headers: Map<String, String>? = null): T {
        return getService(baseUrl.toString(), clazz, headers)
    }
}