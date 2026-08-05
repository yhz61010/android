package com.leovp.http.retrofit.base

import com.leovp.network.SslUtils
import org.amshove.kluent.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Author: Michael Leo
 *
 * Regression test for remediation C3: with no certificate configured, the default OkHttp client
 * must NOT use the trust-all hostname verifier (which would disable TLS hostname verification and
 * allow MITM).
 */
class BaseHttpRequestTest {

    @AfterEach
    fun tearDown() {
        SslUtils.certificateInputStream = null
    }

    @Test
    fun `default client does not use the trust-all hostname verifier`() {
        SslUtils.certificateInputStream = null

        val client = BaseHttpRequest().getOkHttpClient()

        client.hostnameVerifier shouldNotBe SslUtils.doNotVerifier
    }
}
