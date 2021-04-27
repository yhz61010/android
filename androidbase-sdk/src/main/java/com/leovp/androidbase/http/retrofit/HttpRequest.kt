package com.leovp.androidbase.http.retrofit

import com.leovp.androidbase.http.retrofit.base.BaseHttpRequest
import okhttp3.HttpUrl
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Author: Michael Leo
 * Date: 20-5-27 下午8:41
 */
@Suppress("unused", "WeakerAccess")
object HttpRequest : BaseHttpRequest() {
    private val builder: Retrofit.Builder = Retrofit.Builder()
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
//        .addConverterFactory(MoshiConverterFactory.create())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())

    var globalHeaders: Map<String, String>? = null

    fun getRetrofit(baseUrl: String, headers: Map<String, String>? = null): Retrofit {
        mergeMap(headers)?.also { setHeaders(it) }
        return builder.baseUrl(baseUrl).build()
    }

    @Suppress("unused")
    fun getRetrofit(baseUrl: HttpUrl, headers: Map<String, String>? = null): Retrofit {
        mergeMap(headers)?.also { setHeaders(it) }
        return getRetrofit(baseUrl.toString())
    }

    private fun mergeMap(secondMap: Map<String, String>?): Map<String, String>? {
        if (globalHeaders == null && secondMap == null) return null
        if (globalHeaders == null) return secondMap
        if (secondMap == null) return globalHeaders
        return (globalHeaders!!.asSequence() + secondMap.asSequence())
            .distinct()
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.joinToString(",") }
    }
}