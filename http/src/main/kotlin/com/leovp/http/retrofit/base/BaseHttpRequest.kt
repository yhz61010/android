package com.leovp.http.retrofit.base

import com.leovp.http.okhttp.HttpLoggingInterceptor
import com.leovp.log.LogContext
import com.leovp.log.base.LogOutType
import com.leovp.network.SslUtils
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Author: Michael Leo
 * Date: 20-5-27 下午8:41
 */
open class BaseHttpRequest {
    var connectTimeoutInMs = DEFAULT_CONNECTION_TIMEOUT_IN_MS
    var readTimeoutInMs = DEFAULT_READ_TIMEOUT_IN_MS
    var writeTimeoutInMs = DEFAULT_WRITE_TIMEOUT_IN_MS

    fun getOkHttpClient(headerMap: Map<String, String>? = null): OkHttpClient {
        val httpClientBuilder = OkHttpClient.Builder()
        httpClientBuilder
            .connectTimeout(connectTimeoutInMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutInMs, TimeUnit.MILLISECONDS)
            .writeTimeout(writeTimeoutInMs, TimeUnit.MILLISECONDS)
            .addInterceptor(getHeaderInterceptor(headerMap))
            .addInterceptor(logInterceptor)

        // When a certificate is configured, pin trust to it and verify against the configured
        // hostnames. Otherwise, fall back to OkHttp's secure platform defaults (proper hostname
        // verification + system trust store) — never install a trust-all hostname verifier by
        // default (remediation C3).
        val certStream = SslUtils.certificateInputStream
        if (certStream != null) {
            httpClientBuilder.hostnameVerifier(SslUtils.customVerifier)
            val sslContext = SslUtils.getSSLContext(certStream)
            httpClientBuilder.sslSocketFactory(sslContext.first.socketFactory, sslContext.second)
        }
        return httpClientBuilder.build()
    }

    /**
     * HTTP logging verbosity. Defaults to [HttpLoggingInterceptor.Level.NONE] so request/response
     * bodies and headers are never logged unless the host app explicitly opts in (remediation
     * HTTP-1). Raise to [HttpLoggingInterceptor.Level.BODY]/`HEADERS` only in debug builds; the
     * interceptor redacts sensitive headers by name and bounds body logging size.
     */
    @Volatile
    var logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE

    private val logInterceptor: Interceptor
        get() = HttpLoggingInterceptor().apply { level = logLevel }

    private fun getHeaderInterceptor(headerMap: Map<String, String>? = null): Interceptor =
        Interceptor { chain: Interceptor.Chain ->
            val build: Request.Builder = chain.request().newBuilder()
            // Add your other headers here.
            // .addHeader("Content-Type", "application/json")
            headerMap?.let {
                for ((k, v) in headerMap) {
                    // Log the header name only; never log the value, which may carry credentials.
                    LogContext.log.d(TAG, "Add header: $k", outputType = LogOutType.HTTP_HEADER)
                    build.addHeader(k, v)
                }
            }
            chain.proceed(build.build())
        }

    companion object {
        private const val TAG = "HTTP"

        // Timeout explanation
        // https://futurestud.io/tutorials/retrofit-2-customize-network-timeouts
        private const val DEFAULT_CONNECTION_TIMEOUT_IN_MS = 30_000L

        /**
         * The read timeout starts to pay attention once the connection is established and then
         * watches
         * how fast (or slow) every byte gets transferred. If the time between two bytes gets larger
         * than
         * the read timeout, it'll count the request as failed. The counter resets after every
         * incoming byte.
         * Thus, if your response has 120 bytes, and it takes five seconds between each byte,
         * the read timeout will not be triggered and the response will take ten minutes to be
         * completed.
         *
         *
         * Again, this is not only limited to the server. Slow read times can be caused by the
         * Internet connection as well.
         */
        private const val DEFAULT_READ_TIMEOUT_IN_MS = 30_000L

        /**
         * The write timeout is the opposite direction of the read timeout. It checks how fast you
         * can
         * send bytes to the server. Of course, it also gets reset after every successful byte.
         * However,
         * if sending a single byte takes longer than the configured write timeout limit, Retrofit
         * will
         * count the request as failed.
         */
        private const val DEFAULT_WRITE_TIMEOUT_IN_MS = 30_000L
    }
}
