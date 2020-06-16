package com.ho1ho.androidbase.http

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Author: Michael Leo
 * Date: 19-10-22 上午10:03
 *
 * Usage:
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
 */
object SslUtils {
    private val trustAllCerts =
        arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        })

    val doNotVerifier = HostnameVerifier { _, _ -> true }

    fun createSocketFactory(protocol: String): SSLSocketFactory =
        SSLContext.getInstance(protocol).apply {
            init(null, trustAllCerts, SecureRandom())
        }.socketFactory

    /**
     * Trust every server - don't check for any certificate
     */
    @Suppress("unused")
    fun trustAllHosts(protocol: String) {
        HttpsURLConnection.setDefaultSSLSocketFactory(createSocketFactory(protocol))
    }

}