package com.leovp.androidbase.http.retrofit.base

import com.leovp.androidbase.http.SslUtils
import com.leovp.androidbase.http.okhttp.HttpLoggingInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Author: Michael Leo
 * Date: 20-5-27 下午8:41
 */
abstract class BaseHttpRequest(private val headerMap: Map<String, String>?) {
    var connectTimeoutInMs = DEFAULT_CONNECTION_TIMEOUT_IN_MS
    var readTimeoutInMs = DEFAULT_READ_TIMEOUT_IN_MS
    var writeTimeoutInMs = DEFAULT_WRITE_TIMEOUT_IN_MS

    val okHttpClient: OkHttpClient
        get() {
            val httpClientBuilder = OkHttpClient.Builder()
            httpClientBuilder
                .connectTimeout(connectTimeoutInMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutInMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutInMs, TimeUnit.MILLISECONDS)
                .addInterceptor(getHeaderInterceptor())
                .addInterceptor(logInterceptor)

            if (SslUtils.certificateInputStream == null) {
                httpClientBuilder.hostnameVerifier(SslUtils.doNotVerifier)
                httpClientBuilder.sslSocketFactory(SslUtils.createSocketFactory("TLS"), SslUtils.systemDefaultTrustManager())
            } else {
                httpClientBuilder.hostnameVerifier(SslUtils.customVerifier)
                requireNotNull(SslUtils.certificateInputStream, { "For HTTPS, the certification must not be null. Did you forget to set SslUtils.certificateInputStream?" })
                val sslContext = SslUtils.getSSLContext(SslUtils.certificateInputStream!!)
                httpClientBuilder.sslSocketFactory(sslContext.first.socketFactory, sslContext.second)
            }
            return httpClientBuilder.build()
        }

    private val logInterceptor: Interceptor
        get() {
            val logInterceptor = HttpLoggingInterceptor()
            logInterceptor.level = HttpLoggingInterceptor.Level.BODY
            return logInterceptor

//        return new LoggingInterceptor.Builder()
//                .loggable(BuildConfig.DEBUG)
//                .setLevel(Level.BASIC)
//                .log(Platform.INFO)
//                .request("Request")
//                .response("Response")
////                .addHeader("version", BuildConfig.VERSION_NAME)
////                .addQueryParam("query", "0")
////              .logger(new Logger() {
////                  @Override
////                  public void log(int level, String tag, String msg) {
////                      Log.w(tag, msg);
////                  }
////              })
////              .executor(Executors.newSingleThreadExecutor())
//                .build();
        }

    private fun getHeaderInterceptor(): Interceptor {
        return Interceptor { chain: Interceptor.Chain ->
            val build: Request.Builder = chain.request().newBuilder()
            // Add your other headers here.
            // .addHeader("Content-Type", "application/json")
            headerMap?.let {
                for ((k, v) in headerMap) {
                    build.addHeader(k, v)
                }
            }
            chain.proceed(build.build())
        }
    }

    companion object {
        // Timeout explanation
        // https://futurestud.io/tutorials/retrofit-2-customize-network-timeouts
        private const val DEFAULT_CONNECTION_TIMEOUT_IN_MS = 30_000L

        /**
         * The read timeout starts to pay attention once the connection is established and then watches
         * how fast (or slow) every byte gets transferred. If the time between two bytes gets larger than
         * the read timeout, it'll count the request as failed. The counter resets after every incoming byte.
         * Thus, if your response has 120 bytes and it takes five seconds between each byte,
         * the read timeout will not be triggered and the response will take ten minutes to be completed.
         *
         *
         * Again, this is not only limited to the server. Slow read times can be caused by the Internet connection as well.
         */
        private const val DEFAULT_READ_TIMEOUT_IN_MS = 30_000L

        /**
         * The write timeout is the opposite direction of the read timeout. It checks how fast you can
         * send bytes to the server. Of course, it also gets reset after every successful byte. However,
         * if sending a single byte takes longer than the configured write timeout limit, Retrofit will
         * count the request as failed.
         */
        private const val DEFAULT_WRITE_TIMEOUT_IN_MS = 30_000L
    }
}