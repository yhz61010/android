package com.leovp.androidbase.http.retrofit

import com.leovp.androidbase.SingletonHolder
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
class HttpRequest private constructor(headerMap: Map<String, String>?) : BaseHttpRequest(headerMap) {
    companion object : SingletonHolder<HttpRequest, Map<String, String>?>(::HttpRequest)

    private var builder: Retrofit.Builder = getRetrofitBuilder()

    fun reInit() {
        builder = getRetrofitBuilder()
    }

    fun getRetrofit(baseUrl: String): Retrofit {
        return builder.baseUrl(baseUrl).build()
    }

    @Suppress("unused")
    fun getRetrofit(baseUrl: HttpUrl): Retrofit {
        return getRetrofit(baseUrl.toString())
    }

    private fun getRetrofitBuilder(): Retrofit.Builder {
        return Retrofit.Builder()
            .client(getOkHttpClient())
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
//        .addConverterFactory(MoshiConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
    }
}