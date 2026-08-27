package com.leovp.http.retrofit

import com.leovp.http.retrofit.base.BaseHttpRequest
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
    // Store only an immutable header snapshot; build a fresh Retrofit per call so concurrent
    // getRetrofit(baseUrl) calls never share one builder or cross baseUrls (remediation HTTP-2).
    @Volatile
    private var headerMap: Map<String, String> = emptyMap()

    /**
     * If you want to re-init header, you must call this method every time.
     */
    fun initWithHeader(headerMap: Map<String, String>) {
        this.headerMap = headerMap.toMap()
    }

    fun getRetrofit(baseUrl: String): Retrofit =
        getRetrofitBuilder(headerMap).baseUrl(baseUrl).build()

    @Suppress("unused")
    fun getRetrofit(baseUrl: HttpUrl): Retrofit = getRetrofit(baseUrl.toString())

    private fun getRetrofitBuilder(headerMap: Map<String, String>? = null): Retrofit.Builder =
        Retrofit.Builder()
            .client(getOkHttpClient(headerMap))
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
//        .addConverterFactory(MoshiConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())

    fun setReadTimeout(mills: Long) {
        readTimeoutInMs = mills
    }

    fun setConnectTimeout(mills: Long) {
        connectTimeoutInMs = mills
    }

    fun setWriteTimeout(mills: Long) {
        writeTimeoutInMs = mills
    }
}
