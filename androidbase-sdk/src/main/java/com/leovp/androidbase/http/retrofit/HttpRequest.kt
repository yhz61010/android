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
object HttpRequest : BaseHttpRequest() {
    private val builder: Retrofit.Builder = Retrofit.Builder()
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
//        .addConverterFactory(MoshiConverterFactory.create())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())

    fun getRetrofit(baseUrl: String): Retrofit {
        return builder.baseUrl(baseUrl).build()
    }

    @Suppress("unused")
    fun getRetrofit(baseUrl: HttpUrl): Retrofit {
        return getRetrofit(baseUrl.toString())
    }

}