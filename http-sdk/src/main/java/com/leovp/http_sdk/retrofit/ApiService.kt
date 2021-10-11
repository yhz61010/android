package com.leovp.http_sdk.retrofit

import okhttp3.HttpUrl

/**
 * Author: Michael Leo
 * Date: 19-7-24 下午5:08
 */
@Suppress("unused")
object ApiService {
    fun <T> getService(baseUrl: String, clazz: Class<T>): T {
        return HttpRequest.getRetrofit(baseUrl).create(clazz)
    }

    fun <T> getService(baseUrl: HttpUrl, clazz: Class<T>): T {
        return getService(baseUrl.toString(), clazz)
    }
}