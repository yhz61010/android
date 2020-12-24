package com.leovp.androidbase.http.retrofit

import okhttp3.HttpUrl
import java.io.InputStream

/**
 * Author: Michael Leo
 * Date: 19-7-24 下午5:08
 */
object ApiService {
    fun <T> getService(baseUrl: String, clazz: Class<T>, certificateInputStream: InputStream? = null, trustHostNames: Array<String> = emptyArray()): T {
        return HttpRequest.getInstance(certificateInputStream, trustHostNames).getRetrofit(baseUrl).create(clazz)
    }

    @Suppress("unused")
    fun <T> getService(baseUrl: HttpUrl, clazz: Class<T>, certificateInputStream: InputStream? = null, trustHostNames: Array<String> = emptyArray()): T {
        return getService(baseUrl.toString(), clazz, certificateInputStream, trustHostNames)
    }
}