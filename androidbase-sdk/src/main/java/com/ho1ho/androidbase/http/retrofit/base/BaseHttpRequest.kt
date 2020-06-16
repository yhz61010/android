package com.ho1ho.androidbase.http.retrofit.base

import com.ho1ho.androidbase.http.SslUtils
import com.ho1ho.androidbase.http.okhttp.HttpLoggingInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Author: Michael Leo
 * Date: 20-5-27 下午8:41
 */
abstract class BaseHttpRequest {
    protected val okHttpClient: OkHttpClient
        get() {
            val httpClientBuilder = OkHttpClient.Builder()
            httpClientBuilder
                .connectTimeout(DEFAULT_CONNECTION_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_WRITE_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
                .sslSocketFactory(SslUtils.createSocketFactory("TLS"), systemDefaultTrustManager())
                .hostnameVerifier(SslUtils.doNotVerifier)
                .addInterceptor(getHeaderInterceptor())
                .addInterceptor(logInterceptor)
            return httpClientBuilder.build()
        }

    private fun systemDefaultTrustManager(): X509TrustManager {
        return try {
            val trustManagerFactory: TrustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            val trustManagers: Array<TrustManager> = trustManagerFactory.trustManagers
            check(!(trustManagers.size != 1 || trustManagers[0] !is X509TrustManager)) {
                ("Unexpected default trust managers: ${trustManagers.contentToString()}")
            }
            trustManagers[0] as X509TrustManager
        } catch (e: GeneralSecurityException) {
            // The system has no TLS. Just give up.
            throw AssertionError()
        }
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
            val build = chain.request().newBuilder()
                // Add your other headers here.
                // .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(build)
        }
    }

    companion object {
        // Timeout explanation
        // https://futurestud.io/tutorials/retrofit-2-customize-network-timeouts
        private const val DEFAULT_CONNECTION_TIMEOUT_IN_SECONDS = 30L

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
        private const val DEFAULT_READ_TIMEOUT_IN_SECONDS = 30L

        /**
         * The write timeout is the opposite direction of the read timeout. It checks how fast you can
         * send bytes to the server. Of course, it also gets reset after every successful byte. However,
         * if sending a single byte takes longer than the configured write timeout limit, Retrofit will
         * count the request as failed.
         */
        private const val DEFAULT_WRITE_TIMEOUT_IN_SECONDS = 30L
    }
}