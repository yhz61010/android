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
    private var builder: Retrofit.Builder = getRetrofitBuilder()

    /**
     * If you want to re-init header, you must call this method everytime.
     */
    fun initWithHeader(headerMap: Map<String, String>) {
        builder = getRetrofitBuilder(headerMap)
    }

    fun getRetrofit(baseUrl: String): Retrofit {
        return builder.baseUrl(baseUrl).build()
    }

    @Suppress("unused")
    fun getRetrofit(baseUrl: HttpUrl): Retrofit {
        return getRetrofit(baseUrl.toString())
    }

    private fun getRetrofitBuilder(headerMap: Map<String, String>? = null): Retrofit.Builder {
        return Retrofit.Builder()
            .client(getOkHttpClient(headerMap))
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
//        .addConverterFactory(MoshiConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
    }

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