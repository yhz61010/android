package com.leovp.androidbase.http

import com.leovp.androidbase.exts.kotlin.ITAG
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*


/**
 * Author: Michael Leo
 * Date: 19-10-22 上午10:03
 *
 * If you want to verify server certificate, the following code must be called:
 * ```kotlin
 * SslUtils.certificateInputStream = assets.open("cert/certificate.pem")
 * ```
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
@Suppress("unused")
object SslUtils {
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    })

    val doNotVerifier = HostnameVerifier { _, _ ->
        LogContext.log.w(ITAG, "Trust all host names")
        true
    }

    /**
     * Trust every server - don't check for any certificate
     */
    fun trustAllHosts(protocol: String) {
        HttpsURLConnection.setDefaultSSLSocketFactory(createSocketFactory(protocol))
    }

    /**
     * This method must be called if you want to verify server certificate.
     * ```kotlin
     * SslUtils.certificateInputStream = assets.open("cert/certificate.pem")
     * ```
     *
     * You can put your certification file in `assets` folder. Then call following codes to get input stream:
     * ```kotlin
     * resources.assets.open("cert/certificate.pem")
     * ```
     * or you can get certification string, then call following codes to get input stream:
     * ```kotlin
     * okio.Buffer()
     *     .writeUtf8("Cer string")
     *     .inputStream()
     * ```
     */
    var certificateInputStream: InputStream? = null

    var hostnames: Array<String>? = null

    val customVerifier = HostnameVerifier { hostname, _ ->
        LogContext.log.w(ITAG, "Only trust the following host names: ${hostname.toJsonString()}")
        requireNotNull(hostnames, { "Host names must not be empty. Did you forget to set SslUtils.hostnames?" })
        hostnames!!.contains(hostname)
//        else {
//            val hv = HttpsURLConnection.getDefaultHostnameVerifier()
//            hv.verify(this.hostname, session)
//        }
    }

    fun createSocketFactory(protocol: String): SSLSocketFactory =
        SSLContext.getInstance(protocol).apply { init(null, trustAllCerts, SecureRandom()) }.socketFactory

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

    fun getSSLContext(certInputStream: InputStream): Pair<SSLContext, X509TrustManager> {
        return runCatching {
            val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            keyStore.setCertificateEntry("ca", certificateFactory.generateCertificate(certInputStream))

            val trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
//        val kmf: KeyManagerFactory = KeyManagerFactory.getInstance("X509")
//        kmf.init(keyStore, null)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null/*kmf.keyManagers*/, trustMgrFactory.trustManagers, SecureRandom())

            Pair<SSLContext, X509TrustManager>(sslContext, trustMgrFactory.trustManagers[0] as X509TrustManager)
        }.getOrThrow()
    }
}