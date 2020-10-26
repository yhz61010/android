package com.leovp.androidbase.http

import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Author: Michael Leo
 * Date: 19-10-22 上午10:03
 *
 * Usage1 - HttpsURLConnection:
 * ```kotlin
 * SslUtils.trustAllHosts("TLS")
 * val urlServer = URL(url)
 * val conn = urlServer.openConnection() as HttpURLConnection
 * val useHttps: Boolean = url.toLowerCase().startsWith("https")
 * if (useHttps) {
 *     val https = conn as HttpsURLConnection
 *     https.hostnameVerifier = SslUtils.doNotVerifier
 * }
 * conn.connect()
 * ```
 *
 * Usage2 - OkHttp:
 * ```kotlin
 * val builder = OkHttpClient.Builder()
 * builder.sslSocketFactory(SslUtils.createSocketFactory("SSL"), SslUtils.systemDefaultTrustManager())
 * builder.hostnameVerifier(SslUtils.doNotVerifier)
 * val client = builder.build()
 * ```
 */
object SslUtils {
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    })

    val doNotVerifier = HostnameVerifier { _, _ -> true }

    fun createSocketFactory(protocol: String): SSLSocketFactory =
        SSLContext.getInstance(protocol).apply { init(null, trustAllCerts, SecureRandom()) }.socketFactory

    /**
     * Trust every server - don't check for any certificate
     */
    @Suppress("unused")
    fun trustAllHosts(protocol: String) {
        HttpsURLConnection.setDefaultSSLSocketFactory(createSocketFactory(protocol))
    }

    fun systemDefaultTrustManager(): X509TrustManager {
        return runCatching {
            val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            val trustManagers: Array<TrustManager> = trustManagerFactory.trustManagers
            check(!(trustManagers.size != 1 || trustManagers[0] !is X509TrustManager)) {
                ("Unexpected default trust managers: ${trustManagers.contentToString()}")
            }
            trustManagers[0] as X509TrustManager
            // GeneralSecurityException will be thrown if the system has no TLS.
        }.getOrThrow()
    }
}